package dev.sivarj.braingame.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.sivarj.braingame.domain.Skill

/** Lifecycle of a puzzle row. Exactly one row may be [ACTIVE] at a time. */
enum class PuzzleStatus { ACTIVE, SOLVED, FAILED, ABANDONED }

/**
 * One puzzle and the player's progress on it.
 *
 * The puzzle itself is stored as the serialized [dev.sivarj.braingame.domain.Puzzle]
 * JSON rather than exploded into columns: its shape varies by answer kind, and
 * keeping one blob means the generation contract and the schema can't drift apart.
 * Progress lives in real columns because it is written on every move and read for
 * stats.
 */
@Entity(tableName = "puzzles")
data class PuzzleEntity(
    @PrimaryKey val id: String,
    /** Serialized [dev.sivarj.braingame.domain.Puzzle]. */
    val payload: String,
    val skill: Skill,
    val theme: String,
    /** Difficulty the puzzle was requested at; the Elo update uses this. */
    val targetRating: Int,
    val status: PuzzleStatus,
    /** Player's in-progress answer, so a paused puzzle resumes exactly as left. */
    val draftAnswer: String = "",
    /** In-progress ORDERING arrangement as comma-separated indices. */
    val draftOrder: String = "",
    val hintsUsed: Int = 0,
    /** Wrong answers in submission order, newline-separated — fed to the explainer. */
    val wrongAnswers: String = "",
    /** LLM explanation, cached so re-opening a finished puzzle costs nothing. */
    val explanation: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /** Player rating in this skill before the puzzle, for showing the delta. */
    val ratingBefore: Int = 0,
    val ratingAfter: Int = 0,
) {
    val wrongAnswerList: List<String>
        get() = wrongAnswers.split('\n').filter { it.isNotBlank() }

    val draftOrderList: List<Int>
        get() = draftOrder.split(',').mapNotNull { it.trim().toIntOrNull() }
}

/** A player's Elo standing in one skill. One row per [Skill]. */
@Entity(tableName = "skill_ratings")
data class SkillRatingEntity(
    @PrimaryKey val skill: Skill,
    val rating: Int,
    val attempts: Int,
    val solved: Int,
    val updatedAt: Long,
)
