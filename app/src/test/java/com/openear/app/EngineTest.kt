package com.openear.app

import com.openear.app.transcription.FailureKind
import com.openear.app.transcription.NativeWhisper
import com.openear.app.transcription.TranscriptionEngine
import com.openear.app.transcription.TranscriptionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private class FakeNative : NativeWhisper {
    override var isAvailable = true
    var initResult = 7L
    var output: ByteArray? = "hello".toByteArray()
    var throwOnTranscribe: Throwable? = null
    val initCount = AtomicInteger()
    val freed = ArrayList<Long>()
    val started = CountDownLatch(1)
    @Volatile var blockUntilAbort = false
    private val aborted = CountDownLatch(1)

    override fun init(modelPath: String): Long { initCount.incrementAndGet(); return initResult }
    @Synchronized override fun free(handle: Long) { freed += handle }
    override fun abort(handle: Long) { aborted.countDown() }
    val audioCtxSeen = ArrayList<Int>()
    override fun timings(handle: Long): FloatArray? = floatArrayOf(1f, 20f, 30f)
    override fun transcribe(handle: Long, audio: FloatArray, threads: Int, language: String, audioCtx: Int): ByteArray? {
        audioCtxSeen += audioCtx
        started.countDown()
        throwOnTranscribe?.let { throw it }
        if (blockUntilAbort) {
            aborted.await(5, TimeUnit.SECONDS)
            return null
        }
        return output
    }
}

class EngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var model: String
    private lateinit var native: FakeNative
    private lateinit var engine: TranscriptionEngine
    private val audio = FloatArray(16_000) { 0.1f }

    @Before fun setUp() {
        model = tmp.newFile("model.bin").absolutePath
        native = FakeNative()
        engine = TranscriptionEngine(native, { 60_000L }, Executors.newSingleThreadScheduledExecutor { r -> Thread(r).apply { isDaemon = true } })
    }

    @After fun tearDown() = engine.shutdown()

    @Test fun successReturnsDecodedUtf8() = runBlocking {
        native.output = "आज office late".toByteArray(Charsets.UTF_8)
        val r = engine.transcribe(model, audio, "auto", 2)
        assertEquals(TranscriptionResult.Success("आज office late"), r)
    }

    @Test fun modelIsLoadedOnceAndReused() = runBlocking {
        engine.transcribe(model, audio, "auto", 2)
        engine.transcribe(model, audio, "auto", 2)
        assertEquals(1, native.initCount.get())
    }

    @Test fun preloadLoadsOnceAndTranscribeReusesIt() = runBlocking {
        engine.preload(model)
        engine.preload(model)
        val r = engine.transcribe(model, audio, "en", 2)
        assertEquals(TranscriptionResult.Success("hello"), r)
        assertEquals(1, native.initCount.get())
    }

    @Test fun preloadOfMissingModelIsHarmless() = runBlocking {
        engine.preload(File(tmp.root, "nope.bin").path)
        val r = engine.transcribe(File(tmp.root, "nope.bin").path, audio, "en", 2)
        assertEquals(TranscriptionResult.Failure(FailureKind.ModelMissing), r)
    }

    @Test fun fastModeShrinksTheEncoderWindowForShortClips() = runBlocking {
        engine.transcribe(model, audio, "en", 2) // 1 s of audio
        assertEquals(1, native.audioCtxSeen.size)
        assertTrue(native.audioCtxSeen[0] in 1..1499)
    }

    @Test fun fastModeOffUsesTheDefaultWindow() = runBlocking {
        val slow = TranscriptionEngine(native, { 60_000L }, Executors.newSingleThreadScheduledExecutor { r -> Thread(r).apply { isDaemon = true } }, { false })
        slow.transcribe(model, audio, "en", 2)
        assertEquals(listOf(0), native.audioCtxSeen)
        slow.shutdown()
    }

    @Test fun timingIsRecordedWithoutAnyText() = runBlocking {
        engine.transcribe(model, audio, "en", 2)
        val t = engine.lastTiming!!
        assertEquals(1000L, t.audioMs)
        assertEquals(20L, t.encodeMs)
        assertEquals(30L, t.decodeMs)
    }

    @Test fun emptyAudioIsInvalid() = runBlocking {
        val r = engine.transcribe(model, FloatArray(0), "auto", 2)
        assertEquals(TranscriptionResult.Failure(FailureKind.InvalidInput), r)
        assertEquals(0, native.initCount.get())
    }

    @Test fun missingNativeLibraryIsReported() = runBlocking {
        native.isAvailable = false
        assertEquals(TranscriptionResult.Failure(FailureKind.NativeUnavailable), engine.transcribe(model, audio, "auto", 2))
    }

    @Test fun missingModelFileIsReportedWithoutCallingNative() = runBlocking {
        val r = engine.transcribe(File(tmp.root, "nope.bin").path, audio, "auto", 2)
        assertEquals(TranscriptionResult.Failure(FailureKind.ModelMissing), r)
        assertEquals(0, native.initCount.get())
    }

    @Test fun modelLoadFailureIsReported() = runBlocking {
        native.initResult = 0L
        assertEquals(TranscriptionResult.Failure(FailureKind.ModelLoadFailed), engine.transcribe(model, audio, "auto", 2))
    }

    @Test fun nativeReturningNullIsAnInferenceFailure() = runBlocking {
        native.output = null
        assertEquals(TranscriptionResult.Failure(FailureKind.InferenceFailed), engine.transcribe(model, audio, "auto", 2))
    }

    @Test fun nativeExceptionsNeverEscape() = runBlocking {
        native.throwOnTranscribe = RuntimeException("boom")
        assertEquals(TranscriptionResult.Failure(FailureKind.InferenceFailed), engine.transcribe(model, audio, "auto", 2))
        assertEquals(listOf(7L), native.freed) // context dropped after a failure
    }

    @Test fun outOfMemoryMapsToItsOwnFailure() = runBlocking {
        native.throwOnTranscribe = OutOfMemoryError()
        assertEquals(TranscriptionResult.Failure(FailureKind.OutOfMemory), engine.transcribe(model, audio, "auto", 2))
    }

    @Test fun cancelDiscardsTheResult() = runBlocking {
        native.blockUntilAbort = true
        val deferred = async(kotlinx.coroutines.Dispatchers.Default) { engine.transcribe(model, audio, "auto", 2) }
        assertTrue(native.started.await(5, TimeUnit.SECONDS))
        engine.cancel()
        assertEquals(TranscriptionResult.Cancelled, deferred.await())
    }

    @Test fun releaseFreesExactlyOnceEvenIfCalledRepeatedly() = runBlocking {
        engine.transcribe(model, audio, "auto", 2)
        engine.release()
        engine.release()
        engine.shutdown() // also tearDown(); must not double free
        Thread.sleep(200)
        assertEquals(listOf(7L), native.freed)
    }

    @Test fun secondConcurrentRunIsRejectedAsBusy() = runBlocking {
        native.blockUntilAbort = true
        val first = async(kotlinx.coroutines.Dispatchers.Default) { engine.transcribe(model, audio, "auto", 2) }
        assertTrue(native.started.await(5, TimeUnit.SECONDS))
        assertEquals(TranscriptionResult.Failure(FailureKind.Busy), engine.transcribe(model, audio, "auto", 2))
        engine.cancel()
        first.await()
        Unit
    }
}
