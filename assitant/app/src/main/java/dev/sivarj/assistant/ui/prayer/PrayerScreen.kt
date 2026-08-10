package dev.sivarj.assistant.ui.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.sivarj.assistant.ai.EnrichResult
import dev.sivarj.assistant.data.AppDatabase
import dev.sivarj.assistant.data.Prayer
import dev.sivarj.assistant.ui.appViewModel
import dev.sivarj.assistant.ui.components.DictationField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class PrayerViewModel(private val db: AppDatabase) : ViewModel() {
    val prayers = db.prayerDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(prayer: Prayer) {
        viewModelScope.launch { db.prayerDao().upsert(prayer) }
    }

    fun delete(prayer: Prayer) {
        viewModelScope.launch { db.prayerDao().softDelete(prayer.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(vm: PrayerViewModel = appViewModel()) {
    val saved by vm.prayers.collectAsState()
    var situation by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<String?>(null) }
    var currentSituation by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AssistantApp
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    fun requestPrayer() {
        loading = true
        error = null
        current = null
        val asked = situation.trim()
        scope.launch {
            when (val r = app.enrichmentService.pray(asked)) {
                is EnrichResult.Success -> {
                    current = r.text
                    currentSituation = asked
                }
                is EnrichResult.Failure -> error = r.error
            }
            loading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Prayer") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "ask") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Ask for a word of trust and surrender. Describe what you are " +
                            "carrying, or simply ask.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DictationField(
                        value = situation,
                        onValueChange = { situation = it },
                        label = "What is on your heart? (optional)",
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { requestPrayer() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Praying…")
                        } else {
                            Icon(Icons.Default.SelfImprovement, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(if (situation.isBlank()) "Give me a prayer" else "Pray for this")
                        }
                    }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // The freshly generated prayer, with the option to keep it.
            if (current != null) {
                item(key = "current") {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(current!!, style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { current = null }) { Text("Dismiss") }
                                TextButton(onClick = {
                                    vm.save(
                                        Prayer(situation = currentSituation, content = current!!)
                                    )
                                    current = null
                                    situation = ""
                                }) { Text("Save") }
                            }
                        }
                    }
                }
            }

            if (saved.isNotEmpty()) {
                item(key = "saved-header") {
                    Column {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Saved prayers", style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(saved, key = { it.id }) { prayer ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        dateFormat.format(Date(prayer.createdAt)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (prayer.situation.isNotBlank()) {
                                        Text(
                                            prayer.situation,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.delete(prayer) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            Text(
                                prayer.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
