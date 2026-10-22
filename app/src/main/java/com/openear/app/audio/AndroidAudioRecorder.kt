package com.openear.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.openear.app.privacy.SafeLog

/**
 * AudioRecord-based recorder. Audio lives only in memory and is dropped on
 * stop/cancel. Tries 16 kHz first (what Whisper wants) and falls back to
 * 44.1/48 kHz with software resampling on devices that refuse it.
 */
class AndroidAudioRecorder(context: Context) : Recorder {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock = Any()

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var buffer: ShortArray = ShortArray(0)
    private var sampleRate = Pcm.TARGET_RATE
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var running = false
    @Volatile private var failed = false
    @Volatile private var written = 0

    override val isRecording: Boolean get() = running

    @SuppressLint("MissingPermission") // The caller checks RECORD_AUDIO before starting.
    override fun start(maxSeconds: Int, listener: RecorderListener): StartResult = synchronized(lock) {
        if (running) return StartResult.Failed(RecorderError.Busy)

        var created: AudioRecord? = null
        var usedRate = 0
        search@ for (rate in RATES) {
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) continue
            for (source in SOURCES) {
                val candidate = try {
                    AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4)
                } catch (e: IllegalArgumentException) {
                    null
                } catch (e: SecurityException) {
                    return StartResult.Failed(RecorderError.Unavailable)
                }
                if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                    created = candidate
                    usedRate = rate
                    break@search
                }
                candidate?.release()
            }
        }
        val rec = created ?: return StartResult.Failed(RecorderError.Unavailable)

        requestFocus(listener)

        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            rec.release()
            abandonFocus()
            return StartResult.Failed(RecorderError.Unavailable)
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            abandonFocus()
            return StartResult.Failed(RecorderError.Unavailable)
        }

        sampleRate = usedRate
        buffer = ShortArray(usedRate * maxSeconds.coerceAtLeast(1))
        written = 0
        failed = false
        running = true
        record = rec

        val worker = Thread({
            val chunk = ShortArray(2048)
            var limitHit = false
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n < 0) {
                    failed = true
                    break
                }
                if (n == 0) continue
                val room = buffer.size - written
                val take = minOf(n, room)
                System.arraycopy(chunk, 0, buffer, written, take)
                written += take
                if (take < n || written >= buffer.size) {
                    limitHit = true
                    break
                }
            }
            if (failed && running) listener.onInterrupted()
            else if (limitHit && running) listener.onLimitReached()
        }, "openear-record")
        thread = worker
        worker.start()
        StartResult.Started
    }

    override fun stop(): RecordedAudio? = synchronized(lock) {
        if (record == null) return null
        val interrupted = failed
        shutdown()
        if (interrupted) {
            buffer = ShortArray(0)
            return null
        }
        val floats = Pcm.toFloat(buffer, written)
        buffer = ShortArray(0)
        written = 0
        val out = if (sampleRate == Pcm.TARGET_RATE) floats else Pcm.resample(floats, sampleRate, Pcm.TARGET_RATE)
        RecordedAudio(out)
    }

    override fun cancel() {
        synchronized(lock) {
            shutdown()
            buffer = ShortArray(0)
            written = 0
        }
    }

    private fun shutdown() {
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(2000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        record?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                SafeLog.w(TAG, "stop failed")
            }
            it.release()
        }
        record = null
        abandonFocus()
    }

    private fun requestFocus(listener: RecorderListener) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    if (running) {
                        failed = true
                        listener.onInterrupted()
                    }
                }
            }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "OpenEarRecorder"
        val RATES = intArrayOf(16_000, 44_100, 48_000)
        val SOURCES = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
    }
}
