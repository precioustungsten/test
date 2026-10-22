package com.openear.app.settings

import android.content.SharedPreferences

enum class LanguageMode(val code: String, val label: String) {
    AUTO("auto", "Auto-detect"),
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi");

    companion object {
        fun fromCode(code: String?, fallback: LanguageMode = AUTO): LanguageMode =
            entries.firstOrNull { it.code == code } ?: fallback
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Tiny storage abstraction so settings logic is unit-testable without Android. */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun clear()
}

class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun clear() = prefs.edit().clear().apply()
}

class Settings(private val store: KeyValueStore) {

    var languageMode: LanguageMode
        get() = LanguageMode.fromCode(store.getString(K_LANGUAGE, null), DEFAULT_LANGUAGE)
        set(value) = store.putString(K_LANGUAGE, value.code)

    var modelId: String
        get() = store.getString(K_MODEL, null) ?: DEFAULT_MODEL_ID
        set(value) = store.putString(K_MODEL, value)

    var trailingSpace: Boolean
        get() = store.getBoolean(K_TRAILING_SPACE, true)
        set(value) = store.putBoolean(K_TRAILING_SPACE, value)

    var haptics: Boolean
        get() = store.getBoolean(K_HAPTICS, false)
        set(value) = store.putBoolean(K_HAPTICS, value)

    var theme: ThemeMode
        get() = ThemeMode.fromName(store.getString(K_THEME, null))
        set(value) = store.putString(K_THEME, value.name)

    /** Fast mode trims Whisper's encoder to the clip length. Big latency win, tiny accuracy cost. */
    var fastMode: Boolean
        get() = store.getBoolean(K_FAST_MODE, true)
        set(value) = store.putBoolean(K_FAST_MODE, value)

    /** CPU threads for transcription; 0 = automatic. */
    var threads: Int
        get() = store.getInt(K_THREADS, 0).coerceIn(0, 8)
        set(value) = store.putInt(K_THREADS, value.coerceIn(0, 8))

    /** Hard cap on one recording, shown to the user before recording starts. */
    var maxRecordSeconds: Int
        get() = store.getInt(K_MAX_SECONDS, DEFAULT_MAX_SECONDS).coerceIn(MIN_MAX_SECONDS, MAX_MAX_SECONDS)
        set(value) = store.putInt(K_MAX_SECONDS, value.coerceIn(MIN_MAX_SECONDS, MAX_MAX_SECONDS))

    /** Minutes of inactivity before the native model is released from memory. */
    var idleReleaseMinutes: Int
        get() = store.getInt(K_IDLE_MINUTES, DEFAULT_IDLE_MINUTES).coerceIn(1, 30)
        set(value) = store.putInt(K_IDLE_MINUTES, value.coerceIn(1, 30))

    fun resetToDefaults() = store.clear()

    companion object {
        const val DEFAULT_MODEL_ID = "tiny.en-q5_1"
        val DEFAULT_LANGUAGE = LanguageMode.ENGLISH
        const val DEFAULT_MAX_SECONDS = 60
        const val MIN_MAX_SECONDS = 10
        const val MAX_MAX_SECONDS = 120
        const val DEFAULT_IDLE_MINUTES = 2

        private const val K_LANGUAGE = "language_mode"
        private const val K_MODEL = "model_id"
        private const val K_TRAILING_SPACE = "trailing_space"
        private const val K_HAPTICS = "haptics"
        private const val K_THEME = "theme"
        private const val K_MAX_SECONDS = "max_record_seconds"
        private const val K_IDLE_MINUTES = "idle_release_minutes"
        private const val K_FAST_MODE = "fast_mode"
        private const val K_THREADS = "threads"
    }
}
