package dev.sivarj.assistant.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class WhisperModel(
    val fileName: String,
    val displayName: String,
    val url: String,
    val approxSizeMb: Int,
)

val WHISPER_MODELS = listOf(
    WhisperModel(
        fileName = "ggml-base-q5_1.bin",
        displayName = "Whisper Base (fast, ~60MB)",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        approxSizeMb = 60,
    ),
    WhisperModel(
        fileName = "ggml-small-q5_1.bin",
        displayName = "Whisper Small (accurate, ~190MB)",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        approxSizeMb = 190,
    ),
)

/** Downloads and stores whisper models in app-private storage. */
class WhisperModelManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    private fun modelsDir(): File = File(context.filesDir, "whisper").apply { mkdirs() }

    fun localFile(model: WhisperModel): File = File(modelsDir(), model.fileName)

    fun isDownloaded(model: WhisperModel): Boolean = localFile(model).length() > 1_000_000

    /**
     * Downloads [model] with progress callbacks (0..100). Writes to a .part
     * file and renames on completion so partial downloads never pass
     * [isDownloaded].
     */
    suspend fun download(
        model: WhisperModel,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = localFile(model)
            val partial = File(target.path + ".part")
            val request = Request.Builder().url(model.url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty response body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        var written = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            written += n
                            if (total > 0) {
                                val pct = ((written * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
            check(partial.renameTo(target)) { "Could not finalize download" }
            target
        }
    }

    fun delete(model: WhisperModel) {
        localFile(model).delete()
    }
}
