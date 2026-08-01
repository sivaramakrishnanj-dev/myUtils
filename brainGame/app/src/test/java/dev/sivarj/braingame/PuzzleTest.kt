package dev.sivarj.braingame

import dev.sivarj.braingame.domain.AnswerChecker
import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.Attempt
import dev.sivarj.braingame.domain.Puzzle
import dev.sivarj.braingame.domain.PuzzleValidator
import dev.sivarj.braingame.domain.Skill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleValidatorTest {

    private fun base(
        kind: AnswerKind,
        question: String = "How far did Hanuman leap?",
        solution: String = "Multiply speed by time.",
    ) = Puzzle(
        skill = Skill.ARITHMETIC,
        theme = "Ramayana",
        question = question,
        answerKind = kind,
        solution = solution,
    )

    @Test
    fun `valid integer puzzle passes`() {
        val puzzle = base(AnswerKind.INTEGER).copy(answerInteger = 100)
        assertNull(PuzzleValidator.validate(puzzle))
    }

    @Test
    fun `integer puzzle without an answer is rejected`() {
        assertNotNull(PuzzleValidator.validate(base(AnswerKind.INTEGER)))
    }

    @Test
    fun `blank question or solution is rejected`() {
        assertNotNull(PuzzleValidator.validate(base(AnswerKind.INTEGER, question = "  ").copy(answerInteger = 1)))
        assertNotNull(PuzzleValidator.validate(base(AnswerKind.INTEGER, solution = "").copy(answerInteger = 1)))
    }

    @Test
    fun `approximate puzzle needs a usable tolerance`() {
        val ok = base(AnswerKind.APPROXIMATE).copy(answerApproximate = 5.0e6, tolerancePercent = 30.0)
        assertNull(PuzzleValidator.validate(ok))

        assertNotNull(PuzzleValidator.validate(ok.copy(tolerancePercent = null)))
        assertNotNull(PuzzleValidator.validate(ok.copy(tolerancePercent = 0.0)))
        // A tolerance above 100% would accept nearly anything, making the puzzle pointless.
        assertNotNull(PuzzleValidator.validate(ok.copy(tolerancePercent = 150.0)))
        assertNotNull(PuzzleValidator.validate(ok.copy(answerApproximate = Double.NaN)))
    }

    @Test
    fun `multiple choice needs distinct options and an in-range answer`() {
        val ok = base(AnswerKind.MULTIPLE_CHOICE)
            .copy(options = listOf("2", "4", "8", "16"), answerOptionIndex = 1)
        assertNull(PuzzleValidator.validate(ok))

        assertNotNull(PuzzleValidator.validate(ok.copy(answerOptionIndex = 4)))
        assertNotNull(PuzzleValidator.validate(ok.copy(answerOptionIndex = null)))
        assertNotNull(PuzzleValidator.validate(ok.copy(options = listOf("2"))))
        assertNotNull(PuzzleValidator.validate(ok.copy(options = listOf("2", "2", "8", "16"))))
        assertNotNull(PuzzleValidator.validate(ok.copy(options = listOf("2", "  ", "8", "16"))))
    }

    @Test
    fun `ordering answer must be a permutation of the item indices`() {
        val ok = base(AnswerKind.ORDERING)
            .copy(options = listOf("c", "a", "d", "b"), answerOrder = listOf(1, 3, 0, 2))
        assertNull(PuzzleValidator.validate(ok))

        // Duplicate index — not a permutation.
        assertNotNull(PuzzleValidator.validate(ok.copy(answerOrder = listOf(1, 1, 0, 2))))
        // Wrong length.
        assertNotNull(PuzzleValidator.validate(ok.copy(answerOrder = listOf(0, 1, 2))))
        // Out-of-range index.
        assertNotNull(PuzzleValidator.validate(ok.copy(answerOrder = listOf(1, 3, 0, 9))))
        assertNotNull(PuzzleValidator.validate(ok.copy(answerOrder = null)))
        assertNotNull(PuzzleValidator.validate(ok.copy(options = listOf("a", "b"), answerOrder = listOf(0, 1))))
    }
}

class AnswerCheckerTest {

    private val integerPuzzle = Puzzle(
        skill = Skill.ARITHMETIC,
        theme = "Ramayana",
        question = "q",
        answerKind = AnswerKind.INTEGER,
        answerInteger = 42,
        solution = "s",
    )

    @Test
    fun `integer answer must match exactly`() {
        assertTrue(AnswerChecker.isCorrect(integerPuzzle, Attempt.Number(42.0)))
        assertFalse(AnswerChecker.isCorrect(integerPuzzle, Attempt.Number(43.0)))
        // A non-integral value is not an integer answer, even if it rounds to one.
        assertFalse(AnswerChecker.isCorrect(integerPuzzle, Attempt.Number(42.5)))
    }

    @Test
    fun `mismatched attempt type is simply wrong rather than crashing`() {
        assertFalse(AnswerChecker.isCorrect(integerPuzzle, Attempt.Choice(0)))
        assertFalse(AnswerChecker.isCorrect(integerPuzzle, Attempt.Order(listOf(0, 1))))
    }

    @Test
    fun `approximate answer accepts within tolerance and rejects outside`() {
        val puzzle = integerPuzzle.copy(
            answerKind = AnswerKind.APPROXIMATE,
            answerInteger = null,
            answerApproximate = 1000.0,
            tolerancePercent = 25.0,
        )
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Number(1000.0)))
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Number(1250.0)))
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Number(750.0)))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Number(1251.0)))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Number(500.0)))
    }

    @Test
    fun `approximate tolerance works for negative expected values`() {
        val puzzle = integerPuzzle.copy(
            answerKind = AnswerKind.APPROXIMATE,
            answerInteger = null,
            answerApproximate = -200.0,
            tolerancePercent = 10.0,
        )
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Number(-210.0)))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Number(-260.0)))
    }

    @Test
    fun `multiple choice compares the selected index`() {
        val puzzle = integerPuzzle.copy(
            answerKind = AnswerKind.MULTIPLE_CHOICE,
            answerInteger = null,
            options = listOf("a", "b", "c"),
            answerOptionIndex = 2,
        )
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Choice(2)))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Choice(0)))
    }

    @Test
    fun `ordering requires the exact arrangement`() {
        val puzzle = integerPuzzle.copy(
            answerKind = AnswerKind.ORDERING,
            answerInteger = null,
            options = listOf("c", "a", "b"),
            answerOrder = listOf(1, 2, 0),
        )
        assertTrue(AnswerChecker.isCorrect(puzzle, Attempt.Order(listOf(1, 2, 0))))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Order(listOf(0, 1, 2))))
        assertFalse(AnswerChecker.isCorrect(puzzle, Attempt.Order(listOf(1, 2))))
    }
}

class PuzzleSerializationTest {

    /**
     * The puzzle is persisted as JSON, so a round-trip has to be lossless or a
     * resumed puzzle would come back subtly different from the one generated.
     */
    @Test
    fun `puzzle survives a serialization round trip`() {
        val original = Puzzle(
            skill = Skill.ORDERING,
            theme = "LLMs and machine learning",
            question = "Order the steps of attention computation.",
            answerKind = AnswerKind.ORDERING,
            answerOrder = listOf(2, 0, 3, 1),
            options = listOf("softmax", "compute QK^T", "scale by sqrt(d)", "multiply by V"),
            hints = listOf("h1", "h2", "h3"),
            solution = "Q, K, V then scale, softmax, multiply.",
            answerLabel = "",
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(Puzzle.serializer(), original)
        val decoded = json.decodeFromString(Puzzle.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
