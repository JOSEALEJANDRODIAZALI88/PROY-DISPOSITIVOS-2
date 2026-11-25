package com.pacefit.ai.modules

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class LocationPermissionStatus {
    PRECISE,
    APPROXIMATE,
    DENIED,
}

data class DeviceLocationReading(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int,
    val accuracyMeters: Float?,
    val pressureHpa: Float?,
    val altitudeSource: String,
    val zoneLabel: String,
    val approxCellLabel: String,
    val isLowPrecision: Boolean,
)

data class LiveWeatherSnapshot(
    val uvIndex: Double,
    val temperatureC: Double?,
    val cloudCoverPercent: Int?,
    val isOnline: Boolean,
    val sourceLabel: String,
)

object DevicePermissionHelper {
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun checkLocationPermission(context: Context): LocationPermissionStatus {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine) return LocationPermissionStatus.PRECISE

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (coarse) LocationPermissionStatus.APPROXIMATE else LocationPermissionStatus.DENIED
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class DeviceEnvironmentClient(private val context: Context) {

    suspend fun readCurrentLocationAndAltitude(): DeviceLocationReading? {
        if (DevicePermissionHelper.checkLocationPermission(context) == LocationPermissionStatus.DENIED) {
            return null
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val pressureHpa = readBarometerPressureHpa()
        val barometerAlt = pressureHpa?.let {
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).roundToInt()
        }

        val location = fetchBestLocation(lm) ?: return null
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val gpsAlt = if (location.hasAltitude() && location.altitude > 100.0) {
            location.altitude.roundToInt()
        } else {
            null
        }

        // Combina GPS y barómetro si existe; avisa si la medición es menos precisa (mitigación R1)
        val (altitude, sourceText, lowPrecision) = when {
            gpsAlt != null && (accuracy == null || accuracy <= 25f) && barometerAlt != null -> {
                val combined = ((gpsAlt * 0.7) + (barometerAlt * 0.3)).roundToInt()
                Triple(combined, "GPS + barómetro (${pressureHpa.roundToInt()} hPa)", false)
            }
            gpsAlt != null -> {
                val noisy = accuracy == null || accuracy > 20f
                val accNote = accuracy?.let { "±${it.roundToInt()} m" } ?: "sin precisión"
                Triple(gpsAlt, "GPS ($accNote · sin barómetro)", noisy)
            }
            barometerAlt != null -> {
                Triple(barometerAlt, "Barómetro (${pressureHpa.roundToInt()} hPa)", true)
            }
            else -> {
                Triple(
                    inferAltitudeByCoordinates(location.latitude, location.longitude),
                    "Referencia por zona",
                    true,
                )
            }
        }

        // Redondeo a 3 decimales (~100 m) para privacidad social (RNF10)
        val approxLat = String.format(Locale.US, "%.3f", location.latitude)
        val approxLng = String.format(Locale.US, "%.3f", location.longitude)

        return DeviceLocationReading(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = altitude.coerceIn(0, 6000),
            accuracyMeters = accuracy,
            pressureHpa = pressureHpa,
            altitudeSource = sourceText,
            zoneLabel = classifyLaPazZone(altitude),
            approxCellLabel = "$approxLat,$approxLng (~100 m)",
            isLowPrecision = lowPrecision,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBestLocation(lm: LocationManager): Location? {
        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }

        for (provider in providers) {
            val fresh = withTimeoutOrNull(4500L) { readSingleLocation(lm, provider) }
            if (fresh != null) return fresh
        }

        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readSingleLocation(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    lm.removeUpdates(listener)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private suspend fun readBarometerPressureHpa(): Float? = withTimeoutOrNull(1200L) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@withTimeoutOrNull null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return@withTimeoutOrNull null
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    sm.unregisterListener(this)
                    val hpa = event.values.firstOrNull()
                    if (cont.isActive) cont.resume(hpa)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
            val registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            if (!registered && cont.isActive) cont.resume(null)
        }
    }

    private fun classifyLaPazZone(altitudeMeters: Int): String = when {
        altitudeMeters >= 3900 -> "El Alto / Altiplano"
        altitudeMeters >= 3520 -> "La Paz (Centro / Laderas)"
        altitudeMeters >= 3100 -> "La Paz (Zona Sur)"
        else -> "Altitud detectada"
    }

    private fun inferAltitudeByCoordinates(lat: Double, lng: Double): Int = when {
        lng < -68.145 -> 4050
        lat < -16.525 -> 3250
        else -> 3600
    }
}

/**
 * Implementación online de UvProvider usando Open-Meteo (gratuito, sin API key ni tarjeta)
 * con respaldo automático al estimador solar offline de La Paz (RNF02 y RNF08).
 */
class OpenMeteoUvProvider(
    private val altitudeMeters: Double = 3600.0,
    private val ozoneDobson: Double = 260.0,
    private val increasePer1000m: Double = 0.10,
) : UvProvider {

    private val offlineFallback = LaPazUvEstimator(altitudeMeters, ozoneDobson, increasePer1000m)

    override suspend fun currentUv(latitude: Double, longitude: Double): UvReading {
        val snapshot = fetchWeatherSnapshot(latitude, longitude)
        return UvReading(
            index = snapshot.uvIndex,
            observedAtEpochMillis = System.currentTimeMillis(),
            source = snapshot.sourceLabel,
        )
    }

    suspend fun fetchWeatherSnapshot(latitude: Double, longitude: Double): LiveWeatherSnapshot =
        withContext(Dispatchers.IO) {
            try {
                val urlString = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,cloud_cover,uv_index&timezone=America%%2FLa_Paz",
                    latitude,
                    longitude,
                )
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                try {
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val current = JSONObject(body).optJSONObject("current")
                        val uv = current?.optDouble("uv_index", Double.NaN) ?: Double.NaN
                        if (!uv.isNaN()) {
                            val temp = current?.optDouble("temperature_2m", Double.NaN)?.takeIf { !it.isNaN() }
                            val clouds = current?.optInt("cloud_cover", -1)?.takeIf { it >= 0 }
                            return@withContext LiveWeatherSnapshot(
                                uvIndex = uv.coerceIn(0.0, 20.0),
                                temperatureC = temp,
                                cloudCoverPercent = clouds,
                                isOnline = true,
                                sourceLabel = "Clima en vivo (Open-Meteo)",
                            )
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // Cae al modelo offline sin cerrar la app (RNF08)
            }

            val fallback = offlineFallback.currentUv(latitude, longitude)
            LiveWeatherSnapshot(
                uvIndex = fallback.index,
                temperatureC = null,
                cloudCoverPercent = null,
                isOnline = false,
                sourceLabel = "Modo offline · estimación solar para ${altitudeMeters.roundToInt()} m",
            )
        }
}