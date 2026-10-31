package com.hotscan.calc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns the scanners, logger, battery guard and session summary.
 * Which scanners run is decided by the control-panel toggles. All results are written to
 * the local log file only; getting data off the phone is done by the user via the in-app
 * log viewer (share/export).
 */
class ScanService : Service() {

    data class Line(val category: String, val text: String, val alert: Boolean)

    inner class LocalBinder : Binder() { val service: ScanService get() = this@ScanService }

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var prefs: Prefs
    private var logger: SignalLogger? = null
    private val scanners = ArrayList<Scanner>()
    private var running = false
    private var paused = false

    private val buffer = ArrayList<Line>()
    private val maxBuffer = 1000
    var listener: ((Line) -> Unit)? = null

    // dedup + summary state
    private val dedupSeen = HashSet<String>()
    private val counts = HashMap<String, Int>()
    private var alertCount = 0
    private var startedAt = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (!prefs.flag(Prefs.F_BATTGUARD) || !running) return
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val threshold = prefs.batteryThreshold
            if (!paused && pct < threshold) pauseScanners(pct)
            else if (paused && pct >= threshold + 5) resumeScanners(pct)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopEverything(); return START_NOT_STICKY }
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            // e.g. location FGS type needs the permission granted first (Android 14+).
            stopSelf(); return START_NOT_STICKY
        }
        if (!running) startScanning()
        return START_STICKY
    }

    private fun emit(category: String, line: String, alert: Boolean) {
        val log = logger ?: return
        val key = "$category|$line"
        val dedup = prefs.flag(Prefs.F_DEDUP)
        if (dedup && !alert && !dedupSeen.add(key)) return
        log.log(category, line)
        main.post {
            counts[category] = (counts[category] ?: 0) + 1
            if (alert) alertCount++
            val item = Line(category, line, alert)
            synchronized(buffer) {
                buffer.add(item)
                while (buffer.size > maxBuffer) buffer.removeAt(0)
            }
            listener?.invoke(item)
        }
    }

    private fun startScanning() {
        val log = SignalLogger(this, prefs.logFileName)
        log.writeHeader()
        logger = log
        dedupSeen.clear(); counts.clear(); alertCount = 0
        startedAt = System.currentTimeMillis()

        buildScanners()
        scanners.forEach { it.start() }
        running = true
        emit("SYS", "Background scanning started → ${log.path}", false)
    }

    private val cb: SignalCallback = { category, line, alert -> emit(category, line, alert) }

    private fun buildScanners() {
        scanners.clear()
        val iv = prefs.intervalMs
        if (prefs.flag(Prefs.F_WIFI)) scanners += WifiScanner(this, cb, iv)
        if (prefs.flag(Prefs.F_BT)) scanners += BtScanner(
            this, cb, iv, prefs.flag(Prefs.F_TRACKER), prefs.flag(Prefs.F_FOLLOWER)
        )
        if (prefs.flag(Prefs.F_CELL)) scanners += CellScanner(this, cb, iv, prefs.flag(Prefs.F_ROGUE))
        if (prefs.flag(Prefs.F_GNSS)) scanners += GnssScanner(this, cb, iv)
        if (prefs.flag(Prefs.F_TRAIL)) scanners += LocationTrail(
            this, cb, iv, prefs.flag(Prefs.F_AWAY), prefs.homeLat, prefs.homeLon
        )
        if (prefs.flag(Prefs.F_MAG)) scanners += MagScanner(this, cb, iv)
        if (prefs.flag(Prefs.F_BARO))
            scanners += GenericSensorScanner(this, cb, Sensor.TYPE_PRESSURE, "BARO", "hPa", iv)
        if (prefs.flag(Prefs.F_LIGHT))
            scanners += GenericSensorScanner(this, cb, Sensor.TYPE_LIGHT, "LIGHT", "lx", iv)
        if (prefs.flag(Prefs.F_PROX))
            scanners += GenericSensorScanner(this, cb, Sensor.TYPE_PROXIMITY, "PROX", "cm", iv)
        if (prefs.flag(Prefs.F_MOTION)) scanners += MotionScanner(this, cb, iv)
        if (prefs.flag(Prefs.F_MIC)) scanners += MicScanner(this, cb, iv)
    }

    private fun pauseScanners(pct: Int) {
        scanners.forEach { it.stop() }
        paused = true
        emit("SYS", "Battery $pct% — scanning paused (battery guard)", true)
    }

    private fun resumeScanners(pct: Int) {
        buildScanners()
        scanners.forEach { it.start() }
        paused = false
        emit("SYS", "Battery $pct% — scanning resumed", false)
    }

    private fun writeSummary() {
        if (!prefs.flag(Prefs.F_SUMMARY)) return
        val log = logger ?: return
        val mins = (System.currentTimeMillis() - startedAt) / 60_000.0
        val breakdown = counts.entries.sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${it.value}" }
        log.log("SUMMARY", "ran %.1f min; alerts=%d; %s".format(mins, alertCount, breakdown))
    }

    private fun stopEverything() {
        writeSummary()
        scanners.forEach { it.stop() }
        scanners.clear()
        running = false; paused = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        scanners.forEach { it.stop() }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        running = false
        super.onDestroy()
    }

    fun isRunning() = running
    /** Allows the foreground UI to push events (e.g. NFC taps) into the same log/stream. */
    fun external(category: String, text: String, alert: Boolean) = emit(category, text, alert)
    fun snapshot(): List<Line> = synchronized(buffer) { ArrayList(buffer) }
    fun clearBuffer() = synchronized(buffer) { buffer.clear() }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Background service", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calculator")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scan_service"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.hotscan.calc.STOP"
    }
}
