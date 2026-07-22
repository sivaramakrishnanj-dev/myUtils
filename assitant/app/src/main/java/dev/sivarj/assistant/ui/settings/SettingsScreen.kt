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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.AwsConfig
import dev.sivarj.assistant.settings.LlmModel
import dev.sivarj.assistant.settings.LlmProviderKind
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
    val config = settings.awsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AwsConfig())

    fun save(config: AwsConfig) {
        viewModelScope.launch { settings.save(config) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = appSettingsViewModel()) {
    val config by vm.config.collectAsState()

    // Provider
    var provider by remember(config) { mutableStateOf(config.provider) }

    // Bedrock creds
    var accessKey by remember(config) { mutableStateOf(config.accessKey) }
    var secretKey by remember(config) { mutableStateOf(config.secretKey) }
    var region by remember(config) { mutableStateOf(config.region) }
    var bedrockModelId by remember(config) { mutableStateOf(config.bedrockModelId) }

    // Anthropic creds
    var anthropicApiKey by remember(config) { mutableStateOf(config.anthropicApiKey) }
    var anthropicModelId by remember(config) { mutableStateOf(config.anthropicModelId) }

    // Shared
    var customModels by remember(config) { mutableStateOf(config.customModels) }
    var promptTodo by remember(config) { mutableStateOf(config.promptTodo) }
    var promptJournal by remember(config) { mutableStateOf(config.promptJournal) }
    var promptIdea by remember(config) { mutableStateOf(config.promptIdea) }
    var promptAppointment by remember(config) { mutableStateOf(config.promptAppointment) }

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

    // Provider-scoped model list and current selection
    val providerModels = config.modelsFor(provider) + customModels.filter { it.provider == provider }
    val activeModelId = if (provider == LlmProviderKind.BEDROCK) bedrockModelId else anthropicModelId
    val modelLabel = providerModels.find { it.id == activeModelId }?.name ?: activeModelId

    fun saveAll() {
        vm.save(
            AwsConfig(
                provider = provider,
                accessKey = accessKey.trim(),
                secretKey = secretKey.trim(),
                region = region.trim().ifBlank { "us-east-1" },
                bedrockModelId = bedrockModelId,
                anthropicApiKey = anthropicApiKey.trim(),
                anthropicModelId = anthropicModelId,
                customModels = customModels,
                promptTodo = promptTodo,
                promptJournal = promptJournal,
                promptIdea = promptIdea,
                promptAppointment = promptAppointment,
            )
        )
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
            // --- Provider Picker ---
            Text("LLM Provider", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LlmProviderKind.entries.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = provider == kind,
                        onClick = { provider = kind },
                        shape = SegmentedButtonDefaults.itemShape(index, LlmProviderKind.entries.size),
                    ) { Text(kind.displayName) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Provider Credentials ---
            when (provider) {
                LlmProviderKind.BEDROCK -> {
                    Text("AWS Credentials", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = accessKey, onValueChange = { accessKey = it },
                        label = { Text("Access Key ID") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = secretKey, onValueChange = { secretKey = it },
                        label = { Text("Secret Access Key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = region, onValueChange = { region = it },
                        label = { Text("Region") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LlmProviderKind.ANTHROPIC -> {
                    Text("Anthropic Credentials", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = anthropicApiKey, onValueChange = { anthropicApiKey = it },
                        label = { Text("API Key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Get your key at console.anthropic.com → API keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                    providerModels.forEach { model ->
                        DropdownMenuItem(text = { Text(model.name) }, onClick = {
                            if (provider == LlmProviderKind.BEDROCK) bedrockModelId = model.id
                            else anthropicModelId = model.id
                            modelExpanded = false
                        })
                    }
                }
            }

            // Custom models for the active provider
            val providerCustom = customModels.filter { it.provider == provider }
            if (providerCustom.isNotEmpty()) {
                Text("Custom models", style = MaterialTheme.typography.labelMedium)
                providerCustom.forEach { model ->
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
            Text(
                "System prompts sent to the LLM when enriching content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(value = promptTodo, onValueChange = { promptTodo = it },
                label = { Text("Todo prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptJournal, onValueChange = { promptJournal = it },
                label = { Text("Journal prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptIdea, onValueChange = { promptIdea = it },
                label = { Text("Idea prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = promptAppointment, onValueChange = { promptAppointment = it },
                label = { Text("Appointment extraction prompt") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Backup ---
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            Text(
                "Export creates a zip of all your data. Use \"Share\" to send to Google Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

    // --- Add Model Dialog ---
    if (showAddModel) {
        var newModelId by remember { mutableStateOf("") }
        var newModelName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddModel = false },
            title = { Text("Add model (${provider.displayName})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newModelId, onValueChange = { newModelId = it },
                        label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(if (provider == LlmProviderKind.ANTHROPIC) "e.g. claude-opus-4-8" else "e.g. us.anthropic.claude-…")
                        },
                    )
                    OutlinedTextField(value = newModelName, onValueChange = { newModelName = it },
                        label = { Text("Friendly name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newModelId.isNotBlank() && newModelName.isNotBlank(),
                    onClick = {
                        customModels = customModels + LlmModel(newModelId.trim(), newModelName.trim(), provider)
                        showAddModel = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddModel = false }) { Text("Cancel") } },
        )
    }
}
