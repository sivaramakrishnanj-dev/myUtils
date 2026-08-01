package dev.sivarj.braingame.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The skill dimensions the app rates separately. Each carries its own Elo so a
 * strong arithmetic score can't mask weak logic — the selector targets the
 * weakest dimension more often (see [SkillSelector]).
 */
enum class Skill(val displayName: String, val blurb: String) {
    ARITHMETIC("Arithmetic", "Mental math wrapped in a story"),
    LOGIC("Logic", "Deduce the one arrangement that fits"),
    SEQUENCE("Sequence", "Find the rule, then continue it"),
    ESTIMATION("Estimation", "Fermi problems — order-of-magnitude reasoning"),
    ORDERING("Ordering", "Put events or steps in the correct order"),
}

/** Themes the puzzle text is dressed in. Free text so new interests are cheap to add. */
object Themes {
    const val RAMAYANA = "Ramayana"
    const val LLM = "LLMs and machine learning"
    const val SOFTWARE = "Software architecture"
    const val ASTRONOMY = "Astronomy"
    const val EVERYDAY = "Everyday life"

    val ALL = listOf(RAMAYANA, LLM, SOFTWARE, ASTRONOMY, EVERYDAY)
}

/**
 * How the answer is entered and checked. Kept deliberately small: every kind
 * must be checkable by [AnswerChecker] without asking the model, so a wrong
 * answer is never a matter of opinion.
 */
enum class AnswerKind {
    /** Exact integer. */
    INTEGER,

    /** Numeric answer correct within [Puzzle.tolerancePercent]. */
    APPROXIMATE,

    /** One of [Puzzle.options] — index-based. */
    MULTIPLE_CHOICE,

    /** [Puzzle.options] rearranged into the correct order. */
    ORDERING,
}

/**
 * One generated puzzle. This is both the LLM's structured-output contract and
 * (serialized) the payload persisted in Room, so the two can never drift.
 *
 * `@SerialName` values are the wire names the JSON schema declares; keep them
 * in sync with [PuzzleSchema].
 */
@Serializable
data class Puzzle(
    val skill: Skill,
    val theme: String,
    /** The puzzle as shown to the player. */
    val question: String,
    @SerialName("answer_kind") val answerKind: AnswerKind,
    /** Canonical answer: the number, the correct option index, or the correct order. */
    @SerialName("answer_integer") val answerInteger: Long? = null,
    @SerialName("answer_approximate") val answerApproximate: Double? = null,
    @SerialName("answer_option_index") val answerOptionIndex: Int? = null,
    @SerialName("answer_order") val answerOrder: List<Int>? = null,
    /** Choices for MULTIPLE_CHOICE, or the items to arrange for ORDERING. */
    val options: List<String> = emptyList(),
    /** Accepted relative error for APPROXIMATE, e.g. 25.0 means ±25%. */
    @SerialName("tolerance_percent") val tolerancePercent: Double? = null,
    /** Progressive nudges, cheapest first. Revealed one at a time on request. */
    val hints: List<String> = emptyList(),
    /** Worked solution, shown after the puzzle is finished. */
    val solution: String,
    /** The unit or shape of the expected answer, e.g. "yojanas" or "GB". */
    @SerialName("answer_label") val answerLabel: String = "",
)

/**
 * Why a generated puzzle was rejected. Surfaced in logs and (as a short note)
 * in the UI when generation had to retry, so a silent retry never looks like a
 * hang.
 */
data class Rejection(val reason: String)

/**
 * Structural validation of a model-generated puzzle. Structured outputs already
 * guarantee the JSON parses and has the right fields; this checks the semantics
 * the schema can't express — that the answer actually exists, is in range, and
 * that a multiple-choice puzzle has choices to pick from.
 *
 * This is the gate that makes a wrong-but-plausible generation the app's
 * problem rather than the player's.
 */
object PuzzleValidator {

    fun validate(p: Puzzle): Rejection? {
        if (p.question.isBlank()) return Rejection("question is blank")
        if (p.solution.isBlank()) return Rejection("solution is blank")

        return when (p.answerKind) {
            AnswerKind.INTEGER ->
                if (p.answerInteger == null) Rejection("INTEGER puzzle has no answer_integer") else null

            AnswerKind.APPROXIMATE -> when {
                p.answerApproximate == null -> Rejection("APPROXIMATE puzzle has no answer_approximate")
                !p.answerApproximate.isFinite() -> Rejection("answer_approximate is not finite")
                p.tolerancePercent == null -> Rejection("APPROXIMATE puzzle has no tolerance_percent")
                p.tolerancePercent <= 0.0 -> Rejection("tolerance_percent must be positive")
                p.tolerancePercent > 100.0 -> Rejection("tolerance_percent above 100 accepts almost anything")
                else -> null
            }

            AnswerKind.MULTIPLE_CHOICE -> when {
                p.options.size < 2 -> Rejection("MULTIPLE_CHOICE needs at least 2 options")
                p.options.any { it.isBlank() } -> Rejection("MULTIPLE_CHOICE has a blank option")
                p.options.distinct().size != p.options.size -> Rejection("MULTIPLE_CHOICE has duplicate options")
                p.answerOptionIndex == null -> Rejection("MULTIPLE_CHOICE has no answer_option_index")
                p.answerOptionIndex !in p.options.indices -> Rejection("answer_option_index out of range")
                else -> null
            }

            AnswerKind.ORDERING -> when {
                p.options.size < 3 -> Rejection("ORDERING needs at least 3 items")
                p.options.any { it.isBlank() } -> Rejection("ORDERING has a blank item")
                p.answerOrder == null -> Rejection("ORDERING has no answer_order")
                p.answerOrder.size != p.options.size ->
                    Rejection("answer_order length ${p.answerOrder.size} != ${p.options.size} items")
                p.answerOrder.sorted() != p.options.indices.toList() ->
                    Rejection("answer_order is not a permutation of the item indices")
                else -> null
            }
        }
    }
}

/** The player's submitted answer, in the shape matching the puzzle's [AnswerKind]. */
sealed interface Attempt {
    data class Number(val value: Double) : Attempt
    data class Choice(val index: Int) : Attempt
    data class Order(val order: List<Int>) : Attempt
}

/** Deterministic grading. No LLM call — correctness is never a judgement call. */
object AnswerChecker {

    fun isCorrect(puzzle: Puzzle, attempt: Attempt): Boolean = when (puzzle.answerKind) {
        AnswerKind.INTEGER -> {
            val expected = puzzle.answerInteger
            val given = (attempt as? Attempt.Number)?.value
            expected != null && given != null && given == Math.floor(given) &&
                given.toLong() == expected
        }

        AnswerKind.APPROXIMATE -> {
            val expected = puzzle.answerApproximate
            val tolerance = puzzle.tolerancePercent
            val given = (attempt as? Attempt.Number)?.value
            if (expected == null || tolerance == null || given == null) {
                false
            } else {
                // Relative error against the expected magnitude; an expected value of
                // zero degenerates to an absolute window so the check still works.
                val allowed = if (expected == 0.0) tolerance / 100.0
                else Math.abs(expected) * tolerance / 100.0
                Math.abs(given - expected) <= allowed
            }
        }

        AnswerKind.MULTIPLE_CHOICE ->
            puzzle.answerOptionIndex != null &&
                (attempt as? Attempt.Choice)?.index == puzzle.answerOptionIndex

        AnswerKind.ORDERING ->
            puzzle.answerOrder != null &&
                (attempt as? Attempt.Order)?.order == puzzle.answerOrder
    }
}
