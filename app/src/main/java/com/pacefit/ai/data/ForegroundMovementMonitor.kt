package com.pacefit.ai.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.pacefit.ai.domain.ActivityHint
import com.pacefit.ai.domain.Coordinates
import com.pacefit.ai.domain.DetectedSegment
import com.pacefit.ai.domain.MovementReading
import com.pacefit.ai.domain.TransportDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Screen-visible only. This does not replace PF-003's background activity service. */
class ForegroundMovementMonitor(
    context: Context,
    windowSeconds: Int,
    private val activity: () -> ActivityHint,
    private val onReading: (Double, Boolean) -> Unit,
    private val onSegment: (DetectedSegment) -> Unit,
    private val onStatus: (String) -> Unit,
) : LocationListener, SensorEventListener {
    private val locations = context.getSystemService(LocationManager::class.java)
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val detector = TransportDetector(windowSeconds)
    private val mayReadSteps = Build.VERSION.SDK_INT < 29 ||
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    private var steps: Long? = null
    private var previous: Location? = null
    private var registeredSensor = false
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        check(locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "Activa el GPS para detectar transporte." }
        try {
            running = true
            if (mayReadSteps) {
                sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                    registeredSensor = sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, this, Looper.getMainLooper())
        } catch (error: Exception) {
            stop()
            throw error
        }
        onStatus(if (registeredSensor) "Esperando GPS y pasos…" else "Sin sensor de pasos: indica la actividad o el resultado será no determinado.")
    }

    fun stop() {
        running = false
        locations.removeUpdates(this)
        sensors.unregisterListener(this)
        detector.reset()
        previous = null
        steps = null
    }

    override fun onLocationChanged(location: Location) {
        if (!running) return
        if (!location.hasAccuracy() || location.accuracy > 20 ||
            SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000 !in 0L..15_000L
        ) {
            detector.reset()
            previous = null
            onStatus("Esperando una ubicación reciente con precisión de 20 m o mejor.")
            return
        }
        val previousLocation = previous
        previous = location
        val speed = if (location.hasSpeed()) location.speed.toDouble() * 3.6 else {
            val gap = previousLocation?.let { (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9 }
            if (previousLocation == null || gap == null || gap !in 1.0..15.0) return
            previousLocation.distanceTo(location) / gap * 3.6
        }
        onReading(speed, steps != null)
        detector.add(MovementReading(
            location.time, location.elapsedRealtimeNanos / 1_000_000, speed, steps, activity(),
        ))?.let(onSegment)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (running && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            steps = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toLong()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onProviderDisabled(provider: String) {
        detector.reset()
        previous = null
        onStatus("GPS desactivado. No se están generando tramos.")
    }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Platform callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
}

@SuppressLint("MissingPermission")
suspend fun captureCoordinates(context: Context): Coordinates = withTimeout(30_000) {
    suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.hasAccuracy() && location.accuracy <= 20 &&
                    SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000 in 0L..15_000L &&
                    continuation.isActive
                ) {
                    manager.removeUpdates(this)
                    continuation.resume(Coordinates(location.latitude, location.longitude))
                }
            }
            @Deprecated("Platform callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        try {
            check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "Activa el GPS o escribe las coordenadas." }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        } catch (error: Exception) {
            manager.removeUpdates(listener)
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
