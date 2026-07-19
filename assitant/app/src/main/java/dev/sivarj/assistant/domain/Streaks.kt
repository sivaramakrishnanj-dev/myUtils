package dev.sivarj.assistant.domain

data class StreakResult(
    val current: Int,
    val longest: Int,
    val checkedToday: Boolean,
)

/**
 * Computes habit streaks from raw check-in days.
 *
 * @param checkinEpochDays days (LocalDate.toEpochDay values) with a check-in;
 *   may be unsorted and contain duplicates.
 * @param todayEpochDay the caller's notion of "today", so tests and UI agree
 *   on the timezone used.
 *
 * A current streak survives a missing "today" (you may still check in later),
 * but is broken once yesterday is also missing.
 */
fun computeStreak(checkinEpochDays: Collection<Long>, todayEpochDay: Long): StreakResult {
    val days = checkinEpochDays.toSortedSet()
    if (days.isEmpty()) return StreakResult(current = 0, longest = 0, checkedToday = false)

    var longest = 1
    var run = 1
    var prev: Long? = null
    for (day in days) {
        if (prev != null) {
            run = if (day == prev + 1) run + 1 else 1
            if (run > longest) longest = run
        }
        prev = day
    }

    val checkedToday = todayEpochDay in days
    val anchor = when {
        checkedToday -> todayEpochDay
        (todayEpochDay - 1) in days -> todayEpochDay - 1
        else -> null
    }
    var current = 0
    if (anchor != null) {
        var d = anchor
        while (d in days) {
            current++
            d--
        }
    }
    return StreakResult(current = current, longest = longest, checkedToday = checkedToday)
}
