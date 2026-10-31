package com.hotscan.calc

import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * In-app OpenStreetMap view (osmdroid) that draws the location trail from the most recent
 * log file. No API key required; map tiles are fetched from OpenStreetMap over the network.
 */
class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid config must be set before the MapView is created.
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = packageName
        cfg.osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        cfg.osmdroidTileCache = File(cfg.osmdroidBasePath, "tiles").apply { mkdirs() }

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }

        val container = FrameLayout(this).apply { addView(map) }
        setContentView(container)

        val points = loadTrail()
        if (points.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "  No GPS points logged yet.\n  Enable the Location trail and scan outdoors."
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC000000"))
            })
            map.controller.setZoom(3.0)
            return
        }

        val trail = Polyline().apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#FF4FC3F7")
            outlinePaint.strokeWidth = 8f
        }
        map.overlays.add(trail)

        addMarker(points.first(), "Start")
        if (points.size > 1) addMarker(points.last(), "Latest")

        map.post {
            try {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 120)
            } catch (_: Exception) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points.last())
            }
        }
    }

    private fun addMarker(p: GeoPoint, title: String) {
        map.overlays.add(Marker(map).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
        })
    }

    private fun loadTrail(): List<GeoPoint> {
        val dir = File(getExternalFilesDir(null), "logs")
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return emptyList()
        val pts = ArrayList<GeoPoint>()
        try {
            newest.forEachLine { raw ->
                val parts = raw.split('\t')
                if (parts.size >= 3 && parts[1] == "GPS") {
                    val coords = parts[2].substringBefore("  ").split(",")
                    val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull()
                    val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull()
                    if (lat != null && lon != null) pts.add(GeoPoint(lat, lon))
                }
            }
        } catch (_: Exception) {}
        return pts
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
