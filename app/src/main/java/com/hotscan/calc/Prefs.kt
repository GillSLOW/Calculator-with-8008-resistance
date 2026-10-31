package com.hotscan.calc

import android.content.Context

/**
 * All persisted configuration: the unlock code, the log file name, every feature
 * toggle, and the numeric/text settings. Everything here survives restarts and is
 * reused until changed again in the control panel.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("stealthcalc_prefs", Context.MODE_PRIVATE)

    // ---- core ----
    var unlockCode: String
        get() = sp.getString(KEY_CODE, DEFAULT_CODE) ?: DEFAULT_CODE
        set(v) = sp.edit().putString(KEY_CODE, v).apply()

    var logFileName: String
        get() = sp.getString(KEY_LOG, DEFAULT_LOG) ?: DEFAULT_LOG
        set(v) = sp.edit().putString(KEY_LOG, v).apply()

    var backgroundEnabled: Boolean
        get() = sp.getBoolean("background_enabled", true)
        set(v) = sp.edit().putBoolean("background_enabled", v).apply()

    // ---- generic toggle access (used by the control panel) ----
    fun flag(key: String): Boolean = sp.getBoolean(key, DEFAULT_FLAGS[key] ?: false)
    fun setFlag(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    // ---- numeric / text settings ----
    var scanIntervalSec: Int
        get() = sp.getInt("scan_interval_sec", 15).coerceIn(5, 600)
        set(v) = sp.edit().putInt("scan_interval_sec", v.coerceIn(5, 600)).apply()

    var batteryThreshold: Int
        get() = sp.getInt("batt_threshold", 20).coerceIn(1, 99)
        set(v) = sp.edit().putInt("batt_threshold", v.coerceIn(1, 99)).apply()

    var decoyCode: String
        get() = sp.getString("decoy_code", "") ?: ""
        set(v) = sp.edit().putString("decoy_code", v).apply()

    var panicCode: String
        get() = sp.getString("panic_code", "") ?: ""
        set(v) = sp.edit().putString("panic_code", v).apply()

    var homeLat: Float
        get() = sp.getFloat("home_lat", Float.NaN)
        set(v) = sp.edit().putFloat("home_lat", v).apply()

    var homeLon: Float
        get() = sp.getFloat("home_lon", Float.NaN)
        set(v) = sp.edit().putFloat("home_lon", v).apply()

    val intervalMs: Long get() = scanIntervalSec * 1000L

    companion object {
        const val DEFAULT_CODE = "21042"
        const val DEFAULT_LOG = "signals.log"
        private const val KEY_CODE = "unlock_code"
        private const val KEY_LOG = "log_file_name"

        // Feature toggle keys
        const val F_WIFI = "en_wifi"
        const val F_BT = "en_bt"
        const val F_CELL = "en_cell"
        const val F_ROGUE = "en_rogue"
        const val F_GNSS = "en_gnss"
        const val F_TRAIL = "en_trail"
        const val F_MAG = "en_mag"
        const val F_BARO = "en_baro"
        const val F_LIGHT = "en_light"
        const val F_PROX = "en_prox"
        const val F_MOTION = "en_motion"
        const val F_NFC = "en_nfc"
        const val F_MIC = "en_mic"
        const val F_TRACKER = "en_tracker"
        const val F_FOLLOWER = "en_follower"
        const val F_AWAY = "en_away"
        const val F_DEDUP = "en_dedup"
        const val F_SUMMARY = "en_summary"
        const val F_BATTGUARD = "en_battguard"
        const val F_AUTOSTART = "en_autostart"

        val DEFAULT_FLAGS: Map<String, Boolean> = mapOf(
            F_WIFI to true, F_BT to true, F_CELL to true, F_ROGUE to true,
            F_GNSS to true, F_TRAIL to true, F_MAG to true,
            F_BARO to false, F_LIGHT to false, F_PROX to false, F_MOTION to false,
            F_NFC to false, F_MIC to false, F_TRACKER to false, F_FOLLOWER to false,
            F_AWAY to false, F_DEDUP to false, F_SUMMARY to true,
            F_BATTGUARD to false, F_AUTOSTART to false
        )
    }
}
