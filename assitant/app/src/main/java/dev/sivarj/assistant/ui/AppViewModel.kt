package dev.sivarj.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.data.AppDatabase

/**
 * All screen view models take the database as their sole constructor arg;
 * this factory wires them to the singleton owned by [AssistantApp].
 */
class DbViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val ctor = modelClass.getConstructor(AppDatabase::class.java)
        return ctor.newInstance(db) as T
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val app = LocalContext.current.applicationContext as AssistantApp
    return viewModel(factory = DbViewModelFactory(app.database))
}
