package dev.sivarj.braingame.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Elo rating maths, borrowed from chess: the player and each puzzle carry a
 * rating, and the update size depends on how surprising the result was. Beating
 * a puzzle far above your rating moves you a lot; beating an easy one barely
 * moves you at all.
 *
 * Kept free of Android and Room types so it can be unit-tested directly.
 */
object Elo {

    /** Where a new player starts, and the rating of a puzzle we know nothing about. */
    const val DEFAULT_RATING = 1200

    /** Ratings below this are meaningless — puzzles can't get much easier. */
    const val MIN_RATING = 600

    /** Ceiling, to stop a hot streak running away. */
    const val MAX_RATING = 2400

    /**
     * Step size. Higher means faster adaptation but noisier ratings; 32 is the
     * standard chess K-factor and adapts within a handful of puzzles, which
     * suits short bored-on-a-train sessions.
     */
    private const val K_ESTABLISHED = 24.0

    /**
     * A larger step while the rating is still being found, so the first few
     * puzzles home in quickly instead of crawling from 1200.
     */
    private const val K_PROVISIONAL = 48.0

    /** Attempts within a skill before its rating is treated as established. */
    private const val PROVISIONAL_ATTEMPTS = 5

    /**
     * Probability the player solves a puzzle at [puzzleRating], given their
     * [playerRating]. The standard logistic curve: equal ratings give 0.5, and
     * each 400 points of advantage is roughly a 10x odds shift.
     */
    fun expectedScore(playerRating: Int, puzzleRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((puzzleRating - playerRating) / 400.0))

    /**
     * The player's new rating after a puzzle.
     *
     * [score] is 1.0 for a clean solve, 0.0 for a failure, and something in
     * between when the solve needed help — see [scoreFor].
     */
    fun updatedRating(
        playerRating: Int,
        puzzleRating: Int,
        score: Double,
        attemptsInSkill: Int,
    ): Int {
        val k = if (attemptsInSkill < PROVISIONAL_ATTEMPTS) K_PROVISIONAL else K_ESTABLISHED
        val expected = expectedScore(playerRating, puzzleRating)
        val raw = playerRating + k * (score - expected)
        return raw.roundToInt().coerceIn(MIN_RATING, MAX_RATING)
    }

    /**
     * Partial credit. A solve after two hints and four wrong guesses shouldn't
     * count the same as a first-try solve, or the rating inflates past what the
     * player can actually do unaided.
     *
     * Solved: start at 1.0, lose 0.2 per hint revealed and 0.1 per wrong guess,
     * with a floor of 0.35 so a hard-won solve still beats giving up.
     * Failed: 0.0.
     */
    fun scoreFor(solved: Boolean, hintsUsed: Int, wrongAttempts: Int): Double {
        if (!solved) return 0.0
        val penalty = 0.2 * hintsUsed + 0.1 * wrongAttempts
        return max(0.35, 1.0 - penalty)
    }
}

/** A player's standing in one skill. */
data class SkillRating(
    val skill: Skill,
    val rating: Int = Elo.DEFAULT_RATING,
    val attempts: Int = 0,
    val solved: Int = 0,
) {
    val successRate: Double get() = if (attempts == 0) 0.0 else solved.toDouble() / attempts
}

/**
 * Picks what to serve next: which skill needs work, and how hard the puzzle
 * should be.
 *
 * Two ideas drive it. First, bias toward the player's weakest skill so practice
 * goes where it's needed — but keep exploring the others, since a skill that's
 * never served can never show improvement. Second, aim slightly *above* the
 * current rating: puzzles you always solve don't teach anything.
 */
object SkillSelector {

    /**
     * How much harder than the player's rating to aim. +100 puts the expected
     * success rate near 64%, which is hard enough to require thought and easy
     * enough to stay enjoyable.
     */
    private const val DIFFICULTY_STRETCH = 100

    /** Random jitter so consecutive puzzles at the same rating still vary. */
    private const val RATING_JITTER = 60

