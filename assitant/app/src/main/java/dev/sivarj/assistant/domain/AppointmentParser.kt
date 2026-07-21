package dev.sivarj.assistant.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class AppointmentAiResponse(
    val title: String = "",
    val notes: String = "",
    val startHour: Int = 9,
    val startMin: Int = 0,
    val endHour: Int = 10,
    val endMin: Int = 0,
)

data class ParsedAppointment(
    val title: String,
    val notes: String,
    val startMinutes: Int,
    val endMinutes: Int,
)

/**
 * Parses the AI response (expected to be a JSON object) into an appointment.
 * Tolerant of surrounding prose — extracts the first `{…}` substring and
 * attempts JSON deserialization on that.
 */
fun parseAppointmentJson(raw: String): ParsedAppointment? {
    val jsonStr = extractJsonObject(raw) ?: return null
    return try {
        val r = json.decodeFromString<AppointmentAiResponse>(jsonStr)
        if (r.title.isBlank()) return null
        ParsedAppointment(
            title = r.title.trim(),
            notes = r.notes.trim(),
            startMinutes = (r.startHour.coerceIn(0, 23) * 60 + r.startMin.coerceIn(0, 59)),
            endMinutes = (r.endHour.coerceIn(0, 23) * 60 + r.endMin.coerceIn(0, 59)),
        )
    } catch (_: Exception) {
        null
    }
}

private fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    if (start == -1) return null
    var depth = 0
    for (i in start until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}
