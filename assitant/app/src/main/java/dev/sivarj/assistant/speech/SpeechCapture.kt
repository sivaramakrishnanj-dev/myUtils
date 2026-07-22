package dev.sivarj.assistant.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * Wraps [SpeechRecognizer] for Compose with **continuous dictation**.
 *
 * Android ends a recognition session at every pause, and the recognition
 * service can also fail transiently (network blips, audio-busy races, or
 * simply wedging after a restart). This controller keeps one logical session
 * alive across all of that:
 *  - each delivered segment immediately starts a new recognition cycle,
 *  - every recoverable error retries after a short settle delay,
 *  - a watchdog restarts the cycle if the recognizer goes silent without
 *    delivering any callbacks,
 *  - only unrecoverable errors (mic permission, missing language pack) or an
 *    explicit [stop] end the session.
 */
class SpeechCaptureController internal constructor(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStateChange: (SpeechState) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    internal var requestPermission: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    /** True from start() until the user taps stop (or an unrecoverable error). */
    private var sessionActive = false

    /** Consecutive *unexpected* errors — silence timeouts don't count. */
    private var consecutiveErrors = 0

    private val restartRunnable = Runnable { if (sessionActive) startRecognitionCycle() }
    private val watchdogRunnable = Runnable {
        // No callback arrived since the cycle started — recognizer is wedged.
        if (sessionActive) startRecognitionCycle()
    }

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
        consecutiveErrors = 0
        startRecognitionCycle()
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    /** One SpeechRecognizer session; re-invoked while [sessionActive]. */
    private fun startRecognitionCycle() {
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(watchdogRunnable)
        destroyRecognizer()

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                handler.removeCallbacks(watchdogRunnable)
                onStateChange(SpeechState.Listening)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                handler.removeCallbacks(watchdogRunnable)
                if (!sessionActive) {
                    onStateChange(SpeechState.Idle)
                    return
                }
                when (error) {
                    // Unrecoverable — surface and end the session.
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        sessionActive = false
                        onStateChange(SpeechState.Error("Microphone permission denied"))
                    }
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    -> {
                        sessionActive = false
                        onStateChange(SpeechState.Error("Language pack not available offline"))
                    }

                    // Plain silence — the normal end of a pause; restart quickly
                    // and don't count it against the error budget.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> {
                        consecutiveErrors = 0
                        scheduleRestart(RESTART_DELAY_MS)
                    }

                    // Everything else (client/busy/network/server/audio) is a
                    // transient service failure — retry with backoff, but give
                    // up if it repeats without any successful recognition.
                    else -> {
                        consecutiveErrors++
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            sessionActive = false
                            onStateChange(SpeechState.Error("Speech recognition keeps failing (error $error) — try again"))
                        } else {
                            scheduleRestart(RESTART_DELAY_MS * consecutiveErrors)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                handler.removeCallbacks(watchdogRunnable)
                consecutiveErrors = 0
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onResult(text)
                // The user paused — deliver the segment and keep listening.
                if (sessionActive) scheduleRestart(RESTART_DELAY_MS)
                else onStateChange(SpeechState.Idle)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                consecutiveErrors = 0
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
        // If neither ready/error/results arrives, force a restart.
        handler.postDelayed(watchdogRunnable, WATCHDOG_MS)
    }

    fun stop() {
        sessionActive = false
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(watchdogRunnable)
        destroyRecognizer()
        onStateChange(SpeechState.Idle)
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        /** Settle time between destroy and recreate so the restart doesn't race the service. */
        const val RESTART_DELAY_MS = 250L
        /** If the recognizer produces no callback at all within this window, restart it. */
        const val WATCHDOG_MS = 8_000L
        /** Consecutive unexpected failures before giving up. */
        const val MAX_CONSECUTIVE_ERRORS = 6
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
