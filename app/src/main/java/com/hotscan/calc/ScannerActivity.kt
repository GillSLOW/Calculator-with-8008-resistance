package com.hotscan.calc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hotscan.calc.databinding.ActivityScannerBinding

/**
 * Live viewer for the background scan service.
 *
 * Re-lock behaviour: when the screen turns off, this screen finishes itself and
 * drops back to the calculator, so the code must be re-entered to see the monitor
 * again. The ScanService keeps scanning and logging the whole time.
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var b: ActivityScannerBinding
    private lateinit var prefs: Prefs

    private var service: ScanService? = null
    private var bound = false
    private var lineCount = 0

    // True while we're opening one of our own screens (Settings/Logs/Map/permission dialog),
    // so leaving for those does NOT re-lock to the calculator. Any other stop (screen off,
    // Home, app switch) re-locks.
    private var launchingChild = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as ScanService.LocalBinder).service
            service = s
            bound = true
            s.listener = { line -> runOnUiThread { appendLine(line) } }
            renderSnapshot(s)
            syncButton()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null; bound = false
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
            .filter { it != Manifest.permission.POST_NOTIFICATIONS } // notif denial is non-fatal
        if (denied.isNotEmpty()) {
            Toast.makeText(
                this,
                "Missing: ${denied.joinToString { it.substringAfterLast('.') }}",
                Toast.LENGTH_LONG
            ).show()
        }
        startScanService() // start anyway; partial grants still yield some data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.btnStartStop.setOnClickListener {
            if (service?.isRunning() == true) stopScanService() else requestThenScan()
        }
        b.btnClearView.setOnClickListener {
            b.logView.text = ""; lineCount = 0; service?.clearBuffer()
        }
        b.btnSettings.setOnClickListener { openChild(SettingsActivity::class.java) }
        b.btnBackground.setOnClickListener {
            prefs.backgroundEnabled = !prefs.backgroundEnabled
            updateBackgroundButton()
        }
        b.btnLogs.setOnClickListener { openChild(LogsActivity::class.java) }
        b.btnGraph.setOnClickListener { openChild(GraphActivity::class.java) }
        b.btnMap.setOnClickListener { openChild(MapActivity::class.java) }
        updateBackgroundButton()
    }

    private fun openChild(cls: Class<*>) {
        launchingChild = true
        startActivity(Intent(this, cls))
    }

    private fun updateBackgroundButton() {
        b.btnBackground.text = if (prefs.backgroundEnabled) "BG: On" else "BG: Off"
    }

    override fun onResume() {
        super.onResume()
        launchingChild = false
        if (prefs.flag(Prefs.F_NFC)) enableNfc()
    }

    override fun onPause() {
        super.onPause()
        try { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) } catch (_: Exception) {}
    }

    private fun enableNfc() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        try {
            adapter.enableReaderMode(this, { tag ->
                val id = tag.id.joinToString("") { "%02X".format(it) }
                val techs = tag.techList.joinToString(",") { it.substringAfterLast('.') }
                val line = "tag id=$id  tech=[$techs]"
                runOnUiThread {
                    if (service?.isRunning() == true) service?.external("NFC", line, false)
                    else appendLine(ScanService.Line("NFC", line, false))
                }
            }, flags, null)
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        // Bind (auto-create) so we reflect current state and receive live lines.
        bindService(Intent(this, ScanService::class.java), connection, Context.BIND_AUTO_CREATE)
        updateStatus()
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            service?.listener = null
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
        // Re-lock to the calculator whenever the monitor stops for any reason other than
        // opening one of our own screens — covers screen-off, Home, and app-switch.
        if (!launchingChild && !isFinishing) {
            if (!prefs.backgroundEnabled) stopScanService()
            finish()
        }
    }

    private fun requestThenScan() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startScanService()
        } else {
            launchingChild = true // the system permission dialog will stop us; don't re-lock
            permLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (prefs.flag(Prefs.F_MIC)) list += Manifest.permission.RECORD_AUDIO
        return list
    }

    private fun startScanService() {
        warnIfLocationOff()
        val intent = Intent(this, ScanService::class.java)
        ContextCompat.startForegroundService(this, intent)
        if (!bound) {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        b.btnStartStop.text = "Stop"
        updateStatus()
    }

    private fun stopScanService() {
        val intent = Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_STOP }
        startService(intent)
        b.btnStartStop.text = "Start"
        updateStatus()
    }

    private fun renderSnapshot(s: ScanService) {
        b.logView.text = ""
        lineCount = 0
        for (line in s.snapshot()) appendLine(line)
    }

    private fun syncButton() {
        b.btnStartStop.text = if (service?.isRunning() == true) "Stop" else "Start"
        updateStatus()
    }

    private fun warnIfLocationOff() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val on = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!on) Toast.makeText(
            this, "Location is OFF — WiFi/cell results will be empty.", Toast.LENGTH_LONG
        ).show()
    }

    private fun appendLine(line: ScanService.Line) {
        val text = "[${line.category}] ${line.text}\n"
        val span = SpannableString(text)
        if (line.alert) span.setSpan(
            ForegroundColorSpan(Color.parseColor("#FFFF6E6E")),
            0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        b.logView.append(span)
        lineCount++
        if (lineCount > 1200) {
            val keep = b.logView.text.toString().takeLast(40000)
            b.logView.text = keep
            lineCount = keep.count { it == '\n' }
        }
        b.scroll.post { b.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun updateStatus() {
        val state = if (service?.isRunning() == true) "SCANNING (background)" else "idle"
        b.status.text = "Log file: ${prefs.logFileName}   •   $state"
    }
}
