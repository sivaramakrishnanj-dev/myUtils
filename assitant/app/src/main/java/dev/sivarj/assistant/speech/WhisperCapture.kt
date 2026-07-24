package dev.sivarj.assistant.speech

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.sivarj.assistant.AssistantApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** UI state of a whisper record→transcribe session. */
sealed interface WhisperState {
    data object Idle : WhisperState
    data class Recording(val seconds: Int) : WhisperState
    data object Transcribing : WhisperState
    data class Error(val message: String) : WhisperState
}

class WhisperCaptureController internal constructor(
    private val onStart: () -> Unit,
    private val onStopAndTranscribe: () -> Unit,
) {
    internal var requestPermission: (() -> Unit)? = null
    internal var hasPermission: (() -> Boolean)? = null

    fun start() {
        if (hasPermission?.invoke() == true) onStart() else requestPermission?.invoke()
    }

    fun stop() = onStopAndTranscribe()
}

/**
 * Record-then-transcribe capture using on-device whisper.cpp.
 * Recognized text arrives once via [onResult] after transcription finishes.
 */
@Composable
fun rememberWhisperCapture(onResult: (String) -> Unit): Pair<WhisperCaptureController, WhisperState> {
    val context = LocalContext.current
    val app = context.applicationContext as AssistantApp
    var state by remember { mutableStateOf<WhisperState>(WhisperState.Idle) }
    val currentOnResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val recorder = remember { PcmRecorder() }

    fun doStart() {
        if (recorder.start()) {
            state = WhisperState.Recording(0)
            scope.launch {
                while (state is WhisperState.Recording) {
                    kotlinx.coroutines.delay(1000)
                    if (state is WhisperState.Recording) {
                        state = WhisperState.Recording(recorder.durationSeconds.toInt())
                    }
                }
            }
        } else {
            state = WhisperState.Error("Could not open microphone")
        }
    }

    fun doStopAndTranscribe() {
        if (state !is WhisperState.Recording) return
        val samples = recorder.stop()
        if (samples.size < PcmRecorder.SAMPLE_RATE / 2) { // <0.5s — nothing useful
            state = WhisperState.Idle
            return
        }
        state = WhisperState.Transcribing
        scope.launch {
            val modelFile = app.settings.config.first().whisperModelFile
            val text = app.whisperTranscriber.transcribe(samples, modelFile.ifBlank { null })
            state = when {
                text == null -> WhisperState.Error("No Whisper model downloaded — go to Settings")
                text.isBlank() -> WhisperState.Error("Could not transcribe — try again")
                else -> {
                    currentOnResult(text)
                    WhisperState.Idle
                }
            }
        }
    }

    val controller = remember {
        WhisperCaptureController(
            onStart = ::doStart,
            onStopAndTranscribe = ::doStopAndTranscribe,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doStart()
        else state = WhisperState.Error("Microphone permission denied")
    }
    controller.requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    controller.hasPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder.stop() } }
    }
    return controller to state
}
