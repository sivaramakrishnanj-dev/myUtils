package dev.sivarj.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeTimeTest {

    @Test
    fun `empty day is all free from now to midnight`() {
        val r = computeFreeTime(emptyList(), nowMinutes = 9 * 60)
        assertEquals(1, r.slots.size)
        assertEquals(9 * 60, r.slots[0].startMinutes)
        assertEquals(24 * 60, r.slots[0].endMinutes)
        assertEquals(15 * 60, r.totalFreeMinutes)
    }

    @Test
    fun `gap between two appointments is a free slot`() {
        // now 8:00, appts 9-10 and 14-15 → free 8-9, 10-14, 15-24
        val r = computeFreeTime(
            listOf(9 * 60..10 * 60, 14 * 60..15 * 60),
            nowMinutes = 8 * 60,
        )
        assertEquals(3, r.slots.size)
        assertEquals(FreeSlot(8 * 60, 9 * 60), r.slots[0])
        assertEquals(FreeSlot(10 * 60, 14 * 60), r.slots[1])
        assertEquals(FreeSlot(15 * 60, 24 * 60), r.slots[2])
        assertEquals(60 + 4 * 60 + 9 * 60, r.totalFreeMinutes)
    }

    @Test
    fun `past appointments do not affect free time`() {
        // now 12:00, morning appt already over
        val r = computeFreeTime(listOf(9 * 60..10 * 60), nowMinutes = 12 * 60)
        assertEquals(1, r.slots.size)
        assertEquals(12 * 60, r.slots[0].startMinutes)
        assertEquals(12 * 60, r.totalFreeMinutes)
    }

    @Test
    fun `appointment spanning now is clipped`() {
        // now 9:30, appt 9-10 → free starts at 10
        val r = computeFreeTime(listOf(9 * 60..10 * 60), nowMinutes = 9 * 60 + 30)
        assertEquals(1, r.slots.size)
        assertEquals(10 * 60, r.slots[0].startMinutes)
    }

    @Test
    fun `overlapping appointments are merged not double-counted`() {
        // now 8:00, appts 9-11 and 10-12 → blocked 9-12
        val r = computeFreeTime(
            listOf(9 * 60..11 * 60, 10 * 60..12 * 60),
            nowMinutes = 8 * 60,
        )
        assertEquals(2, r.slots.size)
        assertEquals(FreeSlot(8 * 60, 9 * 60), r.slots[0])
        assertEquals(FreeSlot(12 * 60, 24 * 60), r.slots[1])
        assertEquals(60 + 12 * 60, r.totalFreeMinutes)
    }

    @Test
    fun `fully booked rest of day has zero free time`() {
        val r = computeFreeTime(listOf(10 * 60..24 * 60), nowMinutes = 10 * 60)
        assertEquals(0, r.slots.size)
        assertEquals(0, r.totalFreeMinutes)
    }

    @Test
    fun `duration formatting`() {
        assertEquals("45m", formatDuration(45))
        assertEquals("2h", formatDuration(120))
        assertEquals("3h 25m", formatDuration(205))
        assertEquals("0m", formatDuration(0))
    }
}
