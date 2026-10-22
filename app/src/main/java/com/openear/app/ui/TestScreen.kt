package com.openear.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openear.app.OpenEarApp
import com.openear.app.audio.AndroidAudioRecorder
import com.openear.app.keyboard.DictationController
import com.openear.app.keyboard.DictationError
import com.openear.app.keyboard.DictationPhase
import com.openear.app.keyboard.DictationState
import com.openear.app.keyboard.TextTarget

@Composable
fun TestScreen(app: OpenEarApp, resumeTick: Int, modifier: Modifier) {
    val context = LocalContext.current
    var transcript by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(DictationState()) }
    var needsPermission by remember { mutableStateOf(false) }

    val recorder = remember { AndroidAudioRecorder(context) }
    val controller = remember {
        DictationController(
            recorder = recorder,
            transcriber = app.engine,
            settings = app.settings,
            models = app.modelProvider,
            permission = { hasMicPermission(context) },
            target = object : TextTarget {
                override fun fieldToken(): Long = 0L
                override fun isRestricted(): Boolean = false
                override fun insert(text: String, token: Long): Boolean {
                    transcript += text
                    return true
                }
            },
            scope = app.appScope,
            onState = { state = it; needsPermission = it.error == DictationError.PermissionDenied },
        )
    }
    // Leaving this screen or the app always stops recording.
    DisposableEffect(Unit) { onDispose { controller.cancel(); recorder.cancel() } }

    ScreenColumn(modifier) {
        Title("Test dictation")
        Body("Try dictation here without switching apps. Nothing is saved; the text disappears when you clear it or leave the app.")
        val listening = state.phase == DictationPhase.Listening
        Body(state.label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { controller.onMicPressed() }, enabled = state.phase != DictationPhase.Transcribing) {
                Text(if (listening) "Stop and transcribe" else "Start recording")
            }
            if (listening || state.phase == DictationPhase.Transcribing) {
                OutlinedButton(onClick = { controller.cancel() }) { Text("Cancel") }
            }
        }
        if (state.phase == DictationPhase.Inserted) {
            app.engine.lastTiming?.let { t ->
                Small(
                    "Last run: ${t.audioMs} ms of audio, ${t.totalMs} ms to transcribe" +
                        " (model load ${t.loadMs} ms" +
                        (if (t.encodeMs != null && t.decodeMs != null) ", encode ~${t.encodeMs} ms, decode ~${t.decodeMs} ms" else "") +
                        "). Fast mode: ${if (app.settings.fastMode) "on" else "off"}.",
                )
            }
        }
        if (needsPermission) Small("Grant microphone access on the Setup tab, then try again.")
        Small("Maximum recording length: ${app.settings.maxRecordSeconds} seconds.")

        SelectionContainer {
            Text(
                text = transcript.ifEmpty { "Your transcript will appear here." },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("OpenEar transcript", transcript))
            }, enabled = transcript.isNotEmpty()) { Text("Copy") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, transcript)
                context.startActivity(Intent.createChooser(send, "Share transcript"))
            }, enabled = transcript.isNotEmpty()) { Text("Share") }
            OutlinedButton(onClick = { transcript = "" }, enabled = transcript.isNotEmpty()) { Text("Clear") }
        }
    }
}
