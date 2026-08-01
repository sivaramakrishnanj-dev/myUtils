package dev.sivarj.assistant.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class NoteAiResponse(val title: String = "", val body: String = "")

data class PolishedNote(val title: String, val body: String)

/**
 * Parses the note-polish response, which should be {"title":…,"body":…}.
 * Falls back to treating the whole response as the body (with no title) when
 * the model returns prose instead of JSON, so enrichment never dead-ends.
 */
fun parseNoteJson(raw: String): PolishedNote {
    val obj = extractJsonObject(raw)
    if (obj != null) {
        runCatching { json.decodeFromString<NoteAiResponse>(obj) }.getOrNull()?.let { r ->
            if (r.body.isNotBlank()) {
                return PolishedNote(title = r.title.trim(), body = r.body.trim())
            }
        }
    }
    return PolishedNote(title = "", body = raw.trim())
}

private fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    if (start == -1) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            inString -> {}
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}
