package com.openear.app.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.openear.app.MainActivity
import com.openear.app.MicPermissionActivity
import com.openear.app.OpenEarApp
import com.openear.app.audio.AndroidAudioRecorder

/**
 * The OpenEar keyboard. Microphone capture starts only when the user taps the
 * mic key and is cancelled whenever the keyboard or field goes away.
 */
class OpenEarIME : InputMethodService() {

    private lateinit var app: OpenEarApp
    private lateinit var recorder: AndroidAudioRecorder
    private lateinit var controller: DictationController
    private var keyboardView: DictationKeyboardView? = null

    // Bumped for each genuinely new input field (not for restarts of the same field).
    private var fieldEpoch = 0L
    private var restricted = true

    private val target = object : TextTarget {
        override fun fieldToken(): Long = fieldEpoch
        override fun isRestricted(): Boolean = restricted
        override fun insert(text: String, token: Long): Boolean {
            if (token != fieldEpoch) return false
            val ic = currentInputConnection ?: return false
            return ic.commitText(text, 1)
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as OpenEarApp
        recorder = AndroidAudioRecorder(this)
        controller = DictationController(
            recorder = recorder,
            transcriber = app.engine,
            settings = app.settings,
            models = app.modelProvider,
            permission = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            target = target,
            scope = app.appScope,
            onState = { state ->
                keyboardView?.render(state, modelStatus())
                if (state.error == DictationError.PermissionDenied) openPermissionScreen()
            },
        )
    }

    override fun onCreateInputView(): View {
        val view = DictationKeyboardView(this, app.settings.theme, object : DictationKeyboardView.Actions {
            override fun onMic() {
                haptic(keyboardView)
                controller.onMicPressed()
            }

            override fun onCancel() = controller.cancel()
            override fun onBackspace() {
                haptic(keyboardView)
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }

            override fun onSpace() {
                haptic(keyboardView)
                currentInputConnection?.commitText(" ", 1)
            }

            override fun onEnter() {
                haptic(keyboardView)
                // Only on the user's own tap; dictation never presses Enter or Send.
                if (!sendDefaultEditorAction(false)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }

            override fun onSwitchKeyboard() {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }

            override fun onOpenSettings() {
                startActivity(Intent(this@OpenEarIME, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        keyboardView = view
        view.render(controller.state, modelStatus())
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            fieldEpoch++
            controller.cancel()
        }
        restricted = attribute == null || FieldRules.isRestricted(attribute.inputType)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.render(controller.state, modelStatus())
    }

    override fun onFinishInput() {
        controller.cancel()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.cancel()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        controller.cancel()
        super.onWindowHidden()
    }

    override fun onDestroy() {
        controller.cancel()
        recorder.cancel()
        keyboardView = null
        super.onDestroy()
    }

    private fun modelStatus(): String {
        val ref = app.modelProvider.current()
        return if (ref != null) "Model: ${ref.label} · Ready" else "No model installed"
    }

    private fun openPermissionScreen() {
        startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun haptic(view: View?) {
        if (app.settings.haptics) view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
