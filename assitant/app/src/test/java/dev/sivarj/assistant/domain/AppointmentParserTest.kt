package dev.sivarj.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppointmentParserTest {

    @Test
    fun `parses clean json`() {
        val raw = """{"title": "Team standup", "notes": "", "startHour": 10, "startMin": 0, "endHour": 10, "endMin": 30}"""
        val result = parseAppointmentJson(raw)
        assertNotNull(result)
        assertEquals("Team standup", result!!.title)
        assertEquals(600, result.startMinutes) // 10:00
        assertEquals(630, result.endMinutes)   // 10:30
    }

    @Test
    fun `extracts json from surrounding prose`() {
        val raw = """Here is the appointment info: {"title": "Dentist", "notes": "Bring insurance card", "startHour": 14, "startMin": 30, "endHour": 15, "endMin": 0} Hope that helps!"""
        val result = parseAppointmentJson(raw)
        assertNotNull(result)
        assertEquals("Dentist", result!!.title)
        assertEquals("Bring insurance card", result.notes)
        assertEquals(14 * 60 + 30, result.startMinutes)
        assertEquals(15 * 60, result.endMinutes)
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(parseAppointmentJson("no json here"))
        assertNull(parseAppointmentJson("{garbage"))
    }

    @Test
    fun `returns null for empty title`() {
        assertNull(parseAppointmentJson("""{"title": "", "startHour": 9, "startMin": 0, "endHour": 10, "endMin": 0}"""))
    }

    @Test
    fun `clamps out-of-range hours and minutes`() {
        val raw = """{"title": "Late", "notes": "", "startHour": 25, "startMin": 70, "endHour": -1, "endMin": -5}"""
        val result = parseAppointmentJson(raw)
        assertNotNull(result)
        assertEquals(23 * 60 + 59, result!!.startMinutes) // clamped to 23:59
        assertEquals(0, result.endMinutes)                 // clamped to 0:00
    }
}
