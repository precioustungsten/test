package com.openear.app.privacy

import android.util.Log
import com.openear.app.BuildConfig

/**
 * Logging helper that cannot leak spoken or typed content. Callers pass event
 * names and numeric facts only. [describe] is the sanctioned way to mention
 * user text in a log line: it records the length and nothing else.
 */
object SafeLog {
    fun describe(text: CharSequence?): String = "<text length=${text?.length ?: 0}>"
    fun describeAudio(sampleCount: Int, rate: Int): String = "<audio samples=$sampleCount rate=$rate>"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }
}
