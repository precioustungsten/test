// Thin JNI wrapper over whisper.cpp for OpenEar. Kotlin owns the lifecycle:
// a handle is created by init(), freed exactly once by free(), and never used
// after that. All entry points validate their inputs and never throw native
// exceptions across the JNI boundary.
#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "OpenEarNative"
// Never log transcript text or audio. Only lifecycle and error codes.
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Handle {
    whisper_context *ctx = nullptr;
    std::atomic<bool> abort_flag{false};
    std::mutex run_mutex;  // one inference at a time per context
};

bool should_abort(void *user_data) {
    auto *h = static_cast<Handle *>(user_data);
    return h->abort_flag.load();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openear_app_transcription_WhisperNative_nativeInit(JNIEnv *env, jclass, jstring model_path) {
    if (model_path == nullptr) return 0;
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) {
        LOGE("model load failed");
        return 0;
    }
    auto *h = new Handle();
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL
Java_com_openear_app_transcription_WhisperNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    auto *h = reinterpret_cast<Handle *>(handle);
    {
        std::lock_guard<std::mutex> lock(h->run_mutex);  // wait for a running inference
        whisper_free(h->ctx);
        h->ctx = nullptr;
    }
    delete h;
}

JNIEXPORT void JNICALL
Java_com_openear_app_transcription_WhisperNative_nativeAbort(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    reinterpret_cast<Handle *>(handle)->abort_flag.store(true);
}

// Returns the transcript as UTF-8 bytes (decoded in Kotlin: segment text can split a multi-byte
// character, which NewStringUTF must not see), or null on failure/abort.
// language: "auto", or an ISO code like "en", "hi".
JNIEXPORT jbyteArray JNICALL
Java_com_openear_app_transcription_WhisperNative_nativeTranscribe(JNIEnv *env, jclass, jlong handle,
                                                            jfloatArray audio, jint n_threads,
                                                            jstring language, jint audio_ctx) {
    if (handle == 0 || audio == nullptr || language == nullptr) return nullptr;
    auto *h = reinterpret_cast<Handle *>(handle);
    const jsize n = env->GetArrayLength(audio);
    if (n <= 0) return nullptr;

    const char *lang_chars = env->GetStringUTFChars(language, nullptr);
    if (lang_chars == nullptr) return nullptr;
    const std::string lang(lang_chars);
    env->ReleaseStringUTFChars(language, lang_chars);

    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    if (samples == nullptr) return nullptr;

    std::lock_guard<std::mutex> lock(h->run_mutex);
    if (h->ctx == nullptr) {
        env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
        return nullptr;
    }
    h->abort_flag.store(false);

    // Latency-oriented decoding for short dictation clips. Quality-neutral where possible:
    // greedy decoding, no timestamp tokens, no temperature-fallback re-decodes.
    const double seconds = static_cast<double>(n) / 16000.0;
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;  // never translate: preserve what was said
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = seconds <= 28.0;
    params.temperature_inc = 0.0f;  // disable fallback retries (they multiply worst-case latency)
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.greedy.best_of = 1;
    params.n_threads = n_threads > 0 ? n_threads : 2;
    params.detect_language = false;
    // 0 keeps Whisper's default 30 s encoder window; a smaller value trims the encoder to the clip.
    params.audio_ctx = audio_ctx > 0 ? audio_ctx : 0;
    params.language = (lang == "auto") ? "auto" : lang.c_str();
    params.abort_callback = should_abort;
    params.abort_callback_user_data = h;

    whisper_reset_timings(h->ctx);
    const int rc = whisper_full(h->ctx, params, samples, n);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);

    if (rc != 0 || h->abort_flag.load()) {
        if (rc != 0 && !h->abort_flag.load()) LOGE("whisper_full failed rc=%d", rc);
        return nullptr;
    }

    std::string out;
    const int segs = whisper_full_n_segments(h->ctx);
    for (int i = 0; i < segs; i++) {
        const char *t = whisper_full_get_segment_text(h->ctx, i);
        if (t != nullptr) out += t;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()),
                            reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

// Approximate per-phase timings of the last run in milliseconds: [sample, encode, decode].
JNIEXPORT jfloatArray JNICALL
Java_com_openear_app_transcription_WhisperNative_nativeTimings(JNIEnv *env, jclass, jlong handle) {
    if (handle == 0) return nullptr;
    auto *h = reinterpret_cast<Handle *>(handle);
    std::lock_guard<std::mutex> lock(h->run_mutex);
    if (h->ctx == nullptr) return nullptr;
    const whisper_timings *t = whisper_get_timings(h->ctx);
    if (t == nullptr) return nullptr;
    const jfloat values[3] = {t->sample_ms, t->encode_ms, t->decode_ms + t->batchd_ms + t->prompt_ms};
    jfloatArray out = env->NewFloatArray(3);
    if (out == nullptr) return nullptr;
    env->SetFloatArrayRegion(out, 0, 3, values);
    return out;
}

}  // extern "C"
