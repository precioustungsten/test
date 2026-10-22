package com.openear.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openear.app.BuildConfig
import com.openear.app.OpenEarApp
import com.openear.app.settings.LanguageMode
import com.openear.app.settings.ThemeMode

@Composable
fun SettingsScreen(app: OpenEarApp, modifier: Modifier, onThemeChanged: (ThemeMode) -> Unit) {
    val s = app.settings
    var language by remember { mutableStateOf(s.languageMode) }
    var trailing by remember { mutableStateOf(s.trailingSpace) }
    var haptics by remember { mutableStateOf(s.haptics) }
    var theme by remember { mutableStateOf(s.theme) }
    var maxSeconds by remember { mutableStateOf(s.maxRecordSeconds) }
    var fast by remember { mutableStateOf(s.fastMode) }
    var threads by remember { mutableStateOf(s.threads) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    ScreenColumn(modifier) {
        Title("Settings")

        Body("Transcription language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageMode.entries.forEach { mode ->
                FilterChip(selected = language == mode, onClick = { language = mode; s.languageMode = mode }, label = { Text(mode.label) })
            }
        }
        Small("English is the default and the fastest: it skips language detection. Choose Auto-detect or Hindi only if you dictate in those languages (with a multilingual model). OpenEar does not translate or rewrite what you said.")

        SwitchRow("Fast mode (shorter encoder window, lower latency)", fast) { fast = it; s.fastMode = it }
        Small("Whisper normally processes a padded 30 second window even for a 3 second phrase. Fast mode trims that to your clip. Turn it off if you notice accuracy problems.")

        Body("CPU threads")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2, 4, 6).forEach { n ->
                FilterChip(selected = threads == n, onClick = { threads = n; s.threads = n }, label = { Text(if (n == 0) "Auto" else "$n") })
            }
        }
        Small("Auto uses up to 4. On phones with 8 cores, 4 or 6 may be faster; test on the Test tab.")

        SwitchRow("Add a trailing space after inserted text", trailing) { trailing = it; s.trailingSpace = it }
        SwitchRow("Haptic feedback on keyboard keys", haptics) { haptics = it; s.haptics = it }

        Body("Maximum recording length: $maxSeconds s")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120).forEach { sec ->
                FilterChip(selected = maxSeconds == sec, onClick = { maxSeconds = sec; s.maxRecordSeconds = sec }, label = { Text("$sec s") })
            }
        }

        Body("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(selected = theme == mode, onClick = { theme = mode; s.theme = mode; onThemeChanged(mode) }, label = { Text(mode.label) })
            }
        }

        Title("Privacy")
        Body("Audio is recorded only while you are dictating and is processed on this device. Recordings are kept in memory and discarded right after transcription. OpenEar has no accounts, analytics or ads, and never uploads audio or text. The internet permission is used only when you choose to download a model. Text you insert into other apps is then handled by those apps under their own privacy policies.")
        Small("History is not implemented in this version, so no transcripts are stored.")

        Title("Data")
        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete all downloaded models") }
        if (confirmDelete) {
            Body("This removes every model and frees the storage. Continue?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { app.engine.release(); app.modelManager.deleteAll(); confirmDelete = false }) { Text("Delete") }
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        }
        OutlinedButton(onClick = { confirmReset = true }) { Text("Reset settings") }
        if (confirmReset) {
            Body("Reset all settings to their defaults?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    s.resetToDefaults()
                    language = s.languageMode; trailing = s.trailingSpace; haptics = s.haptics
                    theme = s.theme; maxSeconds = s.maxRecordSeconds; fast = s.fastMode; threads = s.threads
                    onThemeChanged(theme)
                    confirmReset = false
                }) { Text("Reset") }
                OutlinedButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        }

        Title("About")
        Body("OpenEar ${BuildConfig.VERSION_NAME}")
        Small("An independent community project using OpenAI's Whisper models via whisper.cpp. Not an official OpenAI product.")
        OutlinedButton(onClick = { showLicenses = !showLicenses }) { Text(if (showLicenses) "Hide open-source licenses" else "Open-source licenses") }
        if (showLicenses) Small(LICENSE_TEXT)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private const val LICENSE_TEXT = """whisper.cpp / ggml (MIT License)
Copyright (c) 2023-2026 The ggml authors
https://github.com/ggml-org/whisper.cpp

OpenAI Whisper model weights (MIT License)
Copyright (c) 2022 OpenAI
https://github.com/openai/whisper

AndroidX, Jetpack Compose, Material 3 and Kotlin coroutines (Apache License 2.0)

Full notices are in THIRD_PARTY_NOTICES.md in the source repository."""
