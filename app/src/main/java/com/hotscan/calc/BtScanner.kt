package com.hotscan.calc

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * Bluetooth scanning: classic discovery + BLE advertisements.
 *
 * Optional features (gated by the control panel):
 *  - trackerDetect: flags Apple Find My / AirTag, Tile and Samsung SmartTag style
 *    advertisements.
 *  - followerDetect: tracks each device across the session and flags one that keeps
 *    reappearing over a long span (possible follower/tail). In-session only.
 */
@SuppressLint("MissingPermission") // permissions requested at runtime by ScannerActivity
class BtScanner(
    private val context: Context,
    private val cb: SignalCallback,
    intervalMs: Long = 20_000,
    private val trackerDetect: Boolean = false,
    private val followerDetect: Boolean = false
) : Scanner {

    private val period = intervalMs.coerceAtLeast(12_000)
    private val manager = context.applicationContext
        .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var receiver: BroadcastReceiver? = null

    private data class Sighting(var first: Long, var last: Long, var count: Int, var flagged: Boolean)
    private val seen = HashMap<String, Sighting>()

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val dev = result.device
            val name = safeName(dev) ?: result.scanRecord?.deviceName ?: "<unknown>"
            cb("BLE", "$name  ${dev.address}  ${result.rssi}dBm", false)
            if (trackerDetect) checkTracker(result, dev.address)
            if (followerDetect) record(dev.address, name)
        }
    }

    private val periodic = object : Runnable {
        override fun run() {
            try { if (adapter?.isDiscovering == false) adapter.startDiscovery() } catch (_: Exception) {}
            if (followerDetect) evaluateFollowers()
            if (running) handler.postDelayed(this, period)
        }
    }

    override fun start() {
        if (running) return
        val a = adapter
        if (a == null) { cb("BT", "No Bluetooth adapter on this device.", false); return }
        if (!a.isEnabled) { cb("BT", "Bluetooth radio is off — enable it for scanning.", false); return }
        running = true

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == BluetoothDevice.ACTION_FOUND) {
                    val dev: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    dev ?: return
                    val name = safeName(dev) ?: "<unknown>"
                    val r = if (rssi != Short.MIN_VALUE) "${rssi}dBm" else "n/a"
                    cb("BT", "$name  ${dev.address}  $r", false)
                    if (followerDetect) record(dev.address, name)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        try { a.bluetoothLeScanner?.startScan(leCallback) } catch (_: Exception) {}
        handler.post(periodic)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(periodic)
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        try { adapter?.bluetoothLeScanner?.stopScan(leCallback) } catch (_: Exception) {}
        receiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
    }

    private fun checkTracker(result: ScanResult, addr: String) {
        val rec = result.scanRecord ?: return
        // Apple Find My / AirTag: manufacturer id 0x004C (76), offline-finding type 0x12.
        val apple = rec.getManufacturerSpecificData(0x004C)
        if (apple != null && apple.isNotEmpty() && (apple[0].toInt() and 0xFF) == 0x12) {
            cb("ALERT", "⚠ Apple Find My / AirTag-type beacon  $addr  ${result.rssi}dBm", true)
            return
        }
        // Tile: service UUID 0xFEED / 0xFEEC.
        val uuids = rec.serviceUuids
        if (uuids != null) {
            if (uuids.any { it.matches16(0xFEED) || it.matches16(0xFEEC) }) {
                cb("ALERT", "⚠ Tile tracker  $addr  ${result.rssi}dBm", true); return
            }
            if (uuids.any { it.matches16(0xFD5A) }) {
                cb("ALERT", "⚠ Samsung SmartTag-type beacon  $addr  ${result.rssi}dBm", true); return
            }
        }
        // Samsung manufacturer id 0x0075.
        if (rec.getManufacturerSpecificData(0x0075) != null) {
            cb("ALERT", "⚠ Samsung tracker-type beacon  $addr  ${result.rssi}dBm", true)
        }
    }

    private fun ParcelUuid.matches16(v: Int): Boolean {
        val s = uuid.toString().lowercase()
        val hex = "%04x".format(v and 0xFFFF)
        return s.startsWith("0000$hex-")
    }

    private fun record(addr: String, name: String) {
        val now = System.currentTimeMillis()
        val s = seen[addr]
        if (s == null) seen[addr] = Sighting(now, now, 1, false)
        else { s.last = now; s.count++ }
    }

    private fun evaluateFollowers() {
        val now = System.currentTimeMillis()
        for ((addr, s) in seen) {
            val span = s.last - s.first
            if (!s.flagged && span > 10 * 60_000L && s.count >= 5 && (now - s.last) < period * 2) {
                s.flagged = true
                cb("ALERT", "⚠ Device persists ${span / 60_000}min (possible follower)  $addr", true)
            }
        }
    }

    private fun safeName(dev: BluetoothDevice): String? = try {
        dev.name?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
}
