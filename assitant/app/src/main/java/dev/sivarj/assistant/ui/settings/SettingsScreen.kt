package dev.sivarj.assistant.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.settings.AppConfig
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.LlmModel
import dev.sivarj.assistant.settings.VoiceEngine
import dev.sivarj.assistant.speech.WHISPER_MODELS
import dev.sivarj.assistant.speech.WhisperModelManager
import dev.sivarj.assistant.sync.BackupManager
import dev.sivarj.assistant.ui.appSettingsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private suspend fun shareBackup(
    context: Context,
    backupManager: BackupManager,
    onStatus: (String) -> Unit,
) {
    val cacheFile = java.io.File(context.cacheDir, "assistant-backup.zip")
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", cacheFile,
    )
    backupManager.exportToUri(uri).fold(
        onSuccess = { count ->
            onStatus("Exported $count items — opening share…")
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share backup"))
        },
        onFailure = { onStatus("Export failed: ${it.message}") },
    )
}

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val config = settings.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppConfig())

    fun save(config: AppConfig) {
        viewModelScope.launch { settings.save(config) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = appSettingsViewModel()) {
    val config by vm.config.collectAsState()

    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var modelId by remember(config) { mutableStateOf(config.modelId) }
    var customModels by remember(config) { mutableStateOf(config.customModels) }
    var promptTodo by remember(config) { mutableStateOf(config.promptTodo) }
    var promptJournal by remember(config) { mutableStateOf(config.promptJournal) }
    var promptIdea by remember(config) { mutableStateOf(config.promptIdea) }
    var promptAppointment by remember(config) { mutableStateOf(config.promptAppointment) }
    var promptMotivation by remember(config) { mutableStateOf(config.promptMotivation) }
    var promptPrayer by remember(config) { mutableStateOf(config.promptPrayer) }
    var voiceEngine by remember(config) { mutableStateOf(config.voiceEngine) }
    var whisperModelFile by remember(config) { mutableStateOf(config.whisperModelFile) }

    var modelExpanded by remember { mutableStateOf(false) }
    var showAddModel by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf("") }
    val context = LocalContext.current
    val app = context.applicationContext as dev.sivarj.assistant.AssistantApp
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context, app.database) }

    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            backupManager.exportToUri(uri).fold(
                onSuccess = { backupStatus = "Exported $it items" },
                onFailure = { backupStatus = "Export failed: ${it.message}" },
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            backupManager.importFromUri(uri).fold(
                onSuccess = { backupStatus = "Imported $it items" },
                onFailure = { backupStatus = "Import failed: ${it.message}" },
            )
        }
    }

    val allModels = config.allModels
    val modelLabel = allModels.find { it.id == modelId }?.name ?: modelId

    fun saveAll() {
        vm.save(AppConfig(
            apiKey = apiKey.trim(),
            modelId = modelId,
            customModels = customModels,
            promptTodo = promptTodo,
            promptJournal = promptJournal,
            promptIdea = promptIdea,
            promptAppointment = promptAppointment,
            promptMotivation = promptMotivation,
            promptPrayer = promptPrayer,
            voiceEngine = voiceEngine,
            whisperModelFile = whisperModelFile,
        ))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- API Key ---
            Text("Anthropic API", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Get your key at console.anthropic.com → API keys",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Model ---
            Text("Model", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                OutlinedTextField(
                    value = modelLabel, onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    allModels.forEach { model ->
                        DropdownMenuItem(text = { Text(model.name) }, onClick = {
                            modelId = model.id
                            modelExpanded = false
                        })
                    }
                }
            }
            if (customModels.isNotEmpty()) {
                Text("Custom models", style = MaterialTheme.typography.labelMedium)
                customModels.forEach { model ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyMedium)
                            Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { customModels = customModels - model }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            TextButton(onClick = { showAddModel = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add model")
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Enrichment Prompts ---
            Text("Enrichment Prompts", style = MaterialTheme.typography.titleSmall)
            Text("System prompts sent to the LLM when enriching content.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = promptTodo, onValueChange = { promptTodo = it },
                label = { Text("Todo prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptJournal, onValueChange = { promptJournal = it },
                label = { Text("Journal prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptPrayer, onValueChange = { promptPrayer = it },
                label = { Text("Prayer prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptAppointment, onValueChange = { promptAppointment = it },
                label = { Text("Appointment extraction prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptMotivation, onValueChange = { promptMotivation = it },
                label = { Text("Habit motivation prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Voice Engine ---
            Text("Voice Input", style = MaterialTheme.typography.titleSmall)
            VoiceEngine.entries.forEach { engine ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = voiceEngine == engine,
                        onClick = { voiceEngine = engine },
                    )
                    Text(engine.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (voiceEngine == VoiceEngine.WHISPER) {
                val whisperManager = remember { WhisperModelManager(context) }
                var downloadProgress by remember { mutableStateOf<Int?>(null) }
                var downloadingModel by remember { mutableStateOf<String?>(null) }
                var refresh by remember { mutableStateOf(0) }

                WHISPER_MODELS.forEach { model ->
                    val downloaded = remember(refresh) { whisperManager.isDownloaded(model) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = whisperModelFile == model.fileName,
                            onClick = { if (downloaded) whisperModelFile = model.fileName },
                            enabled = downloaded,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (downloadingModel == model.fileName && downloadProgress != null) {
                                Text("Downloading… $downloadProgress%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            } else if (!downloaded) {
                                Text("Not downloaded",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (downloaded) {
                            IconButton(onClick = {
                                whisperManager.delete(model)
                                if (whisperModelFile == model.fileName) whisperModelFile = ""
                                refresh++
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete model",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        } else if (downloadingModel != model.fileName) {
                            TextButton(onClick = {
                                downloadingModel = model.fileName
                                downloadProgress = 0
                                scope.launch {
                                    whisperManager.download(model) { downloadProgress = it }.fold(
                                        onSuccess = {
                                            whisperModelFile = model.fileName
                                            refresh++
                                        },
                                        onFailure = { backupStatus = "Model download failed: ${it.message}" },
                                    )
                                    downloadingModel = null
                                    downloadProgress = null
                                }
                            }) { Text("Download") }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Backup ---
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            Text("Export creates a zip of all your data. Use \"Share\" to send to Google Drive.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { shareBackup(context, backupManager) { backupStatus = it } } }) { Text("Share") }
                TextButton(onClick = { exportLauncher.launch("assistant-backup.zip") }) { Text("Save to file") }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import") }
            }
            if (backupStatus.isNotBlank()) {
                Text(backupStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            // --- Save ---
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { saveAll() }) { Text("Save") }
            }
        }
    }

    if (showAddModel) {
        var newModelId by remember { mutableStateOf("") }
        var newModelName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddModel = false },
            title = { Text("Add model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newModelId, onValueChange = { newModelId = it },
                        label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. claude-opus-4-8") })
                    OutlinedTextField(value = newModelName, onValueChange = { newModelName = it },
                        label = { Text("Friendly name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = newModelId.isNotBlank() && newModelName.isNotBlank(), onClick = {
                    customModels = customModels + LlmModel(newModelId.trim(), newModelName.trim())
                    showAddModel = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddModel = false }) { Text("Cancel") } },
        )
    }
}
