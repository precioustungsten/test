package com.openear.app.transcription

import com.openear.app.privacy.SafeLog
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the native whisper context. All native calls (load, run, free) happen on
 * one dedicated thread, which also makes double-free and use-after-free
 * impossible: the handle is only ever touched there. The model is loaded lazily
 * (or earlier via [preload], while the user is still speaking), reused while
 * active, and released after an idle period.
 */
class TranscriptionEngine(
    private val native: NativeWhisper = JniWhisper,
    private val idleMillis: () -> Long = { 120_000L },
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "openear-infer").apply { isDaemon = true } },
    private val fastMode: () -> Boolean = { true },
) : Transcriber {

    private val dispatcher = executor.asCoroutineDispatcher()
    private val busy = AtomicBoolean(false)

    // Written only on the inference thread; volatile so cancel() can read it from anywhere.
    @Volatile private var handle = 0L
    private var loadedPath: String? = null
    @Volatile private var cancelRequested = false
    private var idleFuture: ScheduledFuture<*>? = null
    private var lastLoadMs = 0L

    /** Timing of the most recent successful transcription (numbers only, no content). */
    @Volatile var lastTiming: TranscriptionTiming? = null
        private set

    override suspend fun transcribe(
        modelPath: String,
        audio: FloatArray,
        language: String,
        threads: Int,
    ): TranscriptionResult {
        if (audio.isEmpty()) return TranscriptionResult.Failure(FailureKind.InvalidInput)
        if (!native.isAvailable) return TranscriptionResult.Failure(FailureKind.NativeUnavailable)
        if (!busy.compareAndSet(false, true)) return TranscriptionResult.Failure(FailureKind.Busy)
        cancelRequested = false
        try {
            return withContext(dispatcher) { runOnInferenceThread(modelPath, audio, language, threads) }
        } finally {
            busy.set(false)
        }
    }

    override fun preload(modelPath: String) {
        if (!native.isAvailable || executor.isShutdown) return
        executor.execute {
            idleFuture?.cancel(false)
            try {
                ensureLoaded(modelPath)
            } catch (e: Throwable) {
                // transcribe() will surface a real failure to the user if loading is still broken.
            } finally {
                scheduleIdleRelease()
            }
        }
    }

    /** Inference thread only. Returns null on success or the failure kind. */
    private fun ensureLoaded(modelPath: String): FailureKind? {
        if (handle != 0L && loadedPath == modelPath) return null
        freeNow()
        if (!File(modelPath).isFile) return FailureKind.ModelMissing
        val start = System.nanoTime()
        val h = native.init(modelPath)
        if (h == 0L) return FailureKind.ModelLoadFailed
        handle = h
        loadedPath = modelPath
        lastLoadMs = (System.nanoTime() - start) / 1_000_000
        SafeLog.d(TAG, "model loaded in ${lastLoadMs} ms")
        return null
    }

    private fun runOnInferenceThread(
        modelPath: String,
        audio: FloatArray,
        language: String,
        threads: Int,
    ): TranscriptionResult {
        idleFuture?.cancel(false)
        try {
            val wasLoaded = handle != 0L && loadedPath == modelPath
            val t0 = System.nanoTime()
            ensureLoaded(modelPath)?.let { return TranscriptionResult.Failure(it) }
            val loadMs = if (wasLoaded) 0L else lastLoadMs
            if (cancelRequested) return TranscriptionResult.Cancelled

            val ctx = DecodeTuning.audioCtxFor(audio.size, fastMode())
            val inferStart = System.nanoTime()
            val bytes = native.transcribe(handle, audio, threads, language, ctx)
            val inferMs = (System.nanoTime() - inferStart) / 1_000_000
            if (cancelRequested) return TranscriptionResult.Cancelled
            if (bytes == null) return TranscriptionResult.Failure(FailureKind.InferenceFailed)

            val phases = try { native.timings(handle) } catch (e: Throwable) { null }
            lastTiming = TranscriptionTiming(
                audioMs = audio.size * 1000L / 16_000,
                loadMs = loadMs,
                totalMs = (System.nanoTime() - t0) / 1_000_000,
                encodeMs = phases?.getOrNull(1)?.toLong(),
                decodeMs = phases?.getOrNull(2)?.toLong(),
            )
            SafeLog.d(TAG, "transcribed audioMs=${lastTiming?.audioMs} inferMs=$inferMs ctx=$ctx threads=$threads")
            return TranscriptionResult.Success(String(bytes, Charsets.UTF_8))
        } catch (e: OutOfMemoryError) {
            freeNow()
            return TranscriptionResult.Failure(FailureKind.OutOfMemory)
        } catch (e: Throwable) {
            // Includes UnsatisfiedLinkError and any unexpected native-bridge failure.
            freeNow()
            return TranscriptionResult.Failure(FailureKind.InferenceFailed)
        } finally {
            scheduleIdleRelease()
        }
    }

    override fun cancel() {
        cancelRequested = true
        val h = handle
        if (h != 0L) {
            try {
                native.abort(h)
            } catch (e: Throwable) {
                // Nothing to do: the result is discarded via cancelRequested anyway.
            }
        }
    }

    override fun release() {
        cancel()
        if (!executor.isShutdown) executor.execute { freeNow() }
    }

    private fun scheduleIdleRelease() {
        idleFuture?.cancel(false)
        if (executor.isShutdown) return
        idleFuture = executor.schedule({ freeNow() }, idleMillis(), TimeUnit.MILLISECONDS)
    }

    /** Inference thread only. Frees the context exactly once. */
    private fun freeNow() {
        val h = handle
        handle = 0L
        loadedPath = null
        if (h != 0L) {
            try {
                native.free(h)
            } catch (e: Throwable) {
                // The handle is already forgotten, so it can never be freed twice.
            }
        }
    }

    fun shutdown() {
        release()
        // Drop the pending idle-release timer so no delayed task outlives the engine.
        (executor as? java.util.concurrent.ScheduledThreadPoolExecutor)
            ?.setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        executor.shutdown()
    }

    private companion object {
        const val TAG = "OpenEarEngine"
    }
}
