package dev.sivarj.braingame

import android.app.Application
import dev.sivarj.braingame.data.GameRepository
import dev.sivarj.braingame.settings.AppSettings

/** Manual DI, matching the assistant app: lazy singletons hung off the Application. */
class BrainGameApp : Application() {
    val repository: GameRepository by lazy { GameRepository(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
}
