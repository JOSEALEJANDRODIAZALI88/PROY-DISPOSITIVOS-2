package com.pacefit.ai.ui

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pacefit.ai.BuildConfig
import com.pacefit.ai.data.RoutePointEntity
import com.pacefit.ai.data.TransportSegmentEntity
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

data class MapPin(val id: String, val latitude: Double, val longitude: Double, val label: String)

@Composable
fun MapPanel(points: List<RoutePointEntity> = emptyList(), segments: List<TransportSegmentEntity> = emptyList(),
    pins: List<MapPin> = emptyList(), onPin: (String) -> Unit = {}) {
    val context = LocalContext.current
    val link = LocalUriHandler.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "PaceFit/${BuildConfig.VERSION_NAME} (github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2)"
            osmdroidBasePath = File(context.cacheDir, "osm").apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(15.0)
            // Initial city viewport, not a fabricated GPS fix.
            controller.setCenter(GeoPoint(-16.5, -68.15))
        }
    }
    var centered by remember { mutableStateOf(false) }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    Column {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(280.dp), update = {
            it.overlays.clear()
            points.zipWithNext().forEach { (a, b) ->
                // Never draw across GPS gaps or pauses.
                if (b.timeMillis - a.timeMillis in 1..15_000) {
                    val classification = segments.firstOrNull { s -> a.timeMillis >= s.startedAt && b.timeMillis <= s.endedAt }?.classification
                    it.overlays.add(Polyline().apply {
                        setPoints(listOf(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude)))
                        outlinePaint.color = when (classification) {
                            "ACTIVE" -> Color.rgb(23, 94, 73)
                            "VEHICLE" -> Color.rgb(196, 101, 22)
                            else -> Color.GRAY
                        }
                        outlinePaint.strokeWidth = 9f
                    })
                }
            }
            pins.forEach { pin ->
                it.overlays.add(Marker(it).apply {
                    position = GeoPoint(pin.latitude, pin.longitude); title = pin.label
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> onPin(pin.id); true }
                })
            }
            if (!centered) {
                val first = points.firstOrNull()?.let { p -> GeoPoint(p.latitude, p.longitude) }
                    ?: pins.firstOrNull()?.let { p -> GeoPoint(p.latitude, p.longitude) }
                first?.let { location -> it.controller.setCenter(location); centered = true }
            }
            it.invalidate()
        })
        TextButton({ link.openUri("https://www.openstreetmap.org/copyright") }) { Text("© OpenStreetMap contributors") }
        Text("El mapa base necesita internet; no se descarga para uso sin conexión. La ruta SQLite sigue siendo privada.",
            style = MaterialTheme.typography.bodySmall)
    }
}
