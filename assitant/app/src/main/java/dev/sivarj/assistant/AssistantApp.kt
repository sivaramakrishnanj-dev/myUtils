package dev.sivarj.assistant

import android.app.Application
import dev.sivarj.assistant.data.AppDatabase

class AssistantApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
}
