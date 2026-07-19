package dev.sivarj.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sivarj.assistant.settings.AppSettings
import dev.sivarj.assistant.settings.AwsConfig
import dev.sivarj.assistant.sync.SyncScheduler
import dev.sivarj.assistant.ui.appSettingsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val AVAILABLE_MODELS = listOf(
    "us.anthropic.claude-sonnet-4-20250514-v1:0" to "Claude Sonnet 4",
    "us.anthropic.claude-haiku-4-5-20251001-v1:0" to "Claude Haiku 4.5",
    "anthropic.claude-3-haiku-20240307-v1:0" to "Claude 3 Haiku",
    "anthropic.claude-3-5-sonnet-20241022-v2:0" to "Claude 3.5 Sonnet v2",
)

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
    var modelExpanded by remember { mutableStateOf(false) }
    val modelLabel = AVAILABLE_MODELS.find { it.first == modelId }?.second ?: modelId
    val context = LocalContext.current

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
                    AVAILABLE_MODELS.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                modelId = id
                                modelExpanded = false
                            },
                        )
                    }
                }
            }

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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    vm.save(
                        AwsConfig(
                            accessKey = accessKey.trim(),
                            secretKey = secretKey.trim(),
                            region = region.trim().ifBlank { "us-east-1" },
                            bedrockModelId = modelId,
                            s3Bucket = s3Bucket.trim(),
                        )
                    )
                }) { Text("Save") }
            }
        }
    }
}
