package dev.sivarj.assistant.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-scoped transcription facade. Loads the whisper model lazily and keeps
 * it in memory across transcriptions (model load takes seconds; transcribe
 * calls are serialized by a mutex since whisper_context is not thread-safe).
 */
class WhisperTranscriber(context: Context) {

    private val modelManager = WhisperModelManager(context)
    private val mutex = Mutex()
    private var bridge: WhisperBridge? = null
    private var loadedModelFile: String? = null

    /** The first downloaded model, or null if none downloaded yet. */
    fun availableModel(): WhisperModel? = WHISPER_MODELS.firstOrNull { modelManager.isDownloaded(it) }

    fun preferredModel(fileName: String?): WhisperModel? =
        WHISPER_MODELS.firstOrNull { it.fileName == fileName && modelManager.isDownloaded(it) }
            ?: availableModel()

    /**
     * Transcribes [samples] using [modelFileName] (or the first available
     * model). Returns null on failure with no model, "" on transcribe failure.
     */
    suspend fun transcribe(samples: FloatArray, modelFileName: String?): String? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val model = preferredModel(modelFileName) ?: return@withLock null
                if (loadedModelFile != model.fileName) {
                    bridge?.release()
                    bridge = WhisperBridge.create(modelManager.localFile(model).absolutePath)
                    loadedModelFile = if (bridge != null) model.fileName else null
                }
                bridge?.transcribe(samples)?.trim()
            }
        }
}
