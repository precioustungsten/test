package com.openear.app.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.openear.app.R
import com.openear.app.settings.ThemeMode

/**
 * The keyboard surface: monochrome, large touch targets, state shown as text
 * (never colour alone), plain Views for a light and stable IME.
 */
class DictationKeyboardView(
    context: Context,
    themeMode: ThemeMode,
    private val actions: Actions,
) : LinearLayout(context) {

    interface Actions {
        fun onMic()
        fun onCancel()
        fun onBackspace()
        fun onSpace()
        fun onEnter()
        fun onSwitchKeyboard()
        fun onOpenSettings()
    }

    private val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    private val bg = if (dark) Color.parseColor("#111111") else Color.parseColor("#F2F2F2")
    private val fg = if (dark) Color.WHITE else Color.BLACK
    private val surface = if (dark) Color.parseColor("#2B2B2B") else Color.WHITE
    private val outline = if (dark) Color.parseColor("#8A8A8A") else Color.parseColor("#6B6B6B")
    private val errorColor = if (dark) Color.parseColor("#FF8A80") else Color.parseColor("#B3261E")
    private val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private val modelText = TextView(context)
    private val stateText = TextView(context)
    private val micButton = ImageButton(context)
    private val cancelButton = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(bg)
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)

        val statusRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modelText.apply {
            textSize = 13f
            setTextColor(fg)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val settingsButton = key("Settings", "Open OpenEar settings") { actions.onOpenSettings() }
        statusRow.addView(modelText)
        statusRow.addView(settingsButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)))
        addView(statusRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        stateText.apply {
            textSize = 17f
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = dp(if (landscape) 32 else 52)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(stateText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val micSize = dp(if (landscape) 64 else 88)
        micButton.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fg) }
            setImageResource(R.drawable.ic_mic)
            imageTintList = android.content.res.ColorStateList.valueOf(bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setOnClickListener { actions.onMic() }
        }
        addView(micButton, LayoutParams(micSize, micSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
            bottomMargin = dp(4)
        })

        cancelButton.apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            contentDescription = "Cancel dictation"
            isClickable = true
            isFocusable = true
            background = rounded(surface, outline)
            setOnClickListener { actions.onCancel() }
            visibility = View.GONE
        }
        addView(cancelButton, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(6) })

        val keys = LinearLayout(context).apply { orientation = HORIZONTAL }
        val lp = { w: Float -> LayoutParams(0, dp(48), w).apply { marginStart = dp(3); marginEnd = dp(3) } }
        keys.addView(key("Switch", "Switch keyboard") { actions.onSwitchKeyboard() }, lp(1.2f))
        keys.addView(key("Delete", "Delete previous character") { actions.onBackspace() }, lp(1.2f))
        keys.addView(key("Space", "Space") { actions.onSpace() }, lp(2f))
        keys.addView(key("Enter", "Enter") { actions.onEnter() }, lp(1.2f))
        addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun render(state: DictationState, modelStatus: String) {
        modelText.text = modelStatus
        stateText.text = state.label
        stateText.setTextColor(if (state.phase == DictationPhase.Error) errorColor else fg)
        val listening = state.phase == DictationPhase.Listening
        val busy = state.phase == DictationPhase.Transcribing
        micButton.setImageResource(if (listening) R.drawable.ic_stop else R.drawable.ic_mic)
        micButton.contentDescription = when {
            listening -> "Stop recording and transcribe"
            busy -> "Transcribing, please wait"
            else -> "Start dictation"
        }
        micButton.alpha = if (busy) 0.5f else 1f
        cancelButton.visibility = if (listening || busy) View.VISIBLE else View.GONE
    }

    private fun key(label: String, description: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 15f
        setTextColor(fg)
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minWidth = dp(48)
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = rounded(surface, outline)
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
