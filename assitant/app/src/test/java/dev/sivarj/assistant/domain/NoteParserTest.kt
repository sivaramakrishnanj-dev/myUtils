package dev.sivarj.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParserTest {

    @Test
    fun `parses clean json`() {
        val raw = """{"title": "Garden irrigation plan", "body": "Install drip lines.\nAdd a timer."}"""
        val r = parseNoteJson(raw)
        assertEquals("Garden irrigation plan", r.title)
        assertTrue(r.body.contains("drip lines"))
        assertTrue(r.body.contains("\n"))
    }

    @Test
    fun `extracts json from surrounding prose`() {
        val raw = """Here you go: {"title": "Trip ideas", "body": "Visit Kyoto in spring."} Hope this helps!"""
        val r = parseNoteJson(raw)
        assertEquals("Trip ideas", r.title)
        assertEquals("Visit Kyoto in spring.", r.body)
    }

    @Test
    fun `braces inside strings do not truncate the object`() {
        val raw = """{"title": "Code note", "body": "Use fun main() { println() } in Kotlin"}"""
        val r = parseNoteJson(raw)
        assertEquals("Code note", r.title)
        assertTrue(r.body.contains("println()"))
    }

    @Test
    fun `falls back to whole text as body when not json`() {
        val raw = "Just some polished prose with no JSON at all."
        val r = parseNoteJson(raw)
        assertEquals("", r.title)
        assertEquals(raw, r.body)
    }

    @Test
    fun `falls back when json has empty body`() {
        val raw = """{"title": "Something", "body": ""}"""
        val r = parseNoteJson(raw)
        // Body is unusable, so treat the raw response as the body.
        assertEquals("", r.title)
        assertEquals(raw, r.body)
    }

    @Test
    fun `missing title is tolerated`() {
        val raw = """{"body": "Content without a title."}"""
        val r = parseNoteJson(raw)
        assertEquals("", r.title)
        assertEquals("Content without a title.", r.body)
    }
}
