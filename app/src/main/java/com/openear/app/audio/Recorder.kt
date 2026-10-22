package com.openear.app.audio

class RecordedAudio(val samples: FloatArray)

enum class RecorderError { Busy, Unavailable }

sealed interface StartResult {
    data object Started : StartResult
    data class Failed(val error: RecorderError) : StartResult
}

interface RecorderListener {
    /** The configured maximum duration was reached; the caller should finish the recording. */
    fun onLimitReached()

    /** Recording was interrupted (phone call, another app took audio focus, read error). */
    fun onInterrupted()
}

/** Records microphone audio only between an explicit start() and stop()/cancel(). */
interface Recorder {
    val isRecording: Boolean
    fun start(maxSeconds: Int, listener: RecorderListener): StartResult

    /** Stops and returns 16 kHz mono float audio, or null if nothing valid was captured. */
    fun stop(): RecordedAudio?

    /** Stops and discards everything. Safe to call at any time. */
    fun cancel()
}
