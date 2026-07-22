package dev.sivarj.assistant.ai

import android.content.Context
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.AwsConfig
import dev.sivarj.assistant.settings.LlmProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Application-scoped facade: reads the latest config, picks the active
 * [LlmProvider], and delegates the call. Runs on [Dispatchers.IO] because
 * both providers do blocking I/O.
 */
class EnrichmentService(private val context: Context) {

    private val settings = AppSettings(context)

    suspend fun enrich(rawText: String, type: ContentType): EnrichResult =
        withContext(Dispatchers.IO) {
            val config = settings.awsConfig.first()
            val provider = providerFor(config)
            if (!provider.isConfigured) {
                return@withContext EnrichResult.Failure("${provider.name}: credentials not configured")
            }
            val systemPrompt = when (type) {
                ContentType.TODO -> config.promptTodo
                ContentType.JOURNAL -> config.promptJournal
                ContentType.IDEA -> config.promptIdea
                ContentType.APPOINTMENT -> config.promptAppointment
            }
            provider.complete(systemPrompt, rawText)
        }

    private fun providerFor(config: AwsConfig): LlmProvider = when (config.provider) {
        LlmProviderKind.BEDROCK -> BedrockProvider(config)
        LlmProviderKind.ANTHROPIC -> AnthropicProvider(config)
    }
}
