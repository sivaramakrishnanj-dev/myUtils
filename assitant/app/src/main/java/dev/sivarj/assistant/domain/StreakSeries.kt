package dev.sivarj.assistant.domain

/**
 * The length of the running streak on each day of a window, for charting.
 * A day with no check-in contributes 0; a checked day contributes the count
 * of consecutive checked days ending on it.
 */
fun streakSeries(checkinEpochDays: Collection<Long>, lastDay: Long, windowDays: Int): List<Int> {
    val days = checkinEpochDays.toHashSet()
    val firstDay = lastDay - windowDays + 1

    // Seed the run length from the days immediately before the window so a
    // streak that started earlier shows its true height on day one.
    var run = 0
    var d = firstDay - 1
    while (d in days) {
        run++
        d--
    }

    val out = ArrayList<Int>(windowDays)
    for (day in firstDay..lastDay) {
        run = if (day in days) run + 1 else 0
        out.add(run)
    }
    return out
}

/**
 * All completed streak lengths in history (each maximal run of consecutive
 * days), oldest first. Used to tell the "your streaks are improving" story.
 */
fun streakHistory(checkinEpochDays: Collection<Long>): List<Int> {
    if (checkinEpochDays.isEmpty()) return emptyList()
    val sorted = checkinEpochDays.toSortedSet().toList()
    val runs = mutableListOf<Int>()
    var run = 1
    for (i in 1 until sorted.size) {
        if (sorted[i] == sorted[i - 1] + 1) run++ else {
            runs.add(run)
            run = 1
        }
    }
    runs.add(run)
    return runs
}
