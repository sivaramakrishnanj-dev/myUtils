package dev.sivarj.braingame.data

import android.content.Context
import dev.sivarj.braingame.ai.LlmResult
import dev.sivarj.braingame.ai.PuzzleRequestResult
import dev.sivarj.braingame.ai.PuzzleService
import dev.sivarj.braingame.domain.Attempt
import dev.sivarj.braingame.domain.AnswerChecker
import dev.sivarj.braingame.domain.Elo
import dev.sivarj.braingame.domain.Puzzle
import dev.sivarj.braingame.domain.Skill
import dev.sivarj.braingame.domain.SkillRating
import dev.sivarj.braingame.domain.SkillSelector
import dev.sivarj.braingame.domain.puzzleRatingFor
import dev.sivarj.braingame.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

/** A puzzle plus the player's live progress on it, ready for the UI. */
data class ActivePuzzle(
    val id: String,
    val puzzle: Puzzle,
    val targetRating: Int,
    val draftAnswer: String,
    val draftOrder: List<Int>,
    val hintsUsed: Int,
    val wrongAnswers: List<String>,
)

/** How a finished puzzle ended, including the rating movement to show the player. */
data class PuzzleOutcome(
    val solved: Boolean,
    val puzzle: Puzzle,
    val ratingBefore: Int,
    val ratingAfter: Int,
    val hintsUsed: Int,
    val wrongAnswers: List<String>,
)

/**
 * The single place game state changes. Owns the invariant that at most one
 * puzzle is ACTIVE, and is the only writer of Elo ratings.
 */
class GameRepository(context: Context) {

