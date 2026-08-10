package dev.sivarj.assistant.ai

import android.content.Context
import dev.sivarj.assistant.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class EnrichmentService(private val context: Context) {

    private val settings = AppSettings(context)

    suspend fun enrich(rawText: String, type: ContentType): EnrichResult =
        withContext(Dispatchers.IO) {
            val config = settings.config.first()
            val provider = AnthropicProvider(config)
            if (!provider.isConfigured) {
                return@withContext EnrichResult.Failure("API key not configured — go to Settings")
            }
            val systemPrompt = when (type) {
                ContentType.TODO -> config.promptTodo
                ContentType.JOURNAL -> config.promptJournal
                ContentType.IDEA -> config.promptIdea
                ContentType.APPOINTMENT -> config.promptAppointment
            }
            provider.complete(systemPrompt, rawText)
        }

    /** Habit motivation: sends real streak stats with the editable coach prompt. */
    suspend fun motivate(statsText: String): EnrichResult =
        withContext(Dispatchers.IO) {
            val config = settings.config.first()
            val provider = AnthropicProvider(config)
            if (!provider.isConfigured) {
                return@withContext EnrichResult.Failure("API key not configured — go to Settings")
            }
            provider.complete(config.promptMotivation, statsText)
        }

    /**
     * Generates a prayer/reflection. [situation] may be blank for an
     * unprompted one.
     */
    suspend fun pray(situation: String): EnrichResult =
        withContext(Dispatchers.IO) {
            val config = settings.config.first()
            val provider = AnthropicProvider(config)
            if (!provider.isConfigured) {
                return@withContext EnrichResult.Failure("API key not configured — go to Settings")
            }
            val userText = if (situation.isBlank()) {
                "Please give me a prayer for trust and surrender right now."
            } else {
                "This is what I am carrying right now:\n\n$situation"
            }
            provider.complete(config.promptPrayer, userText)
        }

    /**
     * Proposes a short title for already-polished note text. Kept as its own
     * call with a fixed prompt so titles work no matter how the user has
     * customized their note-polish prompt.
     */
    suspend fun suggestTitle(noteText: String): String? =
        withContext(Dispatchers.IO) {
            if (noteText.isBlank()) return@withContext null
            val config = settings.config.first()
            val provider = AnthropicProvider(config)
            if (!provider.isConfigured) return@withContext null
            val result = provider.complete(TITLE_PROMPT, noteText.take(4000))
            when (result) {
                is EnrichResult.Success -> result.text
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    // Models sometimes wrap or prefix the answer despite instructions.
                    ?.removeSurrounding("\"")
                    ?.removePrefix("Title:")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it.length <= 80 }
                is EnrichResult.Failure -> null
            }
        }

    private companion object {
        const val TITLE_PROMPT =
            "Give a short title (3-8 words) for the note below. " +
                "Reply with the title only — no quotes, no punctuation at the end, " +
                "no preamble, no explanation."
    }
}
