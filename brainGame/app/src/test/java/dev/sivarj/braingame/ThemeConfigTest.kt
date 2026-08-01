package dev.sivarj.braingame

import dev.sivarj.braingame.domain.Themes
import dev.sivarj.braingame.settings.AppConfig
import dev.sivarj.braingame.settings.sanitizeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `custom themes are offered alongside the built-ins`() {
        val config = AppConfig(customThemes = listOf("Carnatic music", "Kubernetes"))
        assertEquals(Themes.ALL + listOf("Carnatic music", "Kubernetes"), config.allThemes)
    }

    @Test
    fun `a custom theme duplicating a built-in is not listed twice`() {
        val config = AppConfig(customThemes = listOf(Themes.ASTRONOMY))
        assertEquals(Themes.ALL, config.allThemes)
    }

    @Test
    fun `generation only uses themes that still exist`() {
        // "Kubernetes" was enabled and then deleted; it must not leak into generation.
        val config = AppConfig(
            enabledThemes = listOf(Themes.RAMAYANA, "Kubernetes"),
            customThemes = emptyList(),
        )
        assertEquals(listOf(Themes.RAMAYANA), config.effectiveThemes)
    }

    @Test
    fun `a custom theme can be the only enabled theme`() {
        val config = AppConfig(
            enabledThemes = listOf("Carnatic music"),
            customThemes = listOf("Carnatic music"),
        )
        assertEquals(listOf("Carnatic music"), config.effectiveThemes)
    }

    @Test
    fun `deselecting everything falls back to all themes rather than blocking play`() {
        val config = AppConfig(enabledThemes = emptyList(), customThemes = listOf("Kubernetes"))
        assertEquals(Themes.ALL + listOf("Kubernetes"), config.effectiveThemes)
    }

    @Test
    fun `stale enabled entries alone also fall back rather than yielding nothing`() {
        // Every enabled theme has since been deleted — generation must still have
        // something to work with.
        val config = AppConfig(enabledThemes = listOf("Deleted"), customThemes = emptyList())
        assertTrue(config.effectiveThemes.isNotEmpty())
        assertEquals(Themes.ALL, config.effectiveThemes)
    }

    @Test
    fun `sanitize trims and collapses whitespace`() {
        assertEquals("Carnatic music", sanitizeTheme("  Carnatic   music  "))
        assertEquals("Indian classical dance", sanitizeTheme("Indian\tclassical\ndance"))
    }

    @Test
    fun `sanitize rejects input with no content`() {
        assertNull(sanitizeTheme(""))
        assertNull(sanitizeTheme("   "))
        assertNull(sanitizeTheme("\n\t "))
    }

    @Test
    fun `sanitize caps runaway length`() {
        val long = "a".repeat(500)
        assertEquals(60, sanitizeTheme(long)!!.length)
    }
}
