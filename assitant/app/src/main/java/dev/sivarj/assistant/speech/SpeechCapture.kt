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
 * Wraps [SpeechRecognizer] for Compose with **continuous dictation**: Android
 * ends a recognition session at every pause in speech, so this controller
 * delivers the recognized segment and immediately starts a new session.
 * Listening therefore survives pauses and only ends when [stop] is called
 * (or an unrecoverable error occurs).
 */
class SpeechCaptureController internal constructor(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStateChange: (SpeechState) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    internal var requestPermission: (() -> Unit)? = null

    /** True from start() until the user taps stop (or a hard error). */
    private var sessionActive = false

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
        sessionActive = true
        startRecognitionCycle()
    }

    /** One SpeechRecognizer session; re-invoked after each pause while [sessionActive]. */
    private fun startRecognitionCycle() {
        destroyRecognizer()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStateChange(SpeechState.Listening)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                when (error) {
                    // A pause with no speech, or a transient race on restart:
                    // keep the continuous session going.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    -> {
                        if (sessionActive) startRecognitionCycle()
                        else onStateChange(SpeechState.Idle)
                    }

                    else -> {
                        sessionActive = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                            -> "Language pack not available offline"
                            else -> "Speech recognition error ($error)"
                        }
                        onStateChange(SpeechState.Error(message))
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onResult(text)
                // The user paused — deliver the segment and keep listening.
                if (sessionActive) startRecognitionCycle()
                else onStateChange(SpeechState.Idle)
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
            // Hints to tolerate longer pauses within one session (best-effort;
            // some recognizer implementations ignore these).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        r.startListening(intent)
    }

    fun stop() {
        sessionActive = false
        destroyRecognizer()
        onStateChange(SpeechState.Idle)
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Remembers a [SpeechCaptureController] and exposes its current [SpeechState].
 * Final recognized text arrives through [onResult], one segment per pause.
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
