package com.openear.app.transcription

/** Raw JNI declarations. Only [JniWhisper] should touch this object. */
object WhisperNative {
    @JvmStatic external fun nativeInit(modelPath: String): Long
    @JvmStatic external fun nativeFree(handle: Long)
    @JvmStatic external fun nativeAbort(handle: Long)
    @JvmStatic external fun nativeTranscribe(handle: Long, audio: FloatArray, threads: Int, language: String, audioCtx: Int): ByteArray?
    @JvmStatic external fun nativeTimings(handle: Long): FloatArray?
}
