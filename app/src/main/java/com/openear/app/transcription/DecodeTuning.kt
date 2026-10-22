package com.openear.app.transcription

import kotlin.math.ceil

/** Pure helpers that decide how much encoder work a clip needs. Unit-tested on the JVM. */
object DecodeTuning {
    const val MIN_AUDIO_CTX = 512 // ~10 s: shorter windows measurably hurt accuracy
    const val MAX_AUDIO_CTX = 1500 // Whisper's full 30 s window
    private const val FRAMES_PER_SECOND = 50
    private const val MARGIN_FRAMES = 100 // 2 s of slack after the speech
    private const val FULL_WINDOW_SECONDS = 28.0

    /**
     * Whisper always encodes a padded 30 s window, so a 4 s dictation pays for 30 s.
     * In fast mode we shrink the encoder context to the clip (plus margin) instead.
     * Returns 0 for "use Whisper's default".
     */
    fun audioCtxFor(sampleCount: Int, fast: Boolean, sampleRate: Int = 16_000): Int {
        if (!fast || sampleCount <= 0 || sampleRate <= 0) return 0
        val seconds = sampleCount.toDouble() / sampleRate
        if (seconds > FULL_WINDOW_SECONDS) return 0
        val frames = ceil(seconds * FRAMES_PER_SECOND).toInt() + MARGIN_FRAMES
        val rounded = ((frames + 63) / 64) * 64
        return rounded.coerceIn(MIN_AUDIO_CTX, MAX_AUDIO_CTX)
    }
}
