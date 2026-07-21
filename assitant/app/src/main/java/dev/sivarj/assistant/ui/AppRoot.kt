package dev.sivarj.assistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.sivarj.assistant.ui.day.DayScreen
import dev.sivarj.assistant.ui.habits.HabitsScreen
import dev.sivarj.assistant.ui.ideas.IdeasScreen
import dev.sivarj.assistant.ui.journal.JournalScreen
import dev.sivarj.assistant.ui.settings.SettingsScreen
import dev.sivarj.assistant.ui.todos.TodosScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("day", "Day", Icons.Default.CalendarToday),
    Tab("todos", "Todos", Icons.Default.Checklist),
    Tab("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook),
    Tab("ideas", "Ideas", Icons.Default.Lightbulb),
    Tab("habits", "Habits", Icons.Default.LocalFireDepartment),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AssistantAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "day",
            modifier = Modifier.padding(padding),
        ) {
            composable("day") { DayScreen() }
            composable("todos") { TodosScreen() }
            composable("journal") { JournalScreen() }
            composable("ideas") { IdeasScreen() }
            composable("habits") { HabitsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
