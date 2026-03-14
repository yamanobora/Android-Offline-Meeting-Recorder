#include <jni.h>
#include <vector>
#include <string>
#include <android/log.h>

#include "whisper.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "WHISPER", __VA_ARGS__)

// ★ グローバルでWhisperモデルを保持
static whisper_context* g_ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_yamanobora_offlinerecorder_WhisperBridge_initModel(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, 0);

    LOGI("Loading Whisper model: %s", path);

    g_ctx = whisper_init_from_file(path);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_ctx) {
        LOGI("Failed to load model");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yamanobora_offlinerecorder_WhisperBridge_runWhisper(
        JNIEnv* env,
        jobject,
        jfloatArray audioData) {

    // ★ モデルが初期化されていない場合
    if (!g_ctx) {
        return env->NewStringUTF("MODEL NOT INITIALIZED");
    }

    jsize length = env->GetArrayLength(audioData);
    jfloat* elements = env->GetFloatArrayElements(audioData, 0);

    whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // ★ 日本語固定
    params.language = "ja";
    params.translate = false;

    // ★ リアルタイム向け設定
    params.no_context = true;
    params.single_segment = true;
    params.max_tokens = 0;

    params.n_threads = 4;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    // ★ 推論
    whisper_full(g_ctx, params, elements, length);

    std::string result;

    int n = whisper_full_n_segments(g_ctx);

    for (int i = 0; i < n; ++i) {
        result += whisper_full_get_segment_text(g_ctx, i);
        result += "\n";
    }

    env->ReleaseFloatArrayElements(audioData, elements, 0);

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_yamanobora_offlinerecorder_WhisperBridge_freeModel(
        JNIEnv*,
        jobject) {

    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
        LOGI("Whisper model freed");
    }
}