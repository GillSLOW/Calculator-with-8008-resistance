package com.hotscan.calc

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Graphs the dBm readings from the most recent log file (WiFi/BT/cell signal over time).
 */
class GraphActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val values = loadDbm()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FF101418"))
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Signal strength (dBm) — ${values.size} readings"
            setTextColor(Color.WHITE); textSize = 18f
            setPadding(0, 0, 0, 16)
        })
        val graph = GraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setValues(values)
        }
        root.addView(graph)
        setContentView(root)
    }

    private fun loadDbm(): List<Int> {
        val dir = File(getExternalFilesDir(null), "logs")
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return emptyList()
        val rx = Regex("(-?\\d+)dBm")
        val out = ArrayList<Int>()
        try {
            newest.forEachLine { line ->
                rx.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { out.add(it) }
            }
        } catch (_: Exception) {}
        return out.takeLast(500)
    }
}
