package com.openear.app.transcription

sealed interface TranscriptionResult {
    data class Success(val rawText: String) : TranscriptionResult
    data object Cancelled : TranscriptionResult
    data class Failure(val kind: FailureKind) : TranscriptionResult
}

enum class FailureKind { InvalidInput, NativeUnavailable, ModelMissing, ModelLoadFailed, OutOfMemory, Busy, InferenceFailed }

/** Timing of the most recent transcription, for the Test screen and tuning. */
data class TranscriptionTiming(
    val audioMs: Long,
    val loadMs: Long,
    val totalMs: Long,
    val encodeMs: Long?,
    val decodeMs: Long?,
)

interface Transcriber {
    suspend fun transcribe(modelPath: String, audio: FloatArray, language: String, threads: Int): TranscriptionResult

    /**
     * Loads the model in the background so it is ready when recording stops. Safe to call
     * repeatedly; a no-op if the model is already loaded or the engine is unavailable.
     */
    fun preload(modelPath: String)

    /** Asks a running transcription to stop; its result is discarded. */
    fun cancel()

    /** Frees native memory. The model reloads on the next transcription. */
    fun release()
}
