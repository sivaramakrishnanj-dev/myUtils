package dev.sivarj.braingame.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sivarj.braingame.BrainGameApp
import dev.sivarj.braingame.settings.AppConfig
import dev.sivarj.braingame.ui.play.PlayScreen
import dev.sivarj.braingame.ui.play.PlayViewModel
import dev.sivarj.braingame.ui.settings.SettingsScreen
import dev.sivarj.braingame.ui.stats.StatsScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: String) {
    PLAY("Play"),
    STATS("Stats"),
    SETTINGS("Settings"),
}

@Composable
fun BrainGameRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as BrainGameApp

    val viewModel: PlayViewModel = viewModel(
        factory = PlayViewModel.Factory(app.repository),
    )
    val config by app.settings.config.collectAsState(initial = AppConfig())

    var tab by remember { mutableStateOf(Tab.PLAY) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                when (entry) {
                                    Tab.PLAY -> Icons.Default.Extension
                                    Tab.STATS -> Icons.Default.BarChart
                                    Tab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.PLAY -> PlayScreen(
                    viewModel = viewModel,
                    onOpenSettings = { tab = Tab.SETTINGS },
                )

                Tab.STATS -> StatsScreen(viewModel = viewModel)

                Tab.SETTINGS -> SettingsScreen(
                    config = config,
                    onSave = { updated ->
                        scope.launch {
                            app.settings.save(updated)
                            snackbarHost.showSnackbar(
                                message = "Settings saved",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                )
            }
        }
    }
}
