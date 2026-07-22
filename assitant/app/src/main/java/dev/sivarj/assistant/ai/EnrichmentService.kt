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
}
