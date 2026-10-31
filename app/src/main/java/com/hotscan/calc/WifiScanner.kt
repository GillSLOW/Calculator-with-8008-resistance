package com.hotscan.calc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

/**
 * Logs nearby WiFi access points the radio can see (SSID, BSSID, channel, signal,
 * security). This is the same beacon data WiFi analyzer apps display.
 *
 * Note: modern Android throttles WifiManager.startScan() to a few calls per ~2 min
 * for foreground apps, so we also read the system's cached results each cycle.
 */
class WifiScanner(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 25_000
) : Scanner {

    // WiFi scans are throttled by the OS, so never poll faster than ~20s.
    private val period = intervalMs.coerceAtLeast(20_000)

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var receiver: BroadcastReceiver? = null

    private val rescan = object : Runnable {
        override fun run() {
            try { wifi.startScan() } catch (_: Exception) {}
            readResults()
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        running = true
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { readResults() }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)
        if (!wifi.isWifiEnabled) {
            cb("WIFI", "WiFi radio is off — enable WiFi for access-point scanning.", false)
        }
        handler.post(rescan)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(rescan)
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun readResults() {
        val results: List<ScanResult> = try {
            wifi.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            cb("WIFI", "Missing permission for WiFi scan results.", false)
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (r in results) {
            val ssid = r.SSID?.ifEmpty { "<hidden>" } ?: "<hidden>"
            val chan = frequencyToChannel(r.frequency)
            val sec = securityOf(r)
            cb("WIFI", "\"$ssid\"  ${r.BSSID}  ch$chan  ${r.level}dBm  [$sec]", false)
        }
    }

    private fun securityOf(r: ScanResult): String {
        val caps = r.capabilities ?: ""
        return when {
            caps.contains("WPA3") -> "WPA3"
            caps.contains("RSN") || caps.contains("WPA2") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5   // 6 GHz
        else -> -1
    }
}
