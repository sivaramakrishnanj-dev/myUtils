package dev.sivarj.assistant.ui.settings

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.AwsConfig
import dev.sivarj.assistant.settings.BedrockModel
import dev.sivarj.assistant.sync.BackupManager
import dev.sivarj.assistant.sync.SyncScheduler
import kotlinx.coroutines.launch
import dev.sivarj.assistant.ui.appSettingsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    var accessKey by remember(config) { mutableStateOf(config.accessKey) }
    var secretKey by remember(config) { mutableStateOf(config.secretKey) }
    var region by remember(config) { mutableStateOf(config.region) }
    var modelId by remember(config) { mutableStateOf(config.bedrockModelId) }
    var s3Bucket by remember(config) { mutableStateOf(config.s3Bucket) }
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
    val backupScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context, app.database) }

    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                backupManager.exportToUri(uri).fold(
                    onSuccess = { backupStatus = "Exported $it items" },
                    onFailure = { backupStatus = "Export failed: ${it.message}" },
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupScope.launch {
                backupManager.importFromUri(uri).fold(
                    onSuccess = { backupStatus = "Imported $it items" },
                    onFailure = { backupStatus = "Import failed: ${it.message}" },
                )
            }
        }
    }

    val allModels = config.allModels
    val modelLabel = allModels.find { it.id == modelId }?.name ?: modelId

    fun saveAll() {
        vm.save(
            AwsConfig(
                accessKey = accessKey.trim(),
                secretKey = secretKey.trim(),
                region = region.trim().ifBlank { "us-east-1" },
                bedrockModelId = modelId,
                s3Bucket = s3Bucket.trim(),
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
            // --- AWS Credentials ---
            Text("AWS Credentials", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = accessKey,
                onValueChange = { accessKey = it },
                label = { Text("Access Key ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secretKey,
                onValueChange = { secretKey = it },
                label = { Text("Secret Access Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Region") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Bedrock Model ---
            Text("Bedrock Model", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                OutlinedTextField(
                    value = modelLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    allModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                modelId = model.id
                                modelExpanded = false
                            },
                        )
                    }
                }
            }

            // Custom models list
            if (customModels.isNotEmpty()) {
                Text("Custom models", style = MaterialTheme.typography.labelMedium)
                customModels.forEach { model ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                "These are the system prompts sent to the LLM when enriching content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = promptTodo,
                onValueChange = { promptTodo = it },
                label = { Text("Todo prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = promptJournal,
                onValueChange = { promptJournal = it },
                label = { Text("Journal prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = promptIdea,
                onValueChange = { promptIdea = it },
                label = { Text("Idea prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = promptAppointment,
                onValueChange = { promptAppointment = it },
                label = { Text("Appointment extraction prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- Backup (zip + system picker → Google Drive) ---
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            Text(
                "Export a zip of all your data, or import one to restore. " +
                    "The system file picker lets you save directly to Google Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { exportLauncher.launch("assistant-backup.zip") }) {
                    Text("Export backup")
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Text("Import backup")
                }
            }
            if (backupStatus.isNotBlank()) {
                Text(backupStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // --- S3 Sync ---
            Text("S3 Sync", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = s3Bucket,
                onValueChange = { s3Bucket = it },
                label = { Text("Bucket name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { SyncScheduler.syncNow(context) },
                enabled = s3Bucket.isNotBlank() && accessKey.isNotBlank(),
            ) { Text("Sync now") }

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
            title = { Text("Add model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newModelId,
                        onValueChange = { newModelId = it },
                        label = { Text("Model ID") },
                        placeholder = { Text("e.g. us.anthropic.claude-…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newModelName,
                        onValueChange = { newModelName = it },
                        label = { Text("Friendly name") },
                        placeholder = { Text("e.g. Claude Opus 4.8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newModelId.isNotBlank() && newModelName.isNotBlank(),
                    onClick = {
                        customModels = customModels + BedrockModel(newModelId.trim(), newModelName.trim())
                        showAddModel = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddModel = false }) { Text("Cancel") } },
        )
    }
}