    private val db = AppDatabase.build(context.applicationContext)
    private val puzzleDao = db.puzzleDao()
    private val ratingDao = db.skillRatingDao()
    private val puzzleService = PuzzleService(context.applicationContext)
    private val settings = AppSettings(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    /** The puzzle to resume, or null when there is nothing in progress. */
    val activePuzzle: Flow<ActivePuzzle?> = puzzleDao.observeActive().map { entity ->
        entity?.let { row ->
            decodePuzzle(row)?.let { puzzle ->
                ActivePuzzle(
                    id = row.id,
                    puzzle = puzzle,
                    targetRating = row.targetRating,
                    draftAnswer = row.draftAnswer,
                    draftOrder = row.draftOrderList,
                    hintsUsed = row.hintsUsed,
                    wrongAnswers = row.wrongAnswerList,
                )
            }
        }
    }

    /** Ratings for every skill, filling in defaults for skills never played. */
    val ratings: Flow<List<SkillRating>> = ratingDao.observeAll().map { rows ->
        Skill.entries.map { skill ->
            rows.firstOrNull { it.skill == skill }
                ?.let { SkillRating(it.skill, it.rating, it.attempts, it.solved) }
                ?: SkillRating(skill)
        }
    }

    val history: Flow<List<PuzzleEntity>> = puzzleDao.observeHistory()

    /**
     * Generates a new puzzle and makes it the active one.
     *
     * Any puzzle still in progress is marked ABANDONED with no rating penalty:
     * there is no opponent to protect a rating from here, and being interrupted
     * mid-puzzle shouldn't cost anything. The abandon count is still recorded,
     * so a habit of rerolling away from hard puzzles would be visible in stats.
     */
    suspend fun startNewPuzzle(): PuzzleRequestResult {
        val currentRatings = ratings.first()
        val skill = SkillSelector.pickSkill(currentRatings)
        val skillRating = currentRatings.first { it.skill == skill }
        val targetRating = SkillSelector.targetRating(skillRating)

        val config = settings.config.first()
        val recentThemes = puzzleDao.observeHistory(limit = 5).first().map { it.theme }
        val theme = SkillSelector.pickTheme(config.effectiveThemes, recentThemes)

        return when (val result = puzzleService.generate(skill, theme, targetRating)) {
            is PuzzleRequestResult.Failure -> result
            is PuzzleRequestResult.Success -> {
                val now = System.currentTimeMillis()
                puzzleDao.abandonActive(now)
                puzzleDao.insert(
                    PuzzleEntity(
                        id = UUID.randomUUID().toString(),
                        payload = json.encodeToString(Puzzle.serializer(), result.puzzle),
                        skill = skill,
                        theme = theme,
                        targetRating = targetRating,
                        status = PuzzleStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                        ratingBefore = skillRating.rating,
                    )
                )
                result
            }
        }
    }

    /** Persists in-progress input so a pause or process death loses nothing. */
    suspend fun saveDraft(id: String, answer: String? = null, order: List<Int>? = null) {
        val row = puzzleDao.getById(id) ?: return
        puzzleDao.update(
            row.copy(
                draftAnswer = answer ?: row.draftAnswer,
                draftOrder = order?.joinToString(",") ?: row.draftOrder,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Reveals the next hint and records it, since hints reduce the rating gain. */
    suspend fun revealHint(id: String): Int {
        val row = puzzleDao.getById(id) ?: return 0
        val puzzle = decodePuzzle(row) ?: return row.hintsUsed
        val next = (row.hintsUsed + 1).coerceAtMost(puzzle.hints.size)
        puzzleDao.update(row.copy(hintsUsed = next, updatedAt = System.currentTimeMillis()))
        return next
    }

    /**
     * Grades an attempt. A correct answer finishes the puzzle and updates the
     * rating; a wrong one is recorded and the puzzle stays active so the player
     * can try again.
     */
    suspend fun submit(id: String, attempt: Attempt, display: String): PuzzleOutcome? {
        val row = puzzleDao.getById(id) ?: return null
        val puzzle = decodePuzzle(row) ?: return null

        if (AnswerChecker.isCorrect(puzzle, attempt)) {
            return finish(row, puzzle, solved = true)
        }

        val wrong = (row.wrongAnswerList + display).joinToString("\n")
        puzzleDao.update(
            row.copy(wrongAnswers = wrong, updatedAt = System.currentTimeMillis())
        )
        return null
    }

    /** Gives up: scores as a failure and reveals the solution. */
    suspend fun giveUp(id: String): PuzzleOutcome? {
        val row = puzzleDao.getById(id) ?: return null
        val puzzle = decodePuzzle(row) ?: return null
        return finish(row, puzzle, solved = false)
    }

    /**
     * Closes out a puzzle: applies the Elo update, bumps the skill's counters,
     * and returns the outcome for display.
     */
    private suspend fun finish(
        row: PuzzleEntity,
        puzzle: Puzzle,
        solved: Boolean,
    ): PuzzleOutcome {
        val existing = ratingDao.get(row.skill)
        val before = existing?.rating ?: Elo.DEFAULT_RATING
        val attempts = existing?.attempts ?: 0
        val solvedCount = existing?.solved ?: 0

        val score = Elo.scoreFor(solved, row.hintsUsed, row.wrongAnswerList.size)
        val after = Elo.updatedRating(
            playerRating = before,
            puzzleRating = puzzleRatingFor(row.targetRating),
            score = score,
            attemptsInSkill = attempts,
        )

        val now = System.currentTimeMillis()
        ratingDao.upsert(
            SkillRatingEntity(
                skill = row.skill,
                rating = after,
                attempts = attempts + 1,
                solved = solvedCount + if (solved) 1 else 0,
                updatedAt = now,
            )
        )
        puzzleDao.update(
            row.copy(
                status = if (solved) PuzzleStatus.SOLVED else PuzzleStatus.FAILED,
                ratingBefore = before,
                ratingAfter = after,
                updatedAt = now,
            )
        )

        return PuzzleOutcome(
            solved = solved,
            puzzle = puzzle,
            ratingBefore = before,
            ratingAfter = after,
            hintsUsed = row.hintsUsed,
            wrongAnswers = row.wrongAnswerList,
        )
    }

    /**
     * Fetches the tutor explanation for a failed puzzle, caching it on the row so
     * revisiting the puzzle doesn't pay for it twice.
     */
    suspend fun explain(id: String): LlmResult {
        val row = puzzleDao.getById(id) ?: return LlmResult.Failure("Puzzle not found")
        if (row.explanation.isNotBlank()) return LlmResult.Success(row.explanation)

        val puzzle = decodePuzzle(row) ?: return LlmResult.Failure("Puzzle could not be read")
        val result = puzzleService.explain(
            puzzle = puzzle,
            wrongAnswers = row.wrongAnswerList,
            hintsUsed = row.hintsUsed,
            gaveUp = row.status == PuzzleStatus.FAILED && row.wrongAnswerList.isEmpty(),
        )
        if (result is LlmResult.Success) {
            puzzleDao.update(
                row.copy(explanation = result.text, updatedAt = System.currentTimeMillis())
            )
        }
        return result
    }

    suspend fun abandonedCount(): Int = puzzleDao.countByStatus(PuzzleStatus.ABANDONED)

    /**
     * A row whose payload can't be decoded is unplayable — treat it as absent
     * rather than crashing, so a bad row can't wedge the app on every launch.
     */
    private fun decodePuzzle(row: PuzzleEntity): Puzzle? =
        runCatching { json.decodeFromString(Puzzle.serializer(), row.payload) }.getOrNull()
}
