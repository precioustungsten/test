package com.openear.app.transcription

/** Turns raw Whisper output into text for insertion. It never rewrites wording. */
object TranscriptFormatter {

    // Whisper emits bracketed markers for non-speech audio, e.g. "[BLANK_AUDIO]", "[Music]", "(silence)".
    private val markerOnly = Regex("^[\\[(][^\\])]{0,40}[\\])]$")
    private val whitespace = Regex("\\s+")

    /** Returns the text to insert, or null when there is nothing meaningful to insert. */
    fun format(raw: String, trailingSpace: Boolean): String? {
        val cleaned = raw.replace(whitespace, " ").trim()
        if (cleaned.isEmpty()) return null
        if (isOnlyNonSpeechMarkers(cleaned)) return null
        return if (trailingSpace) "$cleaned " else cleaned
    }

    private fun isOnlyNonSpeechMarkers(text: String): Boolean {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        // Markers may contain spaces ("[ Silence ]"), so test the whole string and each token.
        if (markerOnly.matches(text)) return true
        return tokens.all { it.startsWith("[") && it.endsWith("]") || it.startsWith("(") && it.endsWith(")") }
    }
}
