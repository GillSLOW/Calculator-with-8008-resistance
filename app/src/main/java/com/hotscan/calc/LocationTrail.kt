package com.hotscan.calc

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

/**
 * Logs a timestamped location trail (lat/lon, altitude, speed, accuracy, provider)
 * roughly every 10s, so the other scans can be correlated with where the phone was.
 * Requires ACCESS_FINE_LOCATION (already requested).
 */
@SuppressLint("MissingPermission")
class LocationTrail(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 10_000,
    private val awayDetect: Boolean = false,
    private val homeLat: Float = Float.NaN,
    private val homeLon: Float = Float.NaN
) : Scanner {

    private val period = intervalMs.coerceAtLeast(5_000)
    private val lm = context.applicationContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false
    private var wasAway: Boolean? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val alt = if (loc.hasAltitude()) "%.0f".format(loc.altitude) else "?"
            val spd = if (loc.hasSpeed()) "%.1f".format(loc.speed) else "?"
            val acc = if (loc.hasAccuracy()) "%.0f".format(loc.accuracy) else "?"
            cb(
                "GPS",
                "%.6f, %.6f  alt=%sm spd=%sm/s acc=%sm  (%s)".format(
                    loc.latitude, loc.longitude, alt, spd, acc, loc.provider
                ),
                false
            )
            if (awayDetect && !homeLat.isNaN() && !homeLon.isNaN()) checkAway(loc)
        }
        override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    override fun start() {
        if (running) return
        running = true
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, period, 0f, listener)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, period, 0f, listener)
        } catch (_: SecurityException) {
            cb("GPS", "Missing permission for location updates.", false)
        } catch (_: Exception) {}
    }

    override fun stop() {
        running = false
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
    }

    private fun checkAway(loc: Location) {
        val d = FloatArray(1)
        Location.distanceBetween(
            loc.latitude, loc.longitude, homeLat.toDouble(), homeLon.toDouble(), d
        )
        val away = d[0] > 150f // meters outside "home"
        if (wasAway == null) {
            wasAway = away
            cb("GEO", if (away) "Starting away from home (${d[0].toInt()}m)"
                      else "Starting at home", false)
        } else if (away != wasAway) {
            wasAway = away
            cb("ALERT", if (away) "⚠ Left home geofence (${d[0].toInt()}m away)"
                        else "Returned to home geofence", true)
        }
    }
}
