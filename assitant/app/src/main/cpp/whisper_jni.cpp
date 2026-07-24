#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <thread>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_sivarj_assistant_speech_WhisperBridge_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) {
        LOGE("Failed to load model");
        return 0;
    }
    LOGI("Model loaded");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_dev_sivarj_assistant_speech_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong ctx_ptr, jfloatArray samples) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx == nullptr) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    struct whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    unsigned hw = std::thread::hardware_concurrency();
    params.n_threads = hw >= 4 ? 4 : (hw > 0 ? (int) hw : 2);
    params.language = "auto";
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.no_timestamps = true;

    LOGI("Transcribing %d samples (%.1fs) on %d threads",
         (int) n, n / 16000.0f, params.n_threads);

    if (whisper_full(ctx, params, pcm.data(), (int) pcm.size()) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    std::string result;
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        result += whisper_full_get_segment_text(ctx, i);
    }
    LOGI("Transcription done: %d segments, %zu chars", segments, result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_dev_sivarj_assistant_speech_WhisperBridge_nativeFree(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx != nullptr) whisper_free(ctx);
}

} // extern "C"
