package dev.sivarj.assistant.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** UI-facing state of a dictation session. */
sealed interface SpeechState {
    data object Idle : SpeechState
    data object Listening : SpeechState
    /** Live partial hypothesis while the user is still speaking. */
    data class Partial(val text: String) : SpeechState
    data class Error(val message: String) : SpeechState
}

/**
 * Wraps [SpeechRecognizer] for Compose: handles the RECORD_AUDIO permission
 * request, lifecycle-safe recognizer creation/destruction, and reduces the
 * listener callbacks to [state] + a single [onResult] with the final text.
 */
class SpeechCaptureController internal constructor(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStateChange: (SpeechState) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    internal var requestPermission: (() -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission?.invoke()
            return
        }
        startListening()
    }

    internal fun startListening() {
        if (!isAvailable) {
            onStateChange(SpeechState.Error("Speech recognition is not available on this device"))
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStateChange(SpeechState.Listening)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language pack not available offline"
                    else -> "Speech recognition error ($error)"
                }
                onStateChange(SpeechState.Error(message))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                onStateChange(SpeechState.Idle)
                if (text.isNotBlank()) onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onStateChange(SpeechState.Partial(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Remembers a [SpeechCaptureController] and exposes its current [SpeechState].
 * Final recognized text arrives through [onResult].
 */
@Composable
fun rememberSpeechCapture(onResult: (String) -> Unit): Pair<SpeechCaptureController, SpeechState> {
    val context = LocalContext.current
    var state by remember { mutableStateOf<SpeechState>(SpeechState.Idle) }
    val currentOnResult by rememberUpdatedState(onResult)

    val controller = remember {
        SpeechCaptureController(
            context = context,
            onResult = { currentOnResult(it) },
            onStateChange = { state = it },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.startListening()
        else state = SpeechState.Error("Microphone permission denied")
    }
    controller.requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }
    return controller to state
}
