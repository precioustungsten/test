package com.openear.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.openear.app.OpenEarApp

internal fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

internal fun isKeyboardSelected(context: Context): Boolean {
    val current = AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
    return current != null && current.startsWith(context.packageName + "/")
}

internal fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun SetupScreen(app: OpenEarApp, resumeTick: Int, modifier: Modifier, onOpenModels: () -> Unit, onOpenTest: () -> Unit) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    // Reading these ticks makes the checklist re-evaluate after returning from system screens.
    @Suppress("UNUSED_VARIABLE") val refresh = resumeTick + permissionTick
    val enabled = isKeyboardEnabled(context)
    val selected = isKeyboardSelected(context)
    val mic = hasMicPermission(context)
    val modelReady = app.modelStore.resolve(app.settings.modelId) != null

    ScreenColumn(modifier) {
        Title("OpenEar")
        Body("Private, offline dictation keyboard, tuned for English. Speech is recognised on your phone; your audio is never uploaded.")
        Small("OpenEar is a keyboard. Enabling it and allowing the microphone are two separate steps. The microphone is used only while you are actively dictating.")

        StatusRow(enabled, "1. Enable the OpenEar keyboard", "Enable") {
            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
        }
        StatusRow(selected, "2. Choose OpenEar as the current keyboard", "Choose") {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        StatusRow(mic, "3. Allow microphone access", "Allow") {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        StatusRow(modelReady, "4. Download a speech model (one-time, needs internet)", "Models") { onOpenModels() }
        StatusRow(false, "5. Try a test dictation", "Test") { onOpenTest() }

        if (!mic) {
            Small("If Android no longer shows the permission prompt, open app settings to allow the microphone.")
            androidx.compose.material3.TextButton(onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
                )
            }) { Text("Open app settings") }
        }
        Small("OpenEar cannot enable or select itself: Android requires you to do these steps. It may not work in password fields or in apps that restrict third-party keyboards.")
    }
}
