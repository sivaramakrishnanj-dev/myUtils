package dev.sivarj.assistant

import android.app.Application
import dev.sivarj.assistant.ai.EnrichmentService
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.settings.AppSettings

class AssistantApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val enrichmentService: EnrichmentService by lazy { EnrichmentService(this) }
}
