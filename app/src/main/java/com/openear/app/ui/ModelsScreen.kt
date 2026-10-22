package com.openear.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openear.app.OpenEarApp
import com.openear.app.model.ModelCatalog
import com.openear.app.model.ModelInfo
import com.openear.app.model.ModelState
import com.openear.app.model.ModelStore

@Composable
fun ModelsScreen(app: OpenEarApp, modifier: Modifier) {
    val context = LocalContext.current
    val states by app.modelManager.states.collectAsState()
    var selected by remember { mutableStateOf(app.settings.modelId) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val result = try {
                context.contentResolver.openInputStream(uri)?.use { app.modelStore.importFrom(it) }
                    ?: ModelStore.ImportResult.Failed
            } catch (e: java.io.IOException) {
                ModelStore.ImportResult.Failed
            }
            app.modelManager.importFinished(result)
            importMessage = when (result) {
                ModelStore.ImportResult.Imported -> "Model imported."
                ModelStore.ImportResult.NotAModel -> "That file is not a ggml Whisper model."
                ModelStore.ImportResult.Failed -> "Import failed."
            }
            selected = app.settings.modelId
        }
    }

    ScreenColumn(modifier) {
        Title("Speech models")
        Body("Models are downloaded once over the internet (about 32 MB to 488 MB). After that, dictation works fully offline, including in airplane mode. A larger model is more accurate but slower and uses more memory. English-only models are the smallest and fastest. Multilingual models are needed for Hindi or Hinglish.")
        Small("Storage used by models: ${formatBytes(app.modelStore.usedBytes())}")

        ModelCatalog.all.forEach { info ->
            ModelCard(
                info = info,
                state = states[info.id] ?: ModelState.NotInstalled,
                isSelected = selected == info.id,
                onSelect = { app.settings.modelId = info.id; selected = info.id },
                app = app,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == ModelCatalog.IMPORTED_ID,
                    enabled = states[ModelCatalog.IMPORTED_ID] == ModelState.Ready,
                    onClick = { app.settings.modelId = ModelCatalog.IMPORTED_ID; selected = ModelCatalog.IMPORTED_ID },
                )
                Text("Imported model (advanced)", fontWeight = FontWeight.Bold)
            }
            Small("Import a compatible ggml Whisper model file. It is copied into private storage. Imported files are not checksum-verified, so only import files you trust.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import file") }
                if (states[ModelCatalog.IMPORTED_ID] == ModelState.Ready) {
                    OutlinedButton(onClick = { app.modelManager.deleteImported() }) { Text("Delete imported") }
                }
            }
            importMessage?.let { Body(it) }
        }

        Small("Models are MIT-licensed OpenAI Whisper weights converted by the whisper.cpp project. OpenEar is an independent community project, not an OpenAI product.")
    }
}

@Composable
private fun ModelCard(info: ModelInfo, state: ModelState, isSelected: Boolean, onSelect: () -> Unit, app: OpenEarApp) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isSelected, enabled = state == ModelState.Ready, onClick = onSelect)
            Text("${info.displayName} · ${formatBytes(info.sizeBytes)}", fontWeight = FontWeight.Bold)
        }
        Small(info.summary)
        when (state) {
            is ModelState.NotInstalled -> Button(onClick = { app.modelManager.start(info) }) { Text("Download") }
            is ModelState.Partial -> {
                Body("Paused at ${state.bytes * 100 / state.total}%")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { app.modelManager.start(info) }) { Text("Resume") }
                    OutlinedButton(onClick = { app.modelManager.cancelAndDiscard(info) }) { Text("Discard") }
                }
            }
            is ModelState.Downloading -> {
                Body("Downloading… ${if (state.total > 0) state.bytes * 100 / state.total else 0}%")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { app.modelManager.pause(info) }) { Text("Pause") }
                    OutlinedButton(onClick = { app.modelManager.cancelAndDiscard(info) }) { Text("Cancel") }
                }
            }
            is ModelState.Verifying -> Body("Verifying checksum…")
            is ModelState.Ready -> {
                Body(if (isSelected) "Ready · in use" else "Ready")
                OutlinedButton(onClick = { app.modelManager.delete(info) }) { Text("Delete to free space") }
            }
            is ModelState.Failed -> {
                Body("Failed: ${state.reason}")
                Button(onClick = { app.modelManager.start(info) }) { Text("Retry") }
            }
        }
    }
}
