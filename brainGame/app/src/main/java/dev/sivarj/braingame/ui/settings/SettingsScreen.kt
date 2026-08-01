package dev.sivarj.braingame.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.sivarj.braingame.ai.DefaultPrompts
import dev.sivarj.braingame.domain.Themes
import dev.sivarj.braingame.settings.AppConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local edit buffer so typing doesn't write to DataStore on every keystroke.
    var draft by remember(config) { mutableStateOf(config) }
    var showKey by remember { mutableStateOf(false) }
    var showPrompts by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { draft = draft.copy(apiKey = it) },
            label = { Text("sk-ant-…") },
            singleLine = true,
            visualTransformation = if (showKey) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showKey = !showKey }) {
            Text(if (showKey) "Hide key" else "Show key")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text("Model", style = MaterialTheme.typography.titleSmall)
        Text(
            "Opus 5 by default — this app makes only a few calls a day, so quality " +
                "matters more than per-call cost.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        draft.allModels.forEach { model ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = draft.modelId == model.id,
                    onClick = { draft = draft.copy(modelId = model.id) },
                )
                Column {
                    Text(model.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        model.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text("Themes", style = MaterialTheme.typography.titleSmall)
        Text(
            "Puzzles are dressed in these settings. Solving never requires knowing the topic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Themes.ALL.forEach { theme ->
                val selected = theme in draft.enabledThemes
                FilterChip(
                    selected = selected,
                    onClick = {
                        val next = if (selected) {
                            draft.enabledThemes - theme
                        } else {
                            draft.enabledThemes + theme
                        }
                        draft = draft.copy(enabledThemes = next)
                    },
                    label = { Text(theme) },
                )
            }
        }
        if (draft.enabledThemes.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "With none selected, all themes are used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { showPrompts = !showPrompts }) {
            Text(if (showPrompts) "Hide prompts" else "Edit prompts")
        }

        if (showPrompts) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "The generation prompt is sent with caching enabled, so keep it " +
                            "stable between puzzles for the cache to pay off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("Puzzle generation", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = draft.promptGeneration,
                onValueChange = { draft = draft.copy(promptGeneration = it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 16,
            )
            TextButton(onClick = {
                draft = draft.copy(promptGeneration = DefaultPrompts.GENERATION)
            }) { Text("Reset to default") }

            Spacer(Modifier.height(12.dp))
            Text("Explanation", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = draft.promptExplanation,
                onValueChange = { draft = draft.copy(promptExplanation = it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 16,
            )
            TextButton(onClick = {
                draft = draft.copy(promptExplanation = DefaultPrompts.EXPLANATION)
            }) { Text("Reset to default") }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        Spacer(Modifier.height(32.dp))
    }
}
