package com.hotscan.calc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Ambient sound-LEVEL meter. Reads the microphone, computes RMS amplitude, and logs a
 * relative level in dBFS every interval. It does NOT record or store audio — only the
 * numeric level. Requires RECORD_AUDIO.
 */
@SuppressLint("MissingPermission")
class MicScanner(
    @Suppress("unused") context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 10_000
) : Scanner {

    private val period = intervalMs.coerceAtLeast(2_000)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var lastDb = Double.NaN

    private val sampleRate = 16_000

    private val emit = object : Runnable {
        override fun run() {
            if (!lastDb.isNaN()) cb("MIC", "level=%.1f dBFS".format(lastDb), false)
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) { cb("MIC", "Microphone unavailable.", false); return }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (_: Exception) {
            cb("MIC", "Missing RECORD_AUDIO permission.", false); return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            cb("MIC", "Microphone could not be initialized.", false); return
        }
        record = rec
        running = true
        try { rec.startRecording() } catch (_: Exception) {
            cb("MIC", "Could not start recording.", false); stop(); return
        }
        thread = Thread {
            val buf = ShortArray(minBuf)
            while (running) {
                val n = try { rec.read(buf, 0, buf.size) } catch (_: Exception) { -1 }
                if (n > 0) {
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i].toDouble() * buf[i].toDouble()
                    val rms = sqrt(sum / n)
                    lastDb = if (rms > 0) 20 * log10(rms / 32767.0) else -120.0
                }
            }
        }.also { it.start() }
        handler.post(emit)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(emit)
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        thread = null
    }
}
