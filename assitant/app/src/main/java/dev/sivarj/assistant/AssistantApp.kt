package dev.sivarj.assistant

import android.app.Application
import dev.sivarj.assistant.ai.EnrichmentService
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.speech.WhisperTranscriber

class AssistantApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val enrichmentService: EnrichmentService by lazy { EnrichmentService(this) }
    val whisperTranscriber: WhisperTranscriber by lazy { WhisperTranscriber(this) }
}
