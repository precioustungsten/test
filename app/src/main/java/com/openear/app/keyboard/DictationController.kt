package com.openear.app.keyboard

import com.openear.app.audio.Pcm
import com.openear.app.audio.RecordedAudio
import com.openear.app.audio.Recorder
import com.openear.app.audio.RecorderListener
import com.openear.app.audio.StartResult
import com.openear.app.settings.Settings
import com.openear.app.transcription.FailureKind
import com.openear.app.transcription.TranscriptFormatter
import com.openear.app.transcription.Transcriber
import com.openear.app.transcription.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where recognised text goes. The IME implements this over InputConnection. */
interface TextTarget {
    /** Identifies the focused field; changes when the user moves to a different field/app. */
    fun fieldToken(): Long
    fun isRestricted(): Boolean

    /** Inserts text into the field identified by [token]. Returns false if it could not be inserted. */
    fun insert(text: String, token: Long): Boolean
}

data class ModelRef(val id: String, val label: String, val path: String)

fun interface ModelProvider {
    fun current(): ModelRef?
}

fun interface PermissionChecker {
    fun hasMicPermission(): Boolean
}

/**
 * The record → transcribe → insert state machine shared by the keyboard and the
 * test screen. Every dictation is a numbered session; results that arrive for a
 * session that was cancelled, replaced, or already completed are ignored, which
 * is what prevents duplicate or misplaced insertion. All calls must be made on
 * the main thread.
 */
class DictationController(
    private val recorder: Recorder,
    private val transcriber: Transcriber,
    private val settings: Settings,
    private val models: ModelProvider,
    private val permission: PermissionChecker,
    private val target: TextTarget,
    private val scope: CoroutineScope,
    private val threads: Int = defaultThreads(),
    private val onState: (DictationState) -> Unit,
) {
    var state: DictationState = DictationState()
        private set

    private var sessionId = 0L
    private var sessionToken = 0L
    private var sessionModel: ModelRef? = null
    private var job: Job? = null

    fun onMicPressed() {
        when (state.phase) {
            DictationPhase.Listening -> finishRecording()
            DictationPhase.Transcribing -> Unit // already busy: ignore extra taps
            else -> startRecording()
        }
    }

    /** Cancel from the user, keyboard dismissal, field change, or lifecycle end. Idempotent. */
    fun cancel() {
        val wasActive = state.phase == DictationPhase.Listening || state.phase == DictationPhase.Transcribing
        if (state.phase == DictationPhase.Listening) recorder.cancel()
        if (state.phase == DictationPhase.Transcribing) transcriber.cancel()
        sessionId++ // invalidates any in-flight result
        job?.cancel()
        job = null
        if (wasActive || state.phase != DictationPhase.Idle) publish(DictationState())
    }

    private fun startRecording() {
        if (target.isRestricted()) return fail(DictationError.Restricted)
        if (!permission.hasMicPermission()) return fail(DictationError.PermissionDenied)
        val model = models.current() ?: return fail(DictationError.ModelMissing)

        val id = ++sessionId
        val listener = object : RecorderListener {
            override fun onLimitReached() {
                scope.launch { if (id == sessionId && state.phase == DictationPhase.Listening) finishRecording() }
            }

            override fun onInterrupted() {
                scope.launch {
                    if (id == sessionId && state.phase == DictationPhase.Listening) {
                        recorder.cancel()
                        sessionId++
                        fail(DictationError.Interrupted)
                    }
                }
            }
        }
        when (recorder.start(settings.maxRecordSeconds, listener)) {
            is StartResult.Started -> {
                sessionToken = target.fieldToken()
                sessionModel = model
                // Load the model while the user is still speaking, so it is ready at stop.
                transcriber.preload(model.path)
                publish(DictationState(DictationPhase.Listening))
            }
            is StartResult.Failed -> {
                sessionId++
                fail(DictationError.MicUnavailable)
            }
        }
    }

    private fun finishRecording() {
        val audio: RecordedAudio? = recorder.stop()
        if (audio == null) {
            sessionId++
            return fail(DictationError.Interrupted)
        }
        if (!Pcm.looksLikeSpeech(audio.samples)) {
            sessionId++
            return fail(DictationError.NoSpeech)
        }
        val model = sessionModel
        if (model == null) {
            sessionId++
            return fail(DictationError.ModelMissing)
        }
        val id = sessionId
        publish(DictationState(DictationPhase.Transcribing))
        job = scope.launch {
            val result = transcriber.transcribe(model.path, audio.samples, settings.languageMode.code, effectiveThreads())
            onResult(id, result)
        }
    }

    private fun onResult(id: Long, result: TranscriptionResult) {
        // Ignore stale, cancelled or repeated completions.
        if (id != sessionId || state.phase != DictationPhase.Transcribing) return
        when (result) {
            is TranscriptionResult.Cancelled -> publish(DictationState())
            is TranscriptionResult.Failure -> fail(result.kind.toError())
            is TranscriptionResult.Success -> {
                val text = TranscriptFormatter.format(result.rawText, settings.trailingSpace)
                    ?: return fail(DictationError.NoSpeech)
                if (target.fieldToken() != sessionToken) return fail(DictationError.FieldGone)
                if (target.insert(text, sessionToken)) {
                    sessionId++ // this session is consumed; nothing can insert it again
                    publish(DictationState(DictationPhase.Inserted))
                } else {
                    fail(DictationError.FieldGone)
                }
            }
        }
    }

    private fun effectiveThreads(): Int = settings.threads.takeIf { it > 0 } ?: threads

    private fun FailureKind.toError(): DictationError = when (this) {
        FailureKind.ModelMissing -> DictationError.ModelMissing
        FailureKind.ModelLoadFailed -> DictationError.ModelProblem
        FailureKind.OutOfMemory -> DictationError.LowMemory
        FailureKind.Busy -> DictationError.Busy
        FailureKind.InvalidInput -> DictationError.NoSpeech
        FailureKind.NativeUnavailable, FailureKind.InferenceFailed -> DictationError.TranscriptionFailed
    }

    private fun fail(error: DictationError) = publish(DictationState(DictationPhase.Error, error))

    private fun publish(newState: DictationState) {
        state = newState
        onState(newState)
    }

    companion object {
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    }
}
