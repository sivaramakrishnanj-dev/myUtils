package dev.sivarj.assistant.domain

/** An open window in the day's schedule, in minutes since midnight. */
data class FreeSlot(val startMinutes: Int, val endMinutes: Int) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}

data class FreeTimeSummary(
    val slots: List<FreeSlot>,
    val totalFreeMinutes: Int,
)

/**
 * Computes the unbooked windows between [nowMinutes] and [dayEndMinutes].
 *
 * Time already in the past never counts as free; appointments are clipped to
 * the remaining window and overlapping/adjacent bookings are merged so a
 * double-booked half hour isn't subtracted twice.
 */
fun computeFreeTime(
    bookings: List<IntRange>,
    nowMinutes: Int,
    dayEndMinutes: Int = 24 * 60,
): FreeTimeSummary {
    val windowStart = nowMinutes.coerceIn(0, dayEndMinutes)

    // Clip bookings to [windowStart, dayEnd], drop empties, merge overlaps.
    val clipped = bookings
        .map { it.first.coerceAtLeast(windowStart)..it.last.coerceAtMost(dayEndMinutes) }
        .filter { it.first < it.last }
        .sortedBy { it.first }

    val merged = mutableListOf<IntRange>()
    for (b in clipped) {
        val last = merged.lastOrNull()
        if (last != null && b.first <= last.last) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, b.last)
        } else {
            merged.add(b)
        }
    }

    val slots = mutableListOf<FreeSlot>()
    var cursor = windowStart
    for (b in merged) {
        if (b.first > cursor) slots.add(FreeSlot(cursor, b.first))
        cursor = b.last
    }
    if (cursor < dayEndMinutes) slots.add(FreeSlot(cursor, dayEndMinutes))

    return FreeTimeSummary(slots = slots, totalFreeMinutes = slots.sumOf { it.durationMinutes })
}

/** "3h 25m", "45m", "2h" — for durations. */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
