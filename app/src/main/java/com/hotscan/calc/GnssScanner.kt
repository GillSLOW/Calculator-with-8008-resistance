package com.hotscan.calc

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/**
 * Logs the navigation satellites the phone's GNSS receiver can see (GPS, GLONASS,
 * Galileo, BeiDou, QZSS, SBAS, IRNSS) with signal strength (C/N0 dB-Hz) and how many
 * are used in the current fix. Emits a summary line every 10s rather than per-satellite
 * spam. Requires ACCESS_FINE_LOCATION (already requested).
 */
@SuppressLint("MissingPermission")
class GnssScanner(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 10_000
) : Scanner {

    private val period = intervalMs.coerceAtLeast(5_000)
    private val lm = context.applicationContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var latest: GnssStatus? = null
    private var callback: GnssStatus.Callback? = null

    private val emit = object : Runnable {
        override fun run() {
            emitSummary()
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        running = true
        val c = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) { latest = status }
        }
        callback = c
        try {
            lm.registerGnssStatusCallback(c, handler)
        } catch (_: SecurityException) {
            cb("GNSS", "Missing permission for GNSS.", false); running = false; return
        } catch (_: Exception) {}
        handler.post(emit)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(emit)
        callback?.let { try { lm.unregisterGnssStatusCallback(it) } catch (_: Exception) {} }
        callback = null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun emitSummary() {
        val s = latest ?: return
        val n = s.satelliteCount
        if (n == 0) return
        var used = 0
        val sats = ArrayList<Triple<String, Int, Float>>(n)
        for (i in 0 until n) {
            if (s.usedInFix(i)) used++
            sats.add(Triple(constellation(s.getConstellationType(i)), s.getSvid(i), s.getCn0DbHz(i)))
        }
        sats.sortByDescending { it.third }
        val top = sats.take(5).joinToString(", ") { "${it.first}#${it.second} ${"%.0f".format(it.third)}dB" }
        cb("GNSS", "$n in view, $used used in fix; top: $top", false)
    }

    private fun constellation(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
        GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
        GnssStatus.CONSTELLATION_QZSS -> "QZS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "?"
    }
}
