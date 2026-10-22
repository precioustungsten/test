package com.openear.app

import com.openear.app.transcription.DecodeTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeTuningTest {
    private val rate = 16_000

    @Test fun fastModeOffAlwaysUsesTheDefaultWindow() =
        assertEquals(0, DecodeTuning.audioCtxFor(rate * 3, fast = false))

    @Test fun shortClipsGetTheMinimumWindow() =
        assertEquals(DecodeTuning.MIN_AUDIO_CTX, DecodeTuning.audioCtxFor(rate * 3, fast = true))

    @Test fun windowGrowsWithClipLengthAndStaysInBounds() {
        var previous = 0
        for (secs in 1..28) {
            val ctx = DecodeTuning.audioCtxFor(rate * secs, fast = true)
            assertTrue("secs=$secs ctx=$ctx", ctx in DecodeTuning.MIN_AUDIO_CTX..DecodeTuning.MAX_AUDIO_CTX)
            assertTrue("monotonic at $secs", ctx >= previous)
            assertEquals("multiple of 64 unless clamped", true, ctx % 64 == 0 || ctx == DecodeTuning.MAX_AUDIO_CTX)
            previous = ctx
        }
    }

    @Test fun windowAlwaysCoversTheSpeechPlusMargin() {
        for (secs in 1..28) {
            val ctx = DecodeTuning.audioCtxFor(rate * secs, fast = true)
            assertTrue("secs=$secs needs ${secs * 50} frames, got $ctx", ctx >= minOf(secs * 50 + 100, DecodeTuning.MAX_AUDIO_CTX))
        }
    }

    @Test fun longClipsFallBackToTheFullWindow() =
        assertEquals(0, DecodeTuning.audioCtxFor(rate * 40, fast = true))

    @Test fun degenerateInputsAreSafe() {
        assertEquals(0, DecodeTuning.audioCtxFor(0, fast = true))
        assertEquals(0, DecodeTuning.audioCtxFor(-5, fast = true))
        assertEquals(0, DecodeTuning.audioCtxFor(100, fast = true, sampleRate = 0))
    }
}
