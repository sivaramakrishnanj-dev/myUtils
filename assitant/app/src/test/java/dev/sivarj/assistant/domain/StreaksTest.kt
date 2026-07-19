package dev.sivarj.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {

    private val today = 20_000L

    @Test
    fun `no checkins yields zero streaks`() {
        val r = computeStreak(emptyList(), today)
        assertEquals(0, r.current)
        assertEquals(0, r.longest)
        assertEquals(false, r.checkedToday)
    }

    @Test
    fun `single checkin today`() {
        val r = computeStreak(listOf(today), today)
        assertEquals(1, r.current)
        assertEquals(1, r.longest)
        assertEquals(true, r.checkedToday)
    }

    @Test
    fun `consecutive run ending today`() {
        val r = computeStreak(listOf(today - 2, today - 1, today), today)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test
    fun `missing today keeps streak alive via yesterday`() {
        val r = computeStreak(listOf(today - 3, today - 2, today - 1), today)
        assertEquals(3, r.current)
        assertEquals(false, r.checkedToday)
    }

    @Test
    fun `gap before yesterday breaks current streak`() {
        val r = computeStreak(listOf(today - 5, today - 4, today - 3), today)
        assertEquals(0, r.current)
        assertEquals(3, r.longest)
    }

    @Test
    fun `longest streak found in history not touching today`() {
        val history = listOf(today - 10, today - 9, today - 8, today - 7, today - 2, today - 1, today)
        val r = computeStreak(history, today)
        assertEquals(3, r.current)
        assertEquals(4, r.longest)
    }

    @Test
    fun `duplicates and unsorted input are tolerated`() {
        val r = computeStreak(listOf(today, today - 1, today, today - 2, today - 1), today)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test
    fun `future checkin does not inflate current streak`() {
        // e.g. device timezone moved backwards across midnight
        val r = computeStreak(listOf(today + 1), today)
        assertEquals(0, r.current)
        assertEquals(1, r.longest)
        assertEquals(false, r.checkedToday)
    }
}
