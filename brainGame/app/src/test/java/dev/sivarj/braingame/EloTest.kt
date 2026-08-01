package dev.sivarj.braingame

import dev.sivarj.braingame.domain.Elo
import dev.sivarj.braingame.domain.Skill
import dev.sivarj.braingame.domain.SkillRating
import dev.sivarj.braingame.domain.SkillSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EloTest {

    @Test
    fun `equal ratings give even odds`() {
        assertEquals(0.5, Elo.expectedScore(1200, 1200), 0.0001)
    }

    @Test
    fun `higher player rating raises expected score`() {
        val strong = Elo.expectedScore(1600, 1200)
        assertTrue("expected $strong to exceed 0.5", strong > 0.5)
        // 400 points is conventionally ~10:1 odds, i.e. ~0.909.
        assertEquals(0.909, strong, 0.01)
    }

    @Test
    fun `beating a harder puzzle gains more than beating an easy one`() {
        val gainVsHard = Elo.updatedRating(1200, 1600, score = 1.0, attemptsInSkill = 20) - 1200
        val gainVsEasy = Elo.updatedRating(1200, 800, score = 1.0, attemptsInSkill = 20) - 1200
        assertTrue("hard gain $gainVsHard should exceed easy gain $gainVsEasy", gainVsHard > gainVsEasy)
    }

    @Test
    fun `failing an easy puzzle loses more than failing a hard one`() {
        val lossVsEasy = 1200 - Elo.updatedRating(1200, 800, score = 0.0, attemptsInSkill = 20)
        val lossVsHard = 1200 - Elo.updatedRating(1200, 1600, score = 0.0, attemptsInSkill = 20)
        assertTrue("easy loss $lossVsEasy should exceed hard loss $lossVsHard", lossVsEasy > lossVsHard)
    }

    @Test
    fun `provisional ratings move faster than established ones`() {
        val provisional = Elo.updatedRating(1200, 1200, score = 1.0, attemptsInSkill = 0) - 1200
        val established = Elo.updatedRating(1200, 1200, score = 1.0, attemptsInSkill = 50) - 1200
        assertTrue("provisional $provisional should exceed established $established", provisional > established)
    }

    @Test
    fun `ratings stay within bounds under sustained wins and losses`() {
        var rating = Elo.DEFAULT_RATING
        repeat(200) { rating = Elo.updatedRating(rating, Elo.MAX_RATING, 1.0, 100) }
        assertTrue("rating $rating exceeded max", rating <= Elo.MAX_RATING)

        rating = Elo.DEFAULT_RATING
        repeat(200) { rating = Elo.updatedRating(rating, Elo.MIN_RATING, 0.0, 100) }
        assertTrue("rating $rating fell below min", rating >= Elo.MIN_RATING)
    }

    @Test
    fun `hints and wrong guesses reduce the score but keep a floor`() {
        assertEquals(1.0, Elo.scoreFor(solved = true, hintsUsed = 0, wrongAttempts = 0), 0.0001)
        assertEquals(0.8, Elo.scoreFor(solved = true, hintsUsed = 1, wrongAttempts = 0), 0.0001)
        assertEquals(0.7, Elo.scoreFor(solved = true, hintsUsed = 1, wrongAttempts = 1), 0.0001)
        // Heavy help still beats giving up.
        assertEquals(0.35, Elo.scoreFor(solved = true, hintsUsed = 3, wrongAttempts = 5), 0.0001)
        assertEquals(0.0, Elo.scoreFor(solved = false, hintsUsed = 0, wrongAttempts = 0), 0.0001)
    }

    @Test
    fun `a clean solve gains more rating than a heavily hinted one`() {
        val clean = Elo.updatedRating(1200, 1300, Elo.scoreFor(true, 0, 0), 20)
        val hinted = Elo.updatedRating(1200, 1300, Elo.scoreFor(true, 3, 2), 20)
        assertTrue("clean $clean should exceed hinted $hinted", clean > hinted)
    }

    @Test
    fun `selector serves an untried skill before biasing on rating`() {
        // ARITHMETIC is lowest-rated, but SEQUENCE has never been attempted.
        val ratings = listOf(
            SkillRating(Skill.ARITHMETIC, rating = 800, attempts = 10, solved = 5),
            SkillRating(Skill.LOGIC, rating = 1500, attempts = 10, solved = 8),
            SkillRating(Skill.SEQUENCE, rating = 1200, attempts = 0, solved = 0),
        )
        assertEquals(Skill.SEQUENCE, SkillSelector.pickSkill(ratings) { 0.99 })
    }

    @Test
    fun `selector picks the weakest skill when not exploring`() {
        val ratings = listOf(
            SkillRating(Skill.ARITHMETIC, rating = 1500, attempts = 5, solved = 4),
            SkillRating(Skill.LOGIC, rating = 900, attempts = 5, solved = 1),
        )
        // 0.99 is above the explore threshold, so the weakness bias applies.
        assertEquals(Skill.LOGIC, SkillSelector.pickSkill(ratings) { 0.99 })
    }

    @Test
    fun `target rating aims above the player's current rating`() {
        val rating = SkillRating(Skill.LOGIC, rating = 1200, attempts = 10, solved = 5)
        // Zero jitter: random() of 0.5 maps to the midpoint of the jitter range.
        val target = SkillSelector.targetRating(rating) { 0.5 }
        assertTrue("target $target should exceed 1200", target > 1200)
    }

    @Test
    fun `difficulty bands describe duration rather than exposing the number`() {
        val band = SkillSelector.difficultyBand(1450)
        assertTrue("band should be prose, was '$band'", band.isNotBlank())
        assertTrue("band must not leak the rating", !band.contains("1450"))
    }

    @Test
    fun `theme picker avoids repeating the previous theme`() {
        val enabled = listOf("Ramayana", "LLMs", "Astronomy")
        val picked = SkillSelector.pickTheme(enabled, recent = listOf("Ramayana")) { 0.0 }
        assertNotEquals("Ramayana", picked)
    }

    @Test
    fun `theme picker tolerates a single enabled theme`() {
        val only = listOf("Ramayana")
        assertEquals("Ramayana", SkillSelector.pickTheme(only, recent = listOf("Ramayana")) { 0.0 })
    }
}
