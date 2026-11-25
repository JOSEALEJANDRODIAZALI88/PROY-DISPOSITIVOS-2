package com.pacefit.ai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.max

data class MapRouteLine(
    val points: List<Pair<Double, Double>>,
    val color: Color,
    val widthDp: Float = 5f,
    val title: String = "",
)

data class MapPin(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val subtitle: String = "",
    val color: Color,
    val sizeDp: Int = 14,
)

private class OsmMapRenderState(
    var lastContentKey: Any? = null,
    var lastCameraKey: Any? = null,
)

@SuppressLint("ClickableViewAccessibility")
@Composable
fun PaceFitRoutesMap(
    lines: List<MapRouteLine>,
    pins: List<MapPin>,
    focusPoints: List<Pair<Double, Double>>,
    cameraKey: Any,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp,
    onMapLongPress: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderState = remember { OsmMapRenderState() }
    val currentOnLongPress by rememberUpdatedState(onMapLongPress)

    val mapView = remember(context) {
        ensureOsmConfigured(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 11.5
            maxZoomLevel = 19.0
            controller.setZoom(14.5)
            controller.setCenter(GeoPoint(-16.502, -68.126))
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    val eventsOverlay = remember {
        MapEventsOverlay(
            object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                override fun longPressHelper(p: GeoPoint): Boolean {
                    val callback = currentOnLongPress ?: return false
                    callback(p.latitude, p.longitude)
                    return true
                }
            },
        )
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val contentKey = remember(lines, pins, onMapLongPress != null) {
        Triple(lines, pins, onMapLongPress != null)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp)),
        update = { view ->
            if (renderState.lastContentKey != contentKey) {
                renderState.lastContentKey = contentKey
                view.overlays.clear()

                // Capa base para detectar pulsación larga sin bloquear pines
                if (currentOnLongPress != null) {
                    view.overlays.add(eventsOverlay)
                }

                val density = view.resources.displayMetrics.density

                lines.forEach { line ->
                    if (line.points.size >= 2) {
                        val poly = Polyline(view).apply {
                            setPoints(line.points.map { (lat, lng) -> GeoPoint(lat, lng) })
                            outlinePaint.color = line.color.toArgb()
                            outlinePaint.strokeWidth = line.widthDp * density
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            title = line.title
                        }
                        view.overlays.add(poly)
                    }
                }

                pins.forEach { pin ->
                    val marker = Marker(view).apply {
                        position = GeoPoint(pin.latitude, pin.longitude)
                        title = pin.title
                        snippet = pin.subtitle
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = buildCirclePinDrawable(
                            context = view.context,
                            fillColorArgb = pin.color.toArgb(),
                            sizeDp = pin.sizeDp,
                        )
                    }
                    view.overlays.add(marker)
                }

                view.invalidate()
            }

            if (renderState.lastCameraKey != cameraKey && focusPoints.isNotEmpty()) {
                renderState.lastCameraKey = cameraKey
                view.post {
                    focusMapOnPoints(view, focusPoints)
                }
            }
        },
    )
}

private fun ensureOsmConfigured(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = context.packageName
    val baseDir = File(context.cacheDir, "osmdroid")
    val tileDir = File(baseDir, "tiles")
    if (!tileDir.exists()) tileDir.mkdirs()
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir
}

private fun focusMapOnPoints(mapView: MapView, points: List<Pair<Double, Double>>) {
    if (points.isEmpty()) return
    if (points.size == 1 || mapView.width == 0 || mapView.height == 0) {
        val (lat, lng) = points.first()
        mapView.controller.setZoom(15.2)
        mapView.controller.setCenter(GeoPoint(lat, lng))
        return
    }
    val north = points.maxOf { it.first }
    val south = points.minOf { it.first }
    val east = points.maxOf { it.second }
    val west = points.minOf { it.second }
    val padLat = max(0.0022, (north - south) * 0.25)
    val padLng = max(0.0022, (east - west) * 0.25)
    val box = BoundingBox(
        (north + padLat).coerceAtMost(85.0),
        (east + padLng).coerceAtMost(180.0),
        (south - padLat).coerceAtLeast(-85.0),
        (west - padLng).coerceAtLeast(-180.0),
    )
    runCatching {
        mapView.zoomToBoundingBox(box, true, 48)
    }.onFailure {
        mapView.controller.setZoom(14.5)
        mapView.controller.setCenter(GeoPoint((north + south) / 2.0, (east + west) / 2.0))
    }
}

private fun buildCirclePinDrawable(
    context: Context,
    fillColorArgb: Int,
    sizeDp: Int,
): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt().coerceAtLeast(18)
    val strokePx = (2.5f * density).toInt().coerceAtLeast(3)
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setSize(px, px)
        setColor(fillColorArgb)
        setStroke(strokePx, 0xFF101820.toInt())
    }
}