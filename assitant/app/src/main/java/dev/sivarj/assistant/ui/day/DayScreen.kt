package dev.sivarj.assistant.ui.day

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.ai.ContentType
import dev.sivarj.assistant.ai.EnrichResult
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Appointment
import dev.sivarj.assistant.domain.FreeSlot
import dev.sivarj.assistant.domain.computeFreeTime
import dev.sivarj.assistant.domain.formatDuration
import dev.sivarj.assistant.domain.parseAppointmentJson
import dev.sivarj.assistant.ui.appViewModel
import dev.sivarj.assistant.ui.components.DictationField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DayViewModel(private val db: AppDatabase) : ViewModel() {
    private val today = LocalDate.now().toEpochDay()

    val appointments = db.appointmentDao().observeForDay(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(appointment: Appointment) {
        viewModelScope.launch { db.appointmentDao().upsert(appointment.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun delete(appointment: Appointment) {
        viewModelScope.launch { db.appointmentDao().softDelete(appointment.id) }
    }
}

/**
 * Material 3 clock-dial time picker in a dialog. The dial uses the device's
 * 12/24h preference; in 12h mode it shows the AM/PM toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A row in the day view: either a booked appointment or an open gap. */
private sealed interface ScheduleRow {
    data class Booked(val appointment: Appointment) : ScheduleRow
    data class Free(val slot: FreeSlot) : ScheduleRow
}

/**
 * Merges appointments (sorted by start) and free slots into one chronological
 * list, keyed on start time.
 */
private fun buildScheduleRows(
    appointments: List<Appointment>,
    freeSlots: List<FreeSlot>,
): List<ScheduleRow> {
    val rows = appointments.map { ScheduleRow.Booked(it) as ScheduleRow } +
        freeSlots.map { ScheduleRow.Free(it) }
    return rows.sortedBy { row ->
        when (row) {
            is ScheduleRow.Booked -> row.appointment.startMinutes
            is ScheduleRow.Free -> row.slot.startMinutes
        }
    }
}

@Composable
private fun AppointmentCard(
    appt: Appointment,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(appt.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${minutesToDisplay(appt.startMinutes)} – ${minutesToDisplay(appt.endMinutes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (appt.notes.isNotBlank()) {
                    Text(appt.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun FreeSlotRow(slot: FreeSlot) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "◦ ${minutesToDisplay(slot.startMinutes)} – ${minutesToDisplay(slot.endMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            "  free · ${formatDuration(slot.durationMinutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun minutesToDisplay(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(h12, m, amPm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: DayViewModel = appViewModel()) {
    val appointments by vm.appointments.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    val today = LocalDate.now()

    // Re-evaluate "now" every minute so free-time numbers stay current.
    var nowMinutes by remember { mutableIntStateOf(LocalTime.now().toSecondOfDay() / 60) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinutes = LocalTime.now().toSecondOfDay() / 60
        }
    }

    val freeTime = remember(appointments, nowMinutes) {
        computeFreeTime(
            bookings = appointments.map { it.startMinutes..it.endMinutes },
            nowMinutes = nowMinutes,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Today — ${today.format(DateTimeFormatter.ofPattern("MMM d"))}")
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) { Icon(Icons.Default.Add, contentDescription = "New appointment") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Free-time summary header
            item(key = "free-summary") {
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            if (freeTime.totalFreeMinutes > 0)
                                "Free time left today: ${formatDuration(freeTime.totalFreeMinutes)}"
                            else
                                "No free time left today",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (freeTime.slots.isNotEmpty()) {
                            Text(
                                "${freeTime.slots.size} open window${if (freeTime.slots.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (appointments.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No appointments today.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                // Interleave appointments and the free gaps between them.
                buildScheduleRows(appointments, freeTime.slots).forEach { row ->
                    when (row) {
                        is ScheduleRow.Booked -> item(key = row.appointment.id) {
                            AppointmentCard(
                                appt = row.appointment,
                                onClick = {
                                    editing = row.appointment
                                    showEditor = true
                                },
                                onDelete = { vm.delete(row.appointment) },
                            )
                        }
                        is ScheduleRow.Free -> item(key = "free-${row.slot.startMinutes}") {
                            FreeSlotRow(row.slot)
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            AppointmentEditor(
                initial = editing,
                onSave = { appt ->
                    vm.save(appt)
                    showEditor = false
                },
            )
        }
    }
}

@Composable
private fun AppointmentEditor(
    initial: Appointment?,
    onSave: (Appointment) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var startHour by remember { mutableIntStateOf(initial?.let { it.startMinutes / 60 } ?: 9) }
    var startMin by remember { mutableIntStateOf(initial?.let { it.startMinutes % 60 } ?: 0) }
    var endHour by remember { mutableIntStateOf(initial?.let { it.endMinutes / 60 } ?: 10) }
    var endMin by remember { mutableIntStateOf(initial?.let { it.endMinutes % 60 } ?: 0) }
    var voiceText by remember { mutableStateOf("") }
    var extracting by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AssistantApp

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial == null) "New appointment" else "Edit appointment",
            style = MaterialTheme.typography.titleMedium,
        )

        // --- AI extraction section ---
        DictationField(
            value = voiceText,
            onValueChange = { voiceText = it },
            label = "Describe appointment (voice / text)",
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = voiceText.isNotBlank() && !extracting,
            onClick = {
                extracting = true
                extractError = null
                scope.launch {
                    when (val result = app.enrichmentService.enrich(voiceText, ContentType.APPOINTMENT)) {
                        is EnrichResult.Success -> {
                            val parsed = parseAppointmentJson(result.text)
                            if (parsed != null) {
                                title = parsed.title
                                if (parsed.notes.isNotBlank()) notes = parsed.notes
                                startHour = parsed.startMinutes / 60
                                startMin = parsed.startMinutes % 60
                                endHour = parsed.endMinutes / 60
                                endMin = parsed.endMinutes % 60
                            } else {
                                extractError = "Could not parse appointment from AI response"
                            }
                        }
                        is EnrichResult.Failure -> extractError = result.error
                    }
                    extracting = false
                }
            },
        ) {
            Text(if (extracting) "Extracting…" else "Extract with AI")
        }
        if (extractError != null) {
            Text(extractError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        // --- Manual fields ---
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 1,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Time selection via Material 3 clock dial (12h + AM/PM) ---
        var showStartPicker by remember { mutableStateOf(false) }
        var showEndPicker by remember { mutableStateOf(false) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                Text("Start: ${minutesToDisplay(startHour * 60 + startMin)}")
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                Text("End: ${minutesToDisplay(endHour * 60 + endMin)}")
            }
        }

        if (showStartPicker) {
            TimePickerDialog(
                title = "Start time",
                initialHour = startHour,
                initialMinute = startMin,
                onConfirm = { h, m ->
                    startHour = h
                    startMin = m
                    // Keep end after start: nudge end to +1h when it falls behind.
                    if (endHour * 60 + endMin <= h * 60 + m) {
                        endHour = (h + 1).coerceAtMost(23)
                        endMin = m
                    }
                    showStartPicker = false
                },
                onDismiss = { showStartPicker = false },
            )
        }
        if (showEndPicker) {
            TimePickerDialog(
                title = "End time",
                initialHour = endHour,
                initialMinute = endMin,
                onConfirm = { h, m ->
                    endHour = h
                    endMin = m
                    showEndPicker = false
                },
                onDismiss = { showEndPicker = false },
            )
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: Appointment(
                        title = title.trim(),
                        epochDay = today,
                        startMinutes = startHour * 60 + startMin,
                        endMinutes = endHour * 60 + endMin,
                    )
                    onSave(
                        base.copy(
                            title = title.trim(),
                            notes = notes.trim(),
                            epochDay = today,
                            startMinutes = startHour * 60 + startMin,
                            endMinutes = endHour * 60 + endMin,
                            rawTranscript = voiceText.ifBlank { initial?.rawTranscript },
                        )
                    )
                },
            ) { Text("Save") }
        }
    }
}
