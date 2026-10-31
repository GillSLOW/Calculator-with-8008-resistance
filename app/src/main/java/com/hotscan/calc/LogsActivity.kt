package com.hotscan.calc

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Lists saved log files and lets the user view, share, export a GPS trail as GPX, or
 * delete them. Sharing is how data leaves the phone — the user chooses the destination.
 */
class LogsActivity : AppCompatActivity() {

    private val dir by lazy { File(getExternalFilesDir(null), "logs") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF101418"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "Logs"; setTextColor(Color.WHITE); textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })

        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No log files yet."; setTextColor(Color.parseColor("#FF90A4AE"))
            })
        }
        for (f in files) {
            root.addView(Button(this).apply {
                text = "${f.name}   (${f.length() / 1024} KB)"
                setOnClickListener { openFile(f) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            })
        }
        setContentView(ScrollView(this).apply {
            addView(root); setBackgroundColor(Color.parseColor("#FF101418"))
        })
    }

    private fun openFile(f: File) {
        val preview = try {
            val t = f.readText()
            if (t.length > 8000) "…(truncated)\n" + t.takeLast(8000) else t
        } catch (_: Exception) { "(could not read file)" }

        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setMessage(preview)
            .setPositiveButton("Share") { _, _ -> shareFile(f) }
            .setNeutralButton("Export GPX") { _, _ -> exportGpx(f) }
            .setNegativeButton("Delete") { _, _ -> confirmDelete(f) }
            .show()
    }

    private fun shareFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share ${f.name}"))
        } catch (e: Exception) {
            toast("Share failed: ${e.message}")
        }
    }

    private fun exportGpx(f: File) {
        val exportsDir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
        val out = File(exportsDir, f.nameWithoutExtension + ".gpx")
        val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val lineTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        var points = 0
        try {
            out.bufferedWriter().use { w ->
                w.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                w.appendLine("<gpx version=\"1.1\" creator=\"StealthCalc\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
                w.appendLine("<trk><name>${f.name}</name><trkseg>")
                f.forEachLine { raw ->
                    val parts = raw.split('\t')
                    if (parts.size >= 3 && parts[1] == "GPS") {
                        val coords = parts[2].substringBefore("  ").split(",")
                        val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull()
                        val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            val t = try { isoUtc.format(lineTs.parse(parts[0])!!) } catch (_: Exception) { null }
                            w.append("<trkpt lat=\"$lat\" lon=\"$lon\">")
                            if (t != null) w.append("<time>$t</time>")
                            w.appendLine("</trkpt>")
                            points++
                        }
                    }
                }
                w.appendLine("</trkseg></trk></gpx>")
            }
        } catch (e: Exception) {
            toast("Export failed: ${e.message}"); return
        }
        if (points == 0) { toast("No GPS points in this log."); out.delete(); return }
        shareFile(out)
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setMessage("Delete ${f.name}?")
            .setPositiveButton("Delete") { _, _ -> f.delete(); render() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
