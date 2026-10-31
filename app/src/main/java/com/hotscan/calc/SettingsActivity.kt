package com.hotscan.calc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File

/**
 * Control panel: unlock/decoy/panic codes, log file name, scan interval, battery
 * threshold, "set home" for the geofence, and an on/off switch for every scanner and
 * feature. Built in code so the long list of switches stays maintainable.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var editCode: EditText
    private lateinit var editLog: EditText
    private lateinit var editDecoy: EditText
    private lateinit var editPanic: EditText
    private lateinit var editInterval: EditText
    private lateinit var editBatt: EditText
    private val switches = HashMap<String, SwitchCompat>()

    // key -> label, in display order
    private val toggles = listOf(
        Prefs.F_WIFI to "WiFi access points",
        Prefs.F_BT to "Bluetooth devices",
        Prefs.F_TRACKER to "  ↳ Tracker/AirTag detection",
        Prefs.F_FOLLOWER to "  ↳ Follower/tail detection",
        Prefs.F_CELL to "Cellular towers",
        Prefs.F_ROGUE to "  ↳ Rogue-cell heuristics",
        Prefs.F_GNSS to "GNSS satellites",
        Prefs.F_TRAIL to "Location trail",
        Prefs.F_AWAY to "  ↳ Home/away geofence alerts",
        Prefs.F_MAG to "Magnetometer",
        Prefs.F_BARO to "Barometer (pressure)",
        Prefs.F_LIGHT to "Light sensor",
        Prefs.F_PROX to "Proximity sensor",
        Prefs.F_MOTION to "Motion (accelerometer)",
        Prefs.F_MIC to "Microphone level meter",
        Prefs.F_DEDUP to "Dedup repeated lines",
        Prefs.F_SUMMARY to "Write session summary",
        Prefs.F_BATTGUARD to "Battery guard (auto-pause)",
        Prefs.F_AUTOSTART to "Auto-start after reboot"
    )

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF101418"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        header(root, "Settings")

        editCode = labeledEdit(root, "Unlock code (3–12 digits)", prefs.unlockCode, numeric = true)
        editLog = labeledEdit(root, "Log file name", prefs.logFileName, numeric = false)
        editDecoy = labeledEdit(root, "Decoy code (optional — opens empty screen)", prefs.decoyCode, numeric = true)
        editPanic = labeledEdit(root, "Panic code (optional — wipes logs)", prefs.panicCode, numeric = true)
        editInterval = labeledEdit(root, "Scan interval (seconds, 5–600)", prefs.scanIntervalSec.toString(), numeric = true)
        editBatt = labeledEdit(root, "Battery guard threshold (%)", prefs.batteryThreshold.toString(), numeric = true)

        val homeBtn = Button(this).apply {
            text = homeLabel()
            setOnClickListener { setHome(this) }
        }
        root.addView(homeBtn)

        header(root, "Scanners & features")
        for ((key, label) in toggles) {
            val sw = SwitchCompat(this).apply {
                text = label
                setTextColor(Color.WHITE)
                isChecked = prefs.flag(key)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            }
            switches[key] = sw
            root.addView(sw)
        }

        val save = Button(this).apply {
            text = "Save"
            setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        }
        root.addView(save)

        val note = TextView(this).apply {
            text = "Changes to the log name or scan interval take effect next time scanning " +
                "is started. A new unlock code takes effect immediately."
            setTextColor(Color.parseColor("#FF607D8B"))
            textSize = 11f
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(note)

        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#FF101418")) })
    }

    private fun save() {
        val code = editCode.text.toString().trim()
        if (!code.matches(Regex("\\d{3,12}"))) { toast("Code must be 3–12 digits."); return }
        val log = editLog.text.toString().trim()
        if (log.isEmpty()) { toast("Enter a log file name."); return }
        val decoy = editDecoy.text.toString().trim()
        val panic = editPanic.text.toString().trim()
        if (decoy.isNotEmpty() && (decoy == code)) { toast("Decoy code must differ from unlock code."); return }
        if (panic.isNotEmpty() && (panic == code || panic == decoy)) { toast("Panic code must be unique."); return }
        val interval = editInterval.text.toString().toIntOrNull() ?: 15
        val batt = editBatt.text.toString().toIntOrNull() ?: 20

        prefs.unlockCode = code
        prefs.logFileName = log
        prefs.decoyCode = decoy
        prefs.panicCode = panic
        prefs.scanIntervalSec = interval
        prefs.batteryThreshold = batt
        for ((key, sw) in switches) prefs.setFlag(key, sw.isChecked)

        toast("Saved.")
        finish()
    }

    @SuppressLint("MissingPermission")
    private fun setHome(btn: Button) {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null } catch (_: Exception) { null }
        if (loc == null) { toast("No location yet — enable location and try again."); return }
        prefs.homeLat = loc.latitude.toFloat()
        prefs.homeLon = loc.longitude.toFloat()
        btn.text = homeLabel()
        toast("Home set.")
    }

    private fun homeLabel(): String =
        if (prefs.homeLat.isNaN()) "Set home location (for geofence)"
        else "Home: %.4f, %.4f  (tap to update)".format(prefs.homeLat, prefs.homeLon)

    // ---- tiny view helpers ----
    private fun header(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun labeledEdit(parent: LinearLayout, label: String, value: String, numeric: Boolean): EditText {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#FFB0BEC5"))
            textSize = 12f
            setPadding(0, dp(10), 0, dp(2))
        })
        val e = EditText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }
        parent.addView(e)
        return e
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
