package dev.sivarj.braingame.ai

import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.Puzzle
import dev.sivarj.braingame.domain.Skill
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What the model returns for a generation request.
 *
 * Deliberately does not include skill or theme: the app already decided those,
 * so asking the model to echo them back only creates a way for them to disagree.
 * [toPuzzle] fills them in from the request.
 */
@Serializable
data class GeneratedPuzzle(
    val question: String,
    @SerialName("answer_kind") val answerKind: AnswerKind,
    @SerialName("answer_integer") val answerInteger: Long? = null,
    @SerialName("answer_approximate") val answerApproximate: Double? = null,
    @SerialName("answer_option_index") val answerOptionIndex: Int? = null,
    @SerialName("answer_order") val answerOrder: List<Int>? = null,
    val options: List<String> = emptyList(),
    @SerialName("tolerance_percent") val tolerancePercent: Double? = null,
    val hints: List<String> = emptyList(),
    val solution: String,
    @SerialName("answer_label") val answerLabel: String = "",
) {
    fun toPuzzle(skill: Skill, theme: String): Puzzle = Puzzle(
        skill = skill,
        theme = theme,
        question = question,
        answerKind = answerKind,
        answerInteger = answerInteger,
        answerApproximate = answerApproximate,
        answerOptionIndex = answerOptionIndex,
        answerOrder = answerOrder,
        options = options,
        tolerancePercent = tolerancePercent,
        hints = hints,
        solution = solution,
        answerLabel = answerLabel,
    )
}

/**
 * JSON Schema handed to `output_config.format`, which guarantees the response is
 * valid JSON in this exact shape — no prose extraction, no brace counting.
 *
 * Two constraints from the structured-outputs implementation shape this:
 * every object needs `additionalProperties: false`, and every property must
 * appear in `required`. Fields that don't apply to a given answer kind are
 * therefore declared nullable rather than omitted, and the model is told to
 * send null for them.
 *
 * Numeric range and array-length constraints are not supported in schemas, so
 * those are enforced by [dev.sivarj.braingame.domain.PuzzleValidator] instead.
 */
object PuzzleSchema {

    val json: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            add("question")
            add("answer_kind")
            add("answer_integer")
            add("answer_approximate")
            add("answer_option_index")
            add("answer_order")
            add("options")
            add("tolerance_percent")
            add("hints")
            add("solution")
            add("answer_label")
        }
        putJsonObject("properties") {
            putJsonObject("question") {
                put("type", "string")
                put("description", "The puzzle as the player sees it. Self-contained.")
            }
            putJsonObject("answer_kind") {
                putJsonArray("enum") {
                    AnswerKind.entries.forEach { add(it.name) }
                }
                put("description", "How the answer is entered and checked.")
            }
            nullableOf("answer_integer", "integer", "Exact answer for INTEGER, else null.")
            nullableOf(
                "answer_approximate", "number",
                "Expected value for APPROXIMATE, else null.",
            )
            nullableOf(
                "answer_option_index", "integer",
                "Zero-based index of the correct entry in options for MULTIPLE_CHOICE, else null.",
            )
            putJsonObject("answer_order") {
                putJsonArray("anyOf") {
                    add(buildJsonObject {
                        put("type", "array")
                        putJsonObject("items") { put("type", "integer") }
                    })
                    add(buildJsonObject { put("type", "null") })
                }
                put(
                    "description",
                    "For ORDERING: the indices of options in correct order, " +
                        "a permutation of 0..n-1. Null otherwise.",
                )
            }
            putJsonObject("options") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
                put(
                    "description",
                    "Choices for MULTIPLE_CHOICE, or the items to arrange for " +
                        "ORDERING. Empty array for other kinds.",
                )
            }
            nullableOf(
                "tolerance_percent", "number",
                "Accepted relative error for APPROXIMATE, e.g. 30 means within 30%. Null otherwise.",
            )
            putJsonObject("hints") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
                put(
                    "description",
                    "Exactly 3 progressive hints, each a nudge toward the method " +
                        "and never the answer itself.",
                )
            }
            putJsonObject("solution") {
                put("type", "string")
                put(
                    "description",
                    "Worked solution showing the steps and stating the final answer.",
                )
            }
            putJsonObject("answer_label") {
                put("type", "string")
                put(
                    "description",
                    "Unit or shape of the expected answer, e.g. 'yojanas' or 'GB'. " +
                        "Empty string when unitless.",
                )
            }
        }
    }

    /** A property that is either [type] or null — the schema-safe way to say "optional". */
    private fun kotlinx.serialization.json.JsonObjectBuilder.nullableOf(
        name: String,
        type: String,
        description: String,
    ) {
        putJsonObject(name) {
            putJsonArray("anyOf") {
                add(buildJsonObject { put("type", type) })
                add(buildJsonObject { put("type", "null") })
            }
            put("description", description)
        }
    }
}
