package com.openear.app

import com.openear.app.audio.RecordedAudio
import com.openear.app.audio.Recorder
import com.openear.app.audio.RecorderError
import com.openear.app.audio.RecorderListener
import com.openear.app.audio.StartResult
import com.openear.app.keyboard.TextTarget
import com.openear.app.settings.KeyValueStore
import com.openear.app.transcription.Transcriber
import com.openear.app.transcription.TranscriptionResult
import kotlinx.coroutines.CompletableDeferred

class MemoryStore : KeyValueStore {
    val map = HashMap<String, Any>()
    override fun getString(key: String, default: String?) = map[key] as String? ?: default
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getBoolean(key: String, default: Boolean) = map[key] as Boolean? ?: default
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, default: Int) = map[key] as Int? ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun clear() = map.clear()
}

class FakeRecorder : Recorder {
    var startError: RecorderError? = null
    var audio: RecordedAudio? = RecordedAudio(FloatArray(16_000) { 0.1f })
    var cancelCount = 0
    var startCount = 0
    private var recording = false
    var listener: RecorderListener? = null
    override val isRecording get() = recording
    override fun start(maxSeconds: Int, listener: RecorderListener): StartResult {
        startCount++
        startError?.let { return StartResult.Failed(it) }
        this.listener = listener
        recording = true
        return StartResult.Started
    }
    override fun stop(): RecordedAudio? { recording = false; return audio }
    override fun cancel() { recording = false; cancelCount++ }
}

class FakeTarget : TextTarget {
    var token = 1L
    var restricted = false
    var acceptInsert = true
    val inserted = ArrayList<String>()
    override fun fieldToken() = token
    override fun isRestricted() = restricted
    override fun insert(text: String, token: Long): Boolean {
        if (!acceptInsert) return false
        inserted += text
        return true
    }
}

/** Each transcribe() call waits on its own deferred so tests control completion order. */
class FakeTranscriber : Transcriber {
    val pending = ArrayList<CompletableDeferred<TranscriptionResult>>()
    var cancelCount = 0
    var releaseCount = 0
    val preloaded = ArrayList<String>()
    val threadsUsed = ArrayList<Int>()
    override fun preload(modelPath: String) { preloaded += modelPath }
    override suspend fun transcribe(modelPath: String, audio: FloatArray, language: String, threads: Int): TranscriptionResult {
        val d = CompletableDeferred<TranscriptionResult>()
        threadsUsed += threads
        pending += d
        return d.await()
    }
    override fun cancel() { cancelCount++ }
    override fun release() { releaseCount++ }
}
