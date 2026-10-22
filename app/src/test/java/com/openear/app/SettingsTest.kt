package com.openear.app

import com.openear.app.settings.LanguageMode
import com.openear.app.settings.Settings
import com.openear.app.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun defaults() {
        val s = Settings(MemoryStore())
        assertEquals(LanguageMode.ENGLISH, s.languageMode)
        assertEquals("tiny.en-q5_1", s.modelId)
        assertTrue(s.fastMode)
        assertEquals(0, s.threads)
        assertTrue(s.trailingSpace)
        assertFalse(s.haptics)
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(60, s.maxRecordSeconds)
    }

    @Test fun valuesPersistAcrossInstancesSharingAStore() {
        val store = MemoryStore()
        Settings(store).apply {
            languageMode = LanguageMode.HINDI
            modelId = "tiny"
            trailingSpace = false
            haptics = true
            theme = ThemeMode.DARK
            maxRecordSeconds = 30
        }
        val s = Settings(store)
        assertEquals(LanguageMode.HINDI, s.languageMode)
        assertEquals("tiny", s.modelId)
        assertFalse(s.trailingSpace)
        assertTrue(s.haptics)
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(30, s.maxRecordSeconds)
    }

    @Test fun maxRecordSecondsIsClamped() {
        val s = Settings(MemoryStore())
        s.maxRecordSeconds = 100_000
        assertEquals(Settings.MAX_MAX_SECONDS, s.maxRecordSeconds)
        s.maxRecordSeconds = 1
        assertEquals(Settings.MIN_MAX_SECONDS, s.maxRecordSeconds)
    }

    @Test fun unknownStoredValuesFallBackToDefaults() {
        val store = MemoryStore()
        store.putString("language_mode", "klingon")
        store.putString("theme", "NEON")
        val s = Settings(store)
        assertEquals(LanguageMode.ENGLISH, s.languageMode)
        assertEquals(ThemeMode.SYSTEM, s.theme)
    }

    @Test fun threadsAreClamped() {
        val s = Settings(MemoryStore())
        s.threads = 99
        assertEquals(8, s.threads)
        s.threads = -3
        assertEquals(0, s.threads)
    }

    @Test fun resetRestoresDefaults() {
        val s = Settings(MemoryStore())
        s.languageMode = LanguageMode.HINDI
        s.trailingSpace = false
        s.resetToDefaults()
        assertEquals(LanguageMode.ENGLISH, s.languageMode)
        assertTrue(s.trailingSpace)
    }

    @Test fun translationIsNeverAnOption() {
        // The UI offers only these modes; none of them translate.
        assertEquals(listOf("auto", "en", "hi"), LanguageMode.entries.map { it.code })
    }
}
