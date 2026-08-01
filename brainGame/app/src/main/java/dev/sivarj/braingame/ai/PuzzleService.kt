package dev.sivarj.braingame.ai

import android.content.Context
import android.util.Log
import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.Puzzle
import dev.sivarj.braingame.domain.PuzzleValidator
import dev.sivarj.braingame.domain.Skill
import dev.sivarj.braingame.domain.SkillSelector
import dev.sivarj.braingame.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** A generation attempt: either a validated puzzle or a reason it could not be produced. */
sealed interface PuzzleRequestResult {
    data class Success(val puzzle: Puzzle, val targetRating: Int) : PuzzleRequestResult
    data class Failure(val error: String) : PuzzleRequestResult
}

/**
 * Generates puzzles and explanations.
 *
 * Generation is one puzzle per request — no batching, no pool. The trade-off is
 * that a bad generation is user-visible rather than silently discarded, so a
 * failed validation is retried once before giving up.
 */
class PuzzleService(context: Context) {

    private val settings = AppSettings(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Generate one puzzle for [skill] at [targetRating], themed [theme].
     *
     * Retries once on a puzzle that fails structural validation: the model is
     * being asked to satisfy cross-field constraints the JSON schema cannot
     * express, so an occasional miss is expected rather than exceptional.
     */
    suspend fun generate(skill: Skill, theme: String, targetRating: Int): PuzzleRequestResult {
        val config = settings.config.first()
        val provider = AnthropicProvider(config)
        if (!provider.isConfigured) {
            return PuzzleRequestResult.Failure("Add your Anthropic API key in Settings to play")
        }

        var lastProblem: String? = null

        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            val userText = buildGenerationRequest(skill, theme, targetRating, retryNote = lastProblem)
            val result = provider.complete(
                LlmRequest(
                    systemPrompt = config.promptGeneration,
                    userText = userText,
                    jsonSchema = PuzzleSchema.json,
                    effort = Effort.MEDIUM,
                    maxTokens = GENERATION_MAX_TOKENS,
                )
            )

            when (result) {
                // A transport or API error won't be fixed by asking again the same way.
                is LlmResult.Failure -> return PuzzleRequestResult.Failure(result.error)

                is LlmResult.Success -> {
                    val generated = runCatching {
                        json.decodeFromString<GeneratedPuzzle>(result.text)
                    }.getOrElse { e ->
                        lastProblem = "the response did not parse as the required JSON shape"
                        Log.w(TAG, "Puzzle JSON did not decode on attempt ${attempt + 1}", e)
                        return@repeat
                    }

                    val puzzle = generated.toPuzzle(skill, theme)
                    val rejection = PuzzleValidator.validate(puzzle)
                    if (rejection == null) {
                        return PuzzleRequestResult.Success(puzzle, targetRating)
                    }
                    lastProblem = rejection.reason
                    Log.w(TAG, "Puzzle rejected on attempt ${attempt + 1}: ${rejection.reason}")
                }
            }
        }

        return PuzzleRequestResult.Failure(
            "Could not generate a valid puzzle" + (lastProblem?.let { " ($it)" } ?: "") +
                ". Tap for a new game to try again."
        )
    }

    /**
     * Explain a finished puzzle in terms of what the player actually did.
     *
     * Runs at [Effort.HIGH]: this is the teaching moment, and a few extra
     * seconds is a good trade for a better explanation.
     */
    suspend fun explain(
        puzzle: Puzzle,
        wrongAnswers: List<String>,
        hintsUsed: Int,
        gaveUp: Boolean,
    ): LlmResult {
        val config = settings.config.first()
        val provider = AnthropicProvider(config)
        if (!provider.isConfigured) return LlmResult.Failure("API key not set")

        return provider.complete(
            LlmRequest(
                systemPrompt = config.promptExplanation,
                userText = buildExplanationRequest(puzzle, wrongAnswers, hintsUsed, gaveUp),
                effort = Effort.HIGH,
                maxTokens = EXPLANATION_MAX_TOKENS,
            )
        )
    }

    /**
     * The varying half of the generation call. Everything here changes per
     * request, which is exactly why none of it belongs in the cached system
     * prompt.
     */
    private fun buildGenerationRequest(
        skill: Skill,
        theme: String,
        targetRating: Int,
        retryNote: String?,
    ): String = buildString {
        appendLine("Generate one puzzle.")
        appendLine()
        appendLine("Skill: ${skill.name} — ${skill.blurb}")
        appendLine("Theme: $theme")
        appendLine("Difficulty: ${SkillSelector.difficultyBand(targetRating)}")
        if (retryNote != null) {
            appendLine()
            appendLine(
                "Your previous attempt was rejected because $retryNote. " +
                    "Produce a fresh puzzle that does not have that problem."
            )
        }
    }

    private fun buildExplanationRequest(
        puzzle: Puzzle,
        wrongAnswers: List<String>,
        hintsUsed: Int,
        gaveUp: Boolean,
    ): String = buildString {
        appendLine("## The puzzle")
        appendLine(puzzle.question)
        if (puzzle.options.isNotEmpty()) {
            appendLine()
            appendLine("Options as shown to the player:")
            puzzle.options.forEachIndexed { i, option -> appendLine("  ${i + 1}. $option") }
        }
        appendLine()
        appendLine("## Correct answer")
        appendLine(describeAnswer(puzzle))
        appendLine()
        appendLine("## Worked solution given to the player")
        appendLine(puzzle.solution)
        appendLine()
        appendLine("## What the player did")
        if (wrongAnswers.isEmpty()) {
            appendLine("Submitted no answer.")
        } else {
            appendLine("Wrong answers, in the order submitted: ${wrongAnswers.joinToString(", ")}")
        }
        appendLine("Hints revealed: $hintsUsed of ${puzzle.hints.size}")
        appendLine(if (gaveUp) "They gave up rather than answering correctly." else "They ran out of attempts.")
    }

    /** Renders the canonical answer in the same terms the player saw. */
    private fun describeAnswer(puzzle: Puzzle): String = when (puzzle.answerKind) {
        AnswerKind.INTEGER ->
            "${puzzle.answerInteger} ${puzzle.answerLabel}".trim()

        AnswerKind.APPROXIMATE ->
            "approximately ${puzzle.answerApproximate} ${puzzle.answerLabel}".trim() +
                " (accepted within ${puzzle.tolerancePercent}%)"

        AnswerKind.MULTIPLE_CHOICE -> {
            val index = puzzle.answerOptionIndex
            val label = index?.let { puzzle.options.getOrNull(it) } ?: "unknown"
            "option ${(index ?: 0) + 1}: $label"
        }

        AnswerKind.ORDERING -> {
            val ordered = puzzle.answerOrder
                ?.mapNotNull { puzzle.options.getOrNull(it) }
                ?.joinToString(" → ")
                ?: "unknown"
            "the correct order is $ordered"
        }
    }

    private companion object {
        const val TAG = "PuzzleService"

        /** One retry after a rejected puzzle; more than that and the player is just waiting. */
        const val MAX_GENERATION_ATTEMPTS = 2

        /**
         * Opus 5 thinks by default and `max_tokens` caps thinking plus output,
         * so this is sized far above the JSON itself. A truncated response wastes
         * the whole call and the player's wait, and `max_tokens` is only a
         * ceiling — headroom that goes unused costs nothing.
         */
        const val GENERATION_MAX_TOKENS = 16384
        const val EXPLANATION_MAX_TOKENS = 4096
    }
}
