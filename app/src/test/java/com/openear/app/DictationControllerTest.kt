package com.openear.app

import com.openear.app.audio.RecordedAudio
import com.openear.app.audio.RecorderError
import com.openear.app.keyboard.DictationController
import com.openear.app.keyboard.DictationError
import com.openear.app.keyboard.DictationPhase
import com.openear.app.keyboard.DictationState
import com.openear.app.keyboard.ModelRef
import com.openear.app.settings.Settings
import com.openear.app.transcription.FailureKind
import com.openear.app.transcription.TranscriptionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationControllerTest {
    private val scope = TestScope()
    private val recorder = FakeRecorder()
    private val transcriber = FakeTranscriber()
    private val target = FakeTarget()
    private val settings = Settings(MemoryStore())
    private var hasPermission = true
    private var model: ModelRef? = ModelRef("base", "Base", "/models/base.bin")
    private val states = ArrayList<DictationState>()
    private lateinit var c: DictationController

    @Before fun setUp() {
        c = DictationController(
            recorder = recorder,
            transcriber = transcriber,
            settings = settings,
            models = { model },
            permission = { hasPermission },
            target = target,
            scope = scope,
            threads = 2,
            onState = { states += it },
        )
    }

    private fun speak() { c.onMicPressed(); c.onMicPressed() } // start, then stop
    private val phase get() = c.state.phase

    @Test fun happyPathInsertsExactlyOnce() {
        speak()
        assertEquals(DictationPhase.Transcribing, phase)
        scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Success(" namaste duniya "))
        scope.advanceUntilIdle()
        assertEquals(DictationPhase.Inserted, phase)
        assertEquals(listOf("namaste duniya "), target.inserted)
    }

    @Test fun modelIsPreloadedWhileRecordingStarts() {
        c.onMicPressed()
        assertEquals(listOf("/models/base.bin"), transcriber.preloaded)
    }

    @Test fun noPreloadWhenRecordingCannotStart() {
        hasPermission = false
        c.onMicPressed()
        assertTrue(transcriber.preloaded.isEmpty())
    }

    @Test fun threadSettingOverridesAutomatic() {
        settings.threads = 6
        speak(); scope.advanceUntilIdle()
        assertEquals(listOf(6), transcriber.threadsUsed)
    }

    @Test fun automaticThreadsUseTheConfiguredDefault() {
        speak(); scope.advanceUntilIdle()
        assertEquals(listOf(2), transcriber.threadsUsed)
    }

    @Test fun trailingSpaceSettingIsHonoured() {
        settings.trailingSpace = false
        speak(); scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Success("hello"))
        scope.advanceUntilIdle()
        assertEquals(listOf("hello"), target.inserted)
    }

    @Test fun permissionDeniedNeverStartsTheMicrophone() {
        hasPermission = false
        c.onMicPressed()
        assertEquals(DictationError.PermissionDenied, c.state.error)
        assertEquals(0, recorder.startCount)
    }

    @Test fun missingModelBlocksRecording() {
        model = null
        c.onMicPressed()
        assertEquals(DictationError.ModelMissing, c.state.error)
        assertEquals(0, recorder.startCount)
    }

    @Test fun passwordFieldsAreRefused() {
        target.restricted = true
        c.onMicPressed()
        assertEquals(DictationError.Restricted, c.state.error)
        assertEquals(0, recorder.startCount)
    }

    @Test fun busyMicrophoneIsReported() {
        recorder.startError = RecorderError.Unavailable
        c.onMicPressed()
        assertEquals(DictationError.MicUnavailable, c.state.error)
    }

    @Test fun cancelWhileListeningDiscardsAudio() {
        c.onMicPressed()
        c.cancel()
        assertEquals(DictationPhase.Idle, phase)
        assertEquals(1, recorder.cancelCount)
        assertTrue(transcriber.pending.isEmpty())
        assertTrue(target.inserted.isEmpty())
    }

    @Test fun cancelWhileTranscribingIgnoresTheLateResult() {
        speak(); scope.advanceUntilIdle()
        c.cancel()
        assertEquals(1, transcriber.cancelCount)
        transcriber.pending[0].complete(TranscriptionResult.Success("late text"))
        scope.advanceUntilIdle()
        assertEquals(DictationPhase.Idle, phase)
        assertTrue(target.inserted.isEmpty())
    }

    @Test fun staleResultFromAnOldSessionNeverInserts() {
        speak(); scope.advanceUntilIdle()   // session A
        c.cancel()
        speak(); scope.advanceUntilIdle()   // session B
        transcriber.pending[0].complete(TranscriptionResult.Success("from A"))
        scope.advanceUntilIdle()
        assertTrue("A's result must be ignored", target.inserted.isEmpty())
        transcriber.pending[1].complete(TranscriptionResult.Success("from B"))
        scope.advanceUntilIdle()
        assertEquals(listOf("from B "), target.inserted)
    }

    @Test fun movingToAnotherFieldPreventsInsertion() {
        speak(); scope.advanceUntilIdle()
        target.token = 2L // user switched fields/apps while transcribing
        transcriber.pending[0].complete(TranscriptionResult.Success("hello"))
        scope.advanceUntilIdle()
        assertEquals(DictationError.FieldGone, c.state.error)
        assertTrue(target.inserted.isEmpty())
    }

    @Test fun failedInsertIsReportedNotSilentlyDropped() {
        target.acceptInsert = false
        speak(); scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Success("hello"))
        scope.advanceUntilIdle()
        assertEquals(DictationError.FieldGone, c.state.error)
    }

    @Test fun silenceIsNoSpeechAndNeverTranscribed() {
        recorder.audio = RecordedAudio(FloatArray(16_000))
        speak(); scope.advanceUntilIdle()
        assertEquals(DictationError.NoSpeech, c.state.error)
        assertTrue(transcriber.pending.isEmpty())
    }

    @Test fun blankMarkerResultInsertsNothing() {
        speak(); scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Success("[BLANK_AUDIO]"))
        scope.advanceUntilIdle()
        assertEquals(DictationError.NoSpeech, c.state.error)
        assertTrue(target.inserted.isEmpty())
    }

    @Test fun interruptedRecordingIsReported() {
        recorder.audio = null
        speak()
        assertEquals(DictationError.Interrupted, c.state.error)
    }

    @Test fun nativeFailureShowsAMessageAndInsertsNothing() {
        speak(); scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Failure(FailureKind.InferenceFailed))
        scope.advanceUntilIdle()
        assertEquals(DictationError.TranscriptionFailed, c.state.error)
        assertTrue(target.inserted.isEmpty())
    }

    @Test fun lowMemoryHasItsOwnMessage() {
        speak(); scope.advanceUntilIdle()
        transcriber.pending[0].complete(TranscriptionResult.Failure(FailureKind.OutOfMemory))
        scope.advanceUntilIdle()
        assertEquals(DictationError.LowMemory, c.state.error)
    }

    @Test fun tappingWhileTranscribingDoesNotStartASecondRun() {
        speak(); scope.advanceUntilIdle()
        c.onMicPressed()
        c.onMicPressed()
        scope.advanceUntilIdle()
        assertEquals(1, transcriber.pending.size)
        assertEquals(1, recorder.startCount)
    }

    @Test fun cancelIsIdempotent() {
        c.cancel(); c.cancel()
        assertEquals(DictationPhase.Idle, phase)
        speak(); scope.advanceUntilIdle()
        c.cancel(); c.cancel()
        assertEquals(DictationPhase.Idle, phase)
    }

    @Test fun recorderLimitFinishesTheRecordingOnce() {
        c.onMicPressed()
        recorder.listener!!.onLimitReached()
        scope.advanceUntilIdle()
        assertEquals(DictationPhase.Transcribing, phase)
        recorder.listener!!.onLimitReached() // duplicate callback
        scope.advanceUntilIdle()
        assertEquals(1, transcriber.pending.size)
    }

    @Test fun interruptionDuringRecordingStopsAndExplains() {
        c.onMicPressed()
        recorder.listener!!.onInterrupted()
        scope.advanceUntilIdle()
        assertEquals(DictationError.Interrupted, c.state.error)
        assertEquals(1, recorder.cancelCount)
    }
}
