package com.openear.app.audio

import kotlin.math.sqrt

/** Deterministic, Android-free audio preprocessing (tested on the JVM). */
object Pcm {
    /** Whisper expects 16 kHz mono float samples in [-1, 1]. */
    const val TARGET_RATE = 16_000

    /** Below this duration a recording is treated as an accidental tap. */
    const val MIN_DURATION_MS = 300

    fun toFloat(samples: ShortArray, count: Int = samples.size): FloatArray {
        val n = count.coerceIn(0, samples.size)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = samples[i] / 32768f
        return out
    }

    /** Linear-interpolation resampler, used only when the device cannot record at 16 kHz. */
    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "Sample rates must be positive" }
        if (input.isEmpty() || fromRate == toRate) return input.copyOf()
        val outLen = (input.size.toLong() * toRate / fromRate).toInt()
        if (outLen <= 0) return FloatArray(0)
        val ratio = fromRate.toDouble() / toRate
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }

    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size).toFloat()
    }

    fun durationMs(sampleCount: Int, rate: Int = TARGET_RATE): Long =
        if (rate <= 0) 0 else sampleCount.toLong() * 1000 / rate

    /** True if the audio is long enough and not digital silence. */
    fun looksLikeSpeech(samples: FloatArray, rate: Int = TARGET_RATE, minRms: Float = 0.002f): Boolean =
        durationMs(samples.size, rate) >= MIN_DURATION_MS && rms(samples) >= minRms
}
