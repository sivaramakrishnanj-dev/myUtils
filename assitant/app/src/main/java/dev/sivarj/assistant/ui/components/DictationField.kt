package dev.sivarj.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sivarj.assistant.speech.SpeechState
import dev.sivarj.assistant.speech.rememberSpeechCapture

/**
 * OutlinedTextField with a trailing mic button. Dictated text is appended to
 * the current value (with a joining space); live partials and errors are
 * surfaced under the field as supporting text.
 */
@Composable
fun DictationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val (controller, state) = rememberSpeechCapture { recognized ->
        val joined = if (value.isBlank()) recognized else "${value.trimEnd()} $recognized"
        onValueChange(joined)
    }

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (state is SpeechState.Listening || state is SpeechState.Partial) {
                    IconButton(onClick = { controller.stop() }) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop dictation",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    IconButton(onClick = { controller.start() }) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate")
                    }
                }
            },
            supportingText = when (state) {
                is SpeechState.Listening -> ({ Text("Listening…") })
                is SpeechState.Partial -> ({ Text("“${state.text}”") })
                is SpeechState.Error -> ({
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                })
                else -> null
            },
        )
    }
}
