package dev.sivarj.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.ui.settings.SettingsViewModel

class DbViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val ctor = modelClass.getConstructor(AppDatabase::class.java)
        return ctor.newInstance(db) as T
    }
}

class SettingsViewModelFactory(private val settings: AppSettings) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val ctor = modelClass.getConstructor(AppSettings::class.java)
        return ctor.newInstance(settings) as T
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val app = LocalContext.current.applicationContext as AssistantApp
    return viewModel(factory = DbViewModelFactory(app.database))
}

@Composable
fun appSettingsViewModel(): SettingsViewModel {
    val app = LocalContext.current.applicationContext as AssistantApp
    return viewModel(factory = SettingsViewModelFactory(app.settings))
}
