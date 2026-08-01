package dev.sivarj.braingame.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sivarj.braingame.data.ActivePuzzle
import dev.sivarj.braingame.domain.AnswerKind
import dev.sivarj.braingame.domain.formatDelta

@Composable
fun PlayScreen(
    viewModel: PlayViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val active by viewModel.activePuzzle.collectAsStateWithLifecycle()

    // Restore draft input whenever a different puzzle becomes active.
    LaunchedEffect(active?.id) {
        active?.let { viewModel.hydrateFrom(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val outcome = ui.outcome
        when {
            outcome != null -> OutcomeCard(
                outcome = outcome,
                explanation = ui.explanation,
                explaining = ui.explaining,
                onExplain = viewModel::explainCurrent,
                onNext = {
                    viewModel.dismissOutcome()
                    viewModel.newPuzzle()
                },
            )

            ui.generating -> GeneratingCard()

            active != null -> PuzzleCard(active = active!!, viewModel = viewModel, ui = ui)

            else -> EmptyState(
                error = ui.error,
                onNewPuzzle = viewModel::newPuzzle,
                onOpenSettings = onOpenSettings,
            )
        }

        // An error alongside a live puzzle (e.g. a failed explanation) shouldn't
        // replace the puzzle, so it is surfaced beneath it instead.
        if (ui.error != null && (active != null || outcome != null)) {
            Spacer(Modifier.height(12.dp))
            ErrorNotice(message = ui.error!!, onDismiss = viewModel::clearError)
        }
    }
}

@Composable
private fun GeneratingCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("Writing you a puzzle…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "The model is thinking one up and checking its own answer. " +
                    "This usually takes 10–30 seconds.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(error: String?, onNewPuzzle: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bored?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Train instead. One puzzle at a time, pitched just above your current level.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNewPuzzle) { Text("New puzzle") }

        if (error != null) {
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (error.contains("API key", ignoreCase = true)) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleCard(active: ActivePuzzle, viewModel: PlayViewModel, ui: PlayUiState) {
    val puzzle = active.puzzle

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = {}, label = { Text(puzzle.skill.displayName) })
        AssistChip(onClick = {}, label = { Text(puzzle.theme) })
    }

    Spacer(Modifier.height(16.dp))

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(puzzle.question, style = MaterialTheme.typography.bodyLarge)
        }
    }

    Spacer(Modifier.height(20.dp))

    when (puzzle.answerKind) {
        AnswerKind.INTEGER, AnswerKind.APPROXIMATE ->
            NumberInput(viewModel = viewModel, label = puzzle.answerLabel, kind = puzzle.answerKind, puzzle = puzzle)

        AnswerKind.MULTIPLE_CHOICE -> ChoiceInput(viewModel = viewModel, options = puzzle.options)

        AnswerKind.ORDERING -> OrderingInput(viewModel = viewModel, options = puzzle.options)
    }

    ui.wrongAnswerNotice?.let { notice ->
        Spacer(Modifier.height(12.dp))
        Text(
            notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(20.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = viewModel::submit, modifier = Modifier.weight(1f)) { Text("Submit") }
        if (active.hintsUsed < puzzle.hints.size) {
            OutlinedButton(onClick = viewModel::revealHint) {
                Text(if (active.hintsUsed == 0) "Hint" else "Next hint")
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = viewModel::giveUp) { Text("Show me how") }

    // Hints stay visible once revealed — re-reading them shouldn't cost another hint.
    if (active.hintsUsed > 0) {
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                puzzle.hints.take(active.hintsUsed).forEachIndexed { index, hint ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Text(
                        "Hint ${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (active.wrongAnswers.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Tried: ${active.wrongAnswers.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberInput(
    viewModel: PlayViewModel,
    label: String,
    kind: AnswerKind,
    puzzle: dev.sivarj.braingame.domain.Puzzle,
) {
    val value by viewModel.draftAnswer.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = value,
        onValueChange = viewModel::onAnswerChanged,
        label = { Text(if (label.isBlank()) "Your answer" else "Your answer ($label)") },
        keyboardOptions = KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        supportingText = if (kind == AnswerKind.APPROXIMATE) {
            {
                Text(
                    "An estimate is fine — within ${puzzle.tolerancePercent?.toInt() ?: 0}% counts.",
                )
            }
        } else null,
    )
}

@Composable
private fun ChoiceInput(viewModel: PlayViewModel, options: List<String>) {
    val selected by viewModel.draftAnswer.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = selected == index.toString()
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { viewModel.onAnswerChanged(index.toString()) },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { viewModel.onAnswerChanged(index.toString()) },
                )
                Spacer(Modifier.width(8.dp))
                Text(option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun OrderingInput(viewModel: PlayViewModel, options: List<String>) {
    val order by viewModel.workingOrder.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Arrange in the correct order",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        order.forEachIndexed { position, optionIndex ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${position + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        options.getOrElse(optionIndex) { "?" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.moveItem(position, position - 1) },
                        enabled = position > 0,
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(
                        onClick = { viewModel.moveItem(position, position + 1) },
                        enabled = position < order.lastIndex,
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeCard(
    outcome: dev.sivarj.braingame.data.PuzzleOutcome,
    explanation: String?,
    explaining: Boolean,
    onExplain: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (outcome.solved) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (outcome.solved) "Solved" else "Not this time",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${outcome.puzzle.skill.displayName} " +
                        formatDelta(outcome.ratingBefore, outcome.ratingAfter),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (outcome.ratingAfter >= outcome.ratingBefore) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                "Rating ${outcome.ratingBefore} → ${outcome.ratingAfter}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("How it works out", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(outcome.puzzle.solution, style = MaterialTheme.typography.bodyMedium)

            if (explanation != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Where it went wrong",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onNext) { Text("Next puzzle") }
                if (explanation == null) {
                    if (explaining) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        OutlinedButton(onClick = onExplain) { Text("Explain my mistake") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
