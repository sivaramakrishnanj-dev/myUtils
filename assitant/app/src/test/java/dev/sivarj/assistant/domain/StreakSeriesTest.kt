package dev.sivarj.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakSeriesTest {

    private val today = 20_000L

    @Test
    fun `empty history is all zeros`() {
        val s = streakSeries(emptyList(), today, 7)
        assertEquals(List(7) { 0 }, s)
    }

    @Test
    fun `growing streak counts up`() {
        // checked the last 3 days of a 5-day window
        val s = streakSeries(listOf(today - 2, today - 1, today), today, 5)
        assertEquals(listOf(0, 0, 1, 2, 3), s)
    }

    @Test
    fun `streak started before window carries its height in`() {
        // checked for 4 days, window only shows the last 2
        val s = streakSeries(listOf(today - 3, today - 2, today - 1, today), today, 2)
        assertEquals(listOf(3, 4), s)
    }

    @Test
    fun `break resets to zero`() {
        val s = streakSeries(listOf(today - 4, today - 3, today - 1, today), today, 5)
        assertEquals(listOf(1, 2, 0, 1, 2), s)
    }

    @Test
    fun `history lists all completed runs oldest first`() {
        // runs: [2 days], [3 days], [1 day]
        val checkins = listOf(
            today - 10, today - 9,          // run of 2
            today - 6, today - 5, today - 4, // run of 3
            today,                            // run of 1
        )
        assertEquals(listOf(2, 3, 1), streakHistory(checkins))
    }

    @Test
    fun `history of empty is empty`() {
        assertEquals(emptyList<Int>(), streakHistory(emptyList()))
    }

    @Test
    fun `single day history`() {
        assertEquals(listOf(1), streakHistory(listOf(today)))
    }
}
