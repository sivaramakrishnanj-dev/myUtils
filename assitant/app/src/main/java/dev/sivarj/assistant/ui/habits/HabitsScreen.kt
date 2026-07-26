package dev.sivarj.assistant.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Habit
import dev.sivarj.assistant.data.HabitCheckin
import dev.sivarj.assistant.domain.StreakResult
import dev.sivarj.assistant.domain.computeStreak
import dev.sivarj.assistant.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitWithStreak(
    val habit: Habit,
    val streak: StreakResult,
    /** All checked epoch-days for this habit, for the detail view. */
    val checkinDays: Set<Long>,
)

class HabitsViewModel(private val db: AppDatabase) : ViewModel() {

    val habitsWithStreaks = combine(
        db.habitDao().observeAll(),
        db.habitDao().observeAllCheckins(),
    ) { habits, checkins ->
        val today = LocalDate.now().toEpochDay()
        val byHabit = checkins.groupBy { it.habitId }
        habits.map { habit ->
            val days = byHabit[habit.id]?.map { it.epochDay } ?: emptyList()
            HabitWithStreak(habit, computeStreak(days, today), days.toSet())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addHabit(name: String) {
        viewModelScope.launch { db.habitDao().upsert(Habit(name = name)) }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch { db.habitDao().softDelete(habit.id) }
    }

    fun toggleToday(item: HabitWithStreak) = toggleDay(item, LocalDate.now().toEpochDay())

    /** Toggles a check-in on any day (used by the detail sheet for backfill). */
    fun toggleDay(item: HabitWithStreak, epochDay: Long) {
        if (epochDay > LocalDate.now().toEpochDay()) return // never the future
        viewModelScope.launch {
            if (epochDay in item.checkinDays) {
                db.habitDao().deleteCheckin(item.habit.id, epochDay)
            } else {
                db.habitDao().insertCheckin(HabitCheckin(habitId = item.habit.id, epochDay = epochDay))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(vm: HabitsViewModel = appViewModel()) {
    val items by vm.habitsWithStreaks.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var detailHabitId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Habits") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "New habit")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No habits yet. Tap + to start one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.habit.id }) { item ->
                    HabitRow(
                        item = item,
                        onToggleToday = { vm.toggleToday(item) },
                        onDelete = { vm.deleteHabit(item.habit) },
                        onOpen = { detailHabitId = item.habit.id },
                    )
                }
            }
        }
    }

    // Detail sheet — resolves the live item each recomposition so grid taps
    // update immediately as the DB flow re-emits.
    val detailItem = items.find { it.habit.id == detailHabitId }
    if (detailItem != null) {
        ModalBottomSheet(onDismissRequest = { detailHabitId = null }) {
            HabitDetailContent(
                item = detailItem,
                onToggleDay = { day -> vm.toggleDay(detailItem, day) },
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New habit") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.addHabit(name.trim())
                        showAdd = false
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HabitRow(
    item: HabitWithStreak,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.habit.name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (item.streak.current > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    Text(
                        "${item.streak.current} day streak · best ${item.streak.longest}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
            }
            if (item.streak.checkedToday) {
                FilledIconButton(onClick = onToggleToday) {
                    Icon(Icons.Default.Check, contentDescription = "Checked in today — tap to undo")
                }
            } else {
                OutlinedIconButton(onClick = onToggleToday) {
                    Icon(Icons.Default.Check, contentDescription = "Check in for today")
                }
            }
        }
    }
}
