package com.hotscan.calc

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Logs the magnetometer (magnetic field magnitude in µT) every 10s. Earth's field is
 * roughly 25–65 µT, so large deviations suggest nearby metal, magnets, or electronics.
 * Those are flagged as ALERT lines. No permission required.
 */
class MagScanner(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 10_000
) : Scanner {

    private val period = intervalMs.coerceAtLeast(3_000)
    private val sm = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var latest = 0f
    private var baseline = -1f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
            latest = sqrt(x * x + y * y + z * z)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val emit = object : Runnable {
        override fun run() {
            emitReading()
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        if (sensor == null) { cb("MAG", "No magnetometer on this device.", false); return }
        running = true
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.post(emit)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(emit)
        try { sm.unregisterListener(listener) } catch (_: Exception) {}
    }

    private fun emitReading() {
        val mag = latest
        if (mag <= 0f) return
        baseline = if (baseline < 0f) mag else baseline * 0.9f + mag * 0.1f
        val line = "field=%.1f µT (baseline %.1f)".format(mag, baseline)
        val anomaly = mag < 20f || mag > 70f || abs(mag - baseline) > 30f
        if (anomaly) cb("ALERT", "⚠ magnetic anomaly: $line", true)
        else cb("MAG", line, false)
    }
}