    /**
     * Chance of ignoring the weakness bias and picking a skill at random
     * (epsilon-greedy exploration). Without this, one weak skill would crowd
     * out everything else.
     */
    private const val EXPLORE_PROBABILITY = 0.30

    /**
     * Choose the next skill. Prefers the lowest-rated skill, but explores
     * randomly [EXPLORE_PROBABILITY] of the time, and always prefers a skill
     * that has never been attempted so every dimension gets a first data point.
     */
    fun pickSkill(
        ratings: List<SkillRating>,
        random: () -> Double = Math::random,
    ): Skill {
        if (ratings.isEmpty()) return Skill.entries.first()

        val untried = ratings.filter { it.attempts == 0 }
        if (untried.isNotEmpty()) return untried.minByOrNull { it.skill.ordinal }!!.skill

        if (random() < EXPLORE_PROBABILITY) {
            val index = (random() * ratings.size).toInt().coerceIn(0, ratings.size - 1)
            return ratings[index].skill
        }
        return ratings.minByOrNull { it.rating }!!.skill
    }

    /**
     * Target difficulty for a puzzle in [skill], stretched above the player's
     * current rating and jittered so repeats don't feel identical.
     */
    fun targetRating(
        current: SkillRating,
        random: () -> Double = Math::random,
    ): Int {
        val jitter = ((random() * 2 - 1) * RATING_JITTER).roundToInt()
        return (current.rating + DIFFICULTY_STRETCH + jitter)
            .coerceIn(Elo.MIN_RATING, Elo.MAX_RATING)
    }

    /**
     * Maps a numeric target rating onto the plain-language difficulty band the
     * generation prompt uses. The model calibrates far better against "a
     * competent adult should need about a minute" than against "1450".
     */
    fun difficultyBand(targetRating: Int): String = when {
        targetRating < 900 -> "very easy — a competent adult solves it in about 15 seconds, single step"
        targetRating < 1100 -> "easy — one or two steps, solvable in under 30 seconds"
        targetRating < 1350 -> "moderate — two or three steps, about a minute of thought"
        targetRating < 1600 -> "challenging — several steps or a non-obvious insight, a few minutes"
        targetRating < 1900 -> "hard — requires a genuine insight plus careful multi-step work"
        else -> "very hard — a puzzle an enthusiast would find satisfying; multiple insights required"
    }

    /**
     * Rotates themes with a bias toward ones played recently, on the assumption
     * that a theme chosen in settings is a theme the player is interested in
     * right now. [recent] should be most-recent-first.
     */
    fun pickTheme(
        enabled: List<String>,
        recent: List<String>,
        random: () -> Double = Math::random,
    ): String {
        if (enabled.isEmpty()) return Themes.EVERYDAY
        if (enabled.size == 1) return enabled.first()

        // Avoid immediately repeating the last theme so sessions feel varied.
        val candidates = enabled.filter { it != recent.firstOrNull() }.ifEmpty { enabled }
        val index = (random() * candidates.size).toInt().coerceIn(0, candidates.size - 1)
        return candidates[index]
    }
}

/**
 * Turns a puzzle's target rating into the rating actually used for the Elo
 * update. The model is told the difficulty band, not the number, so the served
 * puzzle may miss the target — but the target is still the best estimate we
 * have, and mis-estimates wash out over many puzzles.
 */
fun puzzleRatingFor(targetRating: Int): Int =
    targetRating.coerceIn(Elo.MIN_RATING, Elo.MAX_RATING)

/** Formats a rating delta for display, e.g. "+18" or "-7". */
fun formatDelta(before: Int, after: Int): String {
    val delta = after - before
    val sign = if (delta >= 0) "+" else "-"
    return "$sign${abs(delta)}"
}

/** Clamps a hint index into the available hints, tolerating a short hint list. */
fun hintAt(hints: List<String>, index: Int): String? =
    if (index in hints.indices) hints[index] else null

/** True when every hint has already been shown. */
fun allHintsUsed(hints: List<String>, used: Int): Boolean = used >= min(hints.size, Int.MAX_VALUE)
