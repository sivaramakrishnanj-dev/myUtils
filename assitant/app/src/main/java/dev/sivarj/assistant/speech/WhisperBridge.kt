package dev.sivarj.assistant.speech

/** Thin JNI wrapper over whisper.cpp. One instance per loaded model. */
class WhisperBridge private constructor(private var ctxPtr: Long) {

    /** Blocking; call from Dispatchers.Default. Returns "" on failure. */
    fun transcribe(samples: FloatArray): String {
        check(ctxPtr != 0L) { "WhisperBridge already released" }
        return nativeTranscribe(ctxPtr, samples)
    }

    fun release() {
        if (ctxPtr != 0L) {
            nativeFree(ctxPtr)
            ctxPtr = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("whisper_android")
        }

        /** Blocking model load (hundreds of MB mmap); call from Dispatchers.IO. */
        fun create(modelPath: String): WhisperBridge? {
            val ptr = nativeInit(modelPath)
            return if (ptr == 0L) null else WhisperBridge(ptr)
        }

        @JvmStatic
        private external fun nativeInit(modelPath: String): Long

        @JvmStatic
        private external fun nativeTranscribe(ctxPtr: Long, samples: FloatArray): String

        @JvmStatic
        private external fun nativeFree(ctxPtr: Long)
    }
}
