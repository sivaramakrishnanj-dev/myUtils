package dev.sivarj.assistant.ai

import android.content.Context
import dev.sivarj.assistant.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Application-scoped service that reads the latest AWS config and calls Bedrock.
 * Each call reads the config fresh so changes in Settings take effect immediately.
 */
class EnrichmentService(private val context: Context) {

    private val settings = AppSettings(context)

    /**
     * Runs on Dispatchers.IO: the Bedrock client's close() tears down TLS
     * sockets synchronously, which throws NetworkOnMainThreadException if the
     * caller's coroutine is on the main dispatcher (as Compose UI scopes are).
     */
    suspend fun enrich(rawText: String, type: ContentType): EnrichResult =
        withContext(Dispatchers.IO) {
            val config = settings.awsConfig.first()
            BedrockEnricher(config).enrich(rawText, type)
        }
}
