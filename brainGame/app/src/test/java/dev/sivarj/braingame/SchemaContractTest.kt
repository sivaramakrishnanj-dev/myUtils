package dev.sivarj.braingame

import dev.sivarj.braingame.ai.GeneratedPuzzle
import dev.sivarj.braingame.ai.PuzzleSchema
import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.PuzzleValidator
import dev.sivarj.braingame.domain.Skill
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the boundary between the JSON schema sent to the API and the Kotlin
 * type that decodes the reply. A mismatch here is the failure mode structured
 * outputs is supposed to eliminate, so it is worth pinning down with tests
 * rather than discovering at runtime.
 */
class SchemaContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `schema declares every property it requires`() {
        val required = PuzzleSchema.json["required"]!!.jsonArray
            .map { it.jsonPrimitive.content }.toSet()
        val properties = PuzzleSchema.json["properties"]!!.jsonObject.keys

        assertEquals(
            "every required field must be declared in properties",
            emptySet<String>(),
            required - properties,
        )
        // Structured outputs requires every property to appear in `required`;
        // optionality is expressed by allowing null, not by omission.
        assertEquals(
            "every property must be listed in required",
            emptySet<String>(),
            properties - required,
        )
    }

    @Test
    fun `schema forbids extra properties`() {
        assertEquals(
            false,
            PuzzleSchema.json["additionalProperties"]!!.jsonPrimitive.content.toBoolean(),
        )
    }

    @Test
    fun `schema enumerates exactly the answer kinds the app can grade`() {
        val enum = PuzzleSchema.json["properties"]!!.jsonObject["answer_kind"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(AnswerKind.entries.map { it.name }.toSet(), enum)
    }

    @Test
    fun `every schema property name matches a decodable field`() {
        // A property in the schema that the DTO ignores would mean the model is
        // asked for data the app then silently drops.
        val properties = PuzzleSchema.json["properties"]!!.jsonObject.keys
        val allNull = properties.associateWith { "null" }
            .entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        // Fill in the two non-nullable fields so the decode can succeed.
        val body = "{$allNull}"
            .replace("\"question\":null", "\"question\":\"q\"")
            .replace("\"solution\":null", "\"solution\":\"s\"")
            .replace("\"answer_kind\":null", "\"answer_kind\":\"INTEGER\"")
            .replace("\"options\":null", "\"options\":[]")
            .replace("\"hints\":null", "\"hints\":[]")
            .replace("\"answer_label\":null", "\"answer_label\":\"\"")

        val decoded = json.decodeFromString<GeneratedPuzzle>(body)
        assertEquals("q", decoded.question)
        assertEquals(AnswerKind.INTEGER, decoded.answerKind)
    }

    @Test
    fun `a realistic integer response decodes and validates`() {
        // Shaped like an actual structured-output reply, nulls included.
        val body = """
            {
              "question": "Hanuman leaps 100 yojanas in 4 hours. At the same speed, how many yojanas does he cover in 10 hours?",
              "answer_kind": "INTEGER",
              "answer_integer": 250,
              "answer_approximate": null,
              "answer_option_index": null,
              "answer_order": null,
              "options": [],
              "tolerance_percent": null,
              "hints": [
                "Find the rate before scaling it up.",
                "100 yojanas over 4 hours is 25 yojanas per hour.",
                "Multiply the hourly rate by 10 hours."
              ],
              "solution": "The rate is 100 / 4 = 25 yojanas per hour. Over 10 hours that is 25 x 10 = 250 yojanas.",
              "answer_label": "yojanas"
            }
        """.trimIndent()

        val generated = json.decodeFromString<GeneratedPuzzle>(body)
        val puzzle = generated.toPuzzle(Skill.ARITHMETIC, "Ramayana")

        assertNull(PuzzleValidator.validate(puzzle))
        assertEquals(250L, puzzle.answerInteger)
        assertEquals("yojanas", puzzle.answerLabel)
        assertEquals(3, puzzle.hints.size)
        // Skill and theme come from the request, not the model, so they can't disagree.
        assertEquals(Skill.ARITHMETIC, puzzle.skill)
        assertEquals("Ramayana", puzzle.theme)
    }

    @Test
    fun `a realistic ordering response decodes and validates`() {
        val body = """
            {
              "question": "Put the steps of scaled dot-product attention in order.",
              "answer_kind": "ORDERING",
              "answer_integer": null,
              "answer_approximate": null,
              "answer_option_index": null,
              "answer_order": [2, 0, 3, 1],
              "options": [
                "Divide by the square root of the key dimension",
                "Apply softmax across the scores",
                "Multiply queries by transposed keys",
                "Multiply the weights by the values"
              ],
              "tolerance_percent": null,
              "hints": ["h1", "h2", "h3"],
              "solution": "Scores come first, then scaling, then softmax, then the value product.",
              "answer_label": ""
            }
        """.trimIndent()

        val puzzle = json.decodeFromString<GeneratedPuzzle>(body)
            .toPuzzle(Skill.ORDERING, "LLMs and machine learning")
        assertNull(PuzzleValidator.validate(puzzle))
        assertEquals(listOf(2, 0, 3, 1), puzzle.answerOrder)
    }

    @Test
    fun `a realistic estimation response decodes and validates`() {
        val body = """
            {
              "question": "Roughly how many tokens are in a 24,000-verse text averaging 30 words per verse?",
              "answer_kind": "APPROXIMATE",
              "answer_integer": null,
              "answer_approximate": 960000,
              "answer_option_index": null,
              "answer_order": null,
              "options": [],
              "tolerance_percent": 35,
              "hints": ["h1", "h2", "h3"],
              "solution": "24,000 x 30 = 720,000 words; at about 1.3 tokens per word that is roughly 960,000 tokens.",
              "answer_label": "tokens"
            }
        """.trimIndent()

        val puzzle = json.decodeFromString<GeneratedPuzzle>(body)
            .toPuzzle(Skill.ESTIMATION, "LLMs and machine learning")
        assertNull(PuzzleValidator.validate(puzzle))
        assertEquals(35.0, puzzle.tolerancePercent!!, 0.0001)
    }

    @Test
    fun `a response with a broken answer is caught by validation not by decoding`() {
        // Decodes cleanly (the schema is satisfied) but the cross-field
        // constraint is violated: this is exactly what PuzzleValidator is for,
        // and why generation retries rather than serving the puzzle.
        val body = """
            {
              "question": "Pick the right one.",
              "answer_kind": "MULTIPLE_CHOICE",
              "answer_integer": null,
              "answer_approximate": null,
              "answer_option_index": 7,
              "answer_order": null,
              "options": ["a", "b", "c", "d"],
              "tolerance_percent": null,
              "hints": ["h1", "h2", "h3"],
              "solution": "It is b.",
              "answer_label": ""
            }
        """.trimIndent()

        val puzzle = json.decodeFromString<GeneratedPuzzle>(body).toPuzzle(Skill.LOGIC, "Everyday life")
        val rejection = PuzzleValidator.validate(puzzle)
        assertNotNull("out-of-range answer index must be rejected", rejection)
        assertTrue(rejection!!.reason.contains("out of range"))
    }

    @Test
    fun `nullable schema fields accept both a value and null`() {
        val properties = PuzzleSchema.json["properties"]!!.jsonObject
        listOf("answer_integer", "answer_approximate", "answer_option_index", "answer_order")
            .forEach { field ->
                val variants = properties[field]!!.jsonObject["anyOf"]!!.jsonArray
                    .map { (it as JsonObject)["type"]!!.jsonPrimitive.content }
                assertTrue("$field must allow null", variants.contains("null"))
                assertEquals("$field should be a two-variant union", 2, variants.size)
            }
    }
}
