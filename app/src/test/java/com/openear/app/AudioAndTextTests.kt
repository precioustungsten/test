package com.openear.app

import com.openear.app.audio.Pcm
import com.openear.app.keyboard.FieldRules
import com.openear.app.privacy.SafeLog
import com.openear.app.transcription.TranscriptFormatter
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {
    @Test fun convertsPcm16ToUnitFloats() {
        val out = Pcm.toFloat(shortArrayOf(0, 16384, -32768, 32767))
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(0.5f, out[1], 1e-6f)
        assertEquals(-1f, out[2], 1e-6f)
        assertEquals(1f, out[3], 1e-3f)
    }

    @Test fun conversionRespectsCountAndBounds() {
        assertEquals(2, Pcm.toFloat(shortArrayOf(1, 2, 3), 2).size)
        assertEquals(0, Pcm.toFloat(shortArrayOf(1, 2, 3), -5).size)
        assertEquals(3, Pcm.toFloat(shortArrayOf(1, 2, 3), 99).size)
        assertEquals(0, Pcm.toFloat(ShortArray(0)).size)
    }

    @Test fun resampleChangesLengthProportionally() {
        val input = FloatArray(48_000) { 0.25f }
        val out = Pcm.resample(input, 48_000, 16_000)
        assertEquals(16_000, out.size)
        out.forEach { assertEquals(0.25f, it, 1e-5f) }
    }

    @Test fun resampleSameRateReturnsCopyAndEmptyStaysEmpty() {
        val input = floatArrayOf(0.1f, 0.2f)
        val out = Pcm.resample(input, 16_000, 16_000)
        assertEquals(input.toList(), out.toList())
        assertEquals(0, Pcm.resample(FloatArray(0), 44_100, 16_000).size)
    }

    @Test(expected = IllegalArgumentException::class) fun resampleRejectsInvalidRate() {
        Pcm.resample(floatArrayOf(1f), 0, 16_000)
    }

    @Test fun silenceAndTinyClipsAreNotSpeech() {
        assertFalse(Pcm.looksLikeSpeech(FloatArray(0)))
        assertFalse(Pcm.looksLikeSpeech(FloatArray(16_000))) // 1 s of digital silence
        assertFalse(Pcm.looksLikeSpeech(FloatArray(1_000) { 0.5f })) // far too short
        assertTrue(Pcm.looksLikeSpeech(FloatArray(16_000) { 0.1f }))
    }

    @Test fun rmsOfEmptyIsZero() = assertEquals(0f, Pcm.rms(FloatArray(0)), 0f)
}

class TranscriptFormatterTest {
    @Test fun trimsAndCollapsesWhitespace() {
        assertEquals("kal meeting kitne baje hai?", TranscriptFormatter.format("  kal   meeting\nkitne baje hai? ", false))
    }

    @Test fun trailingSpaceIsOptional() {
        assertEquals("hello ", TranscriptFormatter.format("hello", true))
        assertEquals("hello", TranscriptFormatter.format("hello", false))
    }

    @Test fun emptyAndWhitespaceYieldNothing() {
        assertNull(TranscriptFormatter.format("", true))
        assertNull(TranscriptFormatter.format("   \n ", true))
    }

    @Test fun nonSpeechMarkersAreNotInserted() {
        assertNull(TranscriptFormatter.format("[BLANK_AUDIO]", true))
        assertNull(TranscriptFormatter.format(" [Music] ", true))
        assertNull(TranscriptFormatter.format("(silence)", false))
        assertNull(TranscriptFormatter.format("[ Silence ]", false))
    }

    @Test fun realSpeechIsNeverAltered() {
        assertEquals("आज office थोड़ा late पहुँचूँगा ", TranscriptFormatter.format("आज office थोड़ा late पहुँचूँगा", true))
        assertEquals("Press [Enter] to send ", TranscriptFormatter.format("Press [Enter] to send", true))
    }
}

class FieldRulesTest {
    @Test fun passwordFieldsAreRestricted() {
        assertTrue(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(FieldRules.isRestricted(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test fun ordinaryFieldsAreAllowed() {
        assertFalse(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT))
        assertFalse(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(FieldRules.isRestricted(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
    }
}

class SafeLogTest {
    @Test fun describeNeverContainsTheText() {
        val secret = "my bank pin is 4921"
        val described = SafeLog.describe(secret)
        assertFalse(described.contains("4921"))
        assertFalse(described.contains("bank"))
        assertTrue(described.contains(secret.length.toString()))
        assertEquals("<text length=0>", SafeLog.describe(null))
    }
}
