package dev.sivarj.assistant.ai

import android.content.Context
import dev.sivarj.assistant.settings.AppSettings
import kotlinx.coroutines.flow.first

/**
 * Application-scoped service that reads the latest AWS config and calls Bedrock.
 * Each call reads the config fresh so changes in Settings take effect immediately.
 */
class EnrichmentService(private val context: Context) {

    private val settings = AppSettings(context)

    suspend fun enrich(rawText: String, type: ContentType): EnrichResult {
        val config = settings.awsConfig.first()
        return BedrockEnricher(config).enrich(rawText, type)
    }
}
