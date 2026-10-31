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
 * Logs a single-value environment sensor (barometer, light, proximity) every interval.
 * No permissions required.
 */
class GenericSensorScanner(
    context: Context,
    private val cb: SignalCallback,
    private val sensorType: Int,
    private val category: String,
    private val unit: String,
    intervalMs: Long = 10_000
) : Scanner {

    private val period = intervalMs.coerceAtLeast(3_000)
    private val sm = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(sensorType)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var latest = Float.NaN

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { latest = e.values[0] }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val emit = object : Runnable {
        override fun run() {
            if (!latest.isNaN()) cb(category, "%.2f %s".format(latest, unit), false)
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        if (sensor == null) { cb(category, "No $category sensor on this device.", false); return }
        running = true
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.post(emit)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(emit)
        try { sm.unregisterListener(listener) } catch (_: Exception) {}
    }
}

/**
 * Logs motion state from the accelerometer: magnitude (m/s²) and whether the phone is
 * stationary or moving, based on recent variance. No permissions required.
 */
class MotionScanner(
    context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 10_000
) : Scanner {

    private val period = intervalMs.coerceAtLeast(3_000)
    private val sm = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val window = ArrayDeque<Float>()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
            window.addLast(m)
            while (window.size > 50) window.removeFirst()
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val emit = object : Runnable {
        override fun run() {
            if (window.isNotEmpty()) {
                val mean = window.average().toFloat()
                val dev = window.map { abs(it - mean) }.average().toFloat()
                val state = if (dev < 0.3f) "stationary" else "moving"
                cb("MOTION", "%s  accel=%.2f m/s² (var %.2f)".format(state, mean, dev), false)
            }
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        if (sensor == null) { cb("MOTION", "No accelerometer on this device.", false); return }
        running = true
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.post(emit)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(emit)
        try { sm.unregisterListener(listener) } catch (_: Exception) {}
    }
}
