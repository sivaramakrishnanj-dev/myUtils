package dev.sivarj.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.settings.VoiceEngine
import dev.sivarj.assistant.speech.SpeechState
import dev.sivarj.assistant.speech.WhisperState
import dev.sivarj.assistant.speech.rememberSpeechCapture
import dev.sivarj.assistant.speech.rememberWhisperCapture
import kotlinx.coroutines.flow.map

/**
 * OutlinedTextField with a trailing mic button. Dictated text is appended to
 * the current value. The voice engine (live Android recognition vs whisper
 * record-then-transcribe) follows the Settings choice.
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
    val app = LocalContext.current.applicationContext as AssistantApp
    val engine by remember {
        app.settings.config.map { it.voiceEngine }
    }.collectAsState(initial = VoiceEngine.SYSTEM)

    val appendResult: (String) -> Unit = { recognized ->
        val joined = if (value.isBlank()) recognized else "${value.trimEnd()} $recognized"
        onValueChange(joined)
    }

    val (systemController, systemState) = rememberSpeechCapture(appendResult)
    val (whisperController, whisperState) = rememberWhisperCapture(appendResult)

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
                when (engine) {
                    VoiceEngine.SYSTEM -> SystemMicIcon(systemController, systemState)
                    VoiceEngine.WHISPER -> WhisperMicIcon(whisperController, whisperState)
                }
            },
            supportingText = when (engine) {
                VoiceEngine.SYSTEM -> systemSupportingText(systemState)
                VoiceEngine.WHISPER -> whisperSupportingText(whisperState)
            },
        )
    }
}

@Composable
private fun SystemMicIcon(
    controller: dev.sivarj.assistant.speech.SpeechCaptureController,
    state: SpeechState,
) {
    if (state is SpeechState.Listening || state is SpeechState.Partial) {
        IconButton(onClick = { controller.stop() }) {
            Icon(Icons.Default.Stop, contentDescription = "Stop dictation", tint = MaterialTheme.colorScheme.error)
        }
    } else {
        IconButton(onClick = { controller.start() }) {
            Icon(Icons.Default.Mic, contentDescription = "Dictate")
        }
    }
}

private fun systemSupportingText(state: SpeechState): (@Composable () -> Unit)? = when (state) {
    is SpeechState.Listening -> ({ Text("Listening…") })
    is SpeechState.Partial -> ({ Text("“${state.text}”") })
    is SpeechState.Error -> ({ Text(state.message, color = MaterialTheme.colorScheme.error) })
    else -> null
}

@Composable
private fun WhisperMicIcon(
    controller: dev.sivarj.assistant.speech.WhisperCaptureController,
    state: WhisperState,
) {
    when (state) {
        is WhisperState.Recording -> IconButton(onClick = { controller.stop() }) {
            Icon(Icons.Default.Stop, contentDescription = "Stop and transcribe", tint = MaterialTheme.colorScheme.error)
        }
        is WhisperState.Transcribing -> CircularProgressIndicator(
            Modifier.size(24.dp), strokeWidth = 2.dp,
        )
        else -> IconButton(onClick = { controller.start() }) {
            Icon(Icons.Default.Mic, contentDescription = "Record")
        }
    }
}

private fun whisperSupportingText(state: WhisperState): (@Composable () -> Unit)? = when (state) {
    is WhisperState.Recording -> ({ Text("Recording… ${state.seconds}s — tap stop when done") })
    is WhisperState.Transcribing -> ({ Text("Transcribing on device…") })
    is WhisperState.Error -> ({ Text(state.message, color = MaterialTheme.colorScheme.error) })
    else -> null
}
