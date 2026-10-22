package com.openear.app.transcription

/** Abstraction over the JNI layer so the engine can be tested with a fake. */
interface NativeWhisper {
    val isAvailable: Boolean
    fun init(modelPath: String): Long
    fun free(handle: Long)
    fun abort(handle: Long)

    /** [audioCtx] of 0 means Whisper's default (full 30 s encoder window). */
    fun transcribe(handle: Long, audio: FloatArray, threads: Int, language: String, audioCtx: Int): ByteArray?

    /** Approximate [sample, encode, decode] milliseconds of the last run, if available. */
    fun timings(handle: Long): FloatArray?
}

object JniWhisper : NativeWhisper {
    private val loaded: Boolean = try {
        System.loadLibrary("openear_whisper")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    override val isAvailable: Boolean get() = loaded
    override fun init(modelPath: String): Long = WhisperNative.nativeInit(modelPath)
    override fun free(handle: Long) = WhisperNative.nativeFree(handle)
    override fun abort(handle: Long) = WhisperNative.nativeAbort(handle)
    override fun transcribe(handle: Long, audio: FloatArray, threads: Int, language: String, audioCtx: Int): ByteArray? =
        WhisperNative.nativeTranscribe(handle, audio, threads, language, audioCtx)
    override fun timings(handle: Long): FloatArray? = WhisperNative.nativeTimings(handle)
}
