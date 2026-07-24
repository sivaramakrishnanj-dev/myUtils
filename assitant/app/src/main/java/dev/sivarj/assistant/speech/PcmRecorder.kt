package dev.sivarj.assistant.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records mono 16kHz float PCM — exactly the input whisper.cpp expects.
 * Runs the read loop on a dedicated thread; accumulates in memory
 * (~3.8MB per minute — fine for dictation-length recordings).
 */
class PcmRecorder {

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private val recording = AtomicBoolean(false)
    private val chunks = mutableListOf<FloatArray>()

    /** Current recorded duration in seconds. */
    val durationSeconds: Float
        get() = synchronized(chunks) { chunks.sumOf { it.size }.toFloat() / SAMPLE_RATE }

    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO
    fun start(): Boolean {
        if (recording.get()) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuf <= 0) return false
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            minBuf * 4,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        synchronized(chunks) { chunks.clear() }
        audioRecord = record
        recording.set(true)
        record.startRecording()
        thread = Thread {
            val buf = FloatArray(minBuf)
            while (recording.get()) {
                val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n > 0) {
                    synchronized(chunks) { chunks.add(buf.copyOf(n)) }
                }
            }
        }.also { it.start() }
        return true
    }

    /** Stops recording and returns all captured samples. */
    fun stop(): FloatArray {
        recording.set(false)
        thread?.join(1000)
        thread = null
        audioRecord?.run {
            runCatching { stop() }
            release()
        }
        audioRecord = null
        return synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val out = FloatArray(total)
            var offset = 0
            for (c in chunks) {
                c.copyInto(out, offset)
                offset += c.size
            }
            chunks.clear()
            out
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
