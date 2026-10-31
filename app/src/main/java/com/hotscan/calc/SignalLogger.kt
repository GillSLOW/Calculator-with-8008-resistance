package com.hotscan.calc

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends scan results to a log file in the app's external files directory:
 *   Android/data/com.hotscan.calc/files/logs/<name>
 * This location needs no storage permission and survives app restarts.
 * The same file is reused for every session until the name is changed in Settings.
 */
class SignalLogger(context: Context, fileName: String) {

    private val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
    private val file = File(dir, sanitize(fileName))
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    val path: String get() = file.absolutePath

    fun writeHeader() {
        append("==== Session started ${ts.format(Date())} ====")
    }

    /** category e.g. WIFI / BT / BLE / CELL / ALERT */
    fun log(category: String, message: String) {
        append("${ts.format(Date())}\t$category\t$message")
    }

    private fun append(line: String) {
        synchronized(lock) {
            try {
                file.appendText(line + "\n")
            } catch (_: Exception) {
                // Logging is best-effort; never crash the scan loop on an I/O error.
            }
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim().ifEmpty { Prefs.DEFAULT_LOG }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.contains(".")) cleaned else "$cleaned.log"
    }
}
