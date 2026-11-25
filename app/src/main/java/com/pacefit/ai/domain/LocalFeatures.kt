package com.pacefit.ai.domain

enum class ActivityHint(val label: String) {
    UNKNOWN("Sin indicar"), WALK("Caminando"), RUN("Corriendo"), VEHICLE("En vehículo"),
}

enum class TransportClass(val label: String) {
    ACTIVE("A pie"), VEHICLE("Vehículo"), UNKNOWN("No determinado"),
}

enum class WasteQuantity(val label: String, val points: Int) {
    LITTLE("Poco", 5), MEDIUM("Medio", 10), MUCH("Mucho", 20),
}

enum class ReportKind(val label: String) {
    POTHOLE("Bache"), WASTE("Basura"), LIGHT("Luminaria"), OTHER("Otro"),
}

data class Coordinates(val latitude: Double, val longitude: Double)

object LocalInput {
    fun coordinates(latitude: String, longitude: String): Coordinates? {
        fun number(text: String) = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        val lat = number(latitude) ?: return null
        val lon = number(longitude) ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) Coordinates(lat, lon) else null
    }

    fun windowSeconds(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 15..120 }
}

data class MovementReading(
    val timeMillis: Long,
    val elapsedMillis: Long,
    val speedKmh: Double,
    val stepCounter: Long?,
    val activity: ActivityHint,
)

data class DetectedSegment(
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val steps: Long?,
    val activity: ActivityHint,
    val classification: TransportClass,
) {
    // Unknown samples are not credited as exercise either.
    val includedInMetrics: Boolean get() = classification == TransportClass.ACTIVE
}

/**
 * Conservative local heuristic, not a validated activity-recognition model.
 * Speed alone never turns running into a vehicle. Missing step evidence is unknown.
 */
class TransportDetector(private val windowSeconds: Int = 30) {
    init { require(windowSeconds in 15..120) }
    private val readings = mutableListOf<MovementReading>()

    fun reset() = readings.clear()

    fun add(reading: MovementReading): DetectedSegment? {
        if (!reading.speedKmh.isFinite() || reading.speedKmh !in 0.0..250.0) {
            reset()
            return null
        }
        readings.lastOrNull()?.let { previous ->
            val gap = reading.elapsedMillis - previous.elapsedMillis
            if (gap <= 0 || gap > 15_000 || previous.activity != reading.activity) reset()
        }
        readings += reading
        val duration = reading.elapsedMillis - readings.first().elapsedMillis
        if (duration < windowSeconds * 1_000L || readings.size < 3) return null
        val first = readings.first()
        val distance = readings.zipWithNext().sumOf { (a, b) ->
            (a.speedKmh + b.speedKmh) / 2.0 / 3.6 * (b.elapsedMillis - a.elapsedMillis) / 1_000.0
        }
        val speed = distance / (duration / 1_000.0) * 3.6
        val steps = if (readings.all { it.stepCounter != null } &&
            readings.zipWithNext().all { (a, b) -> b.stepCounter!! >= a.stepCounter!! }
        ) reading.stepCounter!! - first.stepCounter!! else null
        val cadence = steps?.let { it * 60_000.0 / duration }
        val classification = when {
            speed < 1.5 -> TransportClass.UNKNOWN
            reading.activity == ActivityHint.VEHICLE -> TransportClass.VEHICLE
            speed <= 30 && (reading.activity == ActivityHint.RUN || reading.activity == ActivityHint.WALK) ->
                TransportClass.ACTIVE
            speed <= 30 && cadence != null && cadence >= 40 -> TransportClass.ACTIVE
            speed >= 15 && cadence != null && cadence < 20 -> TransportClass.VEHICLE
            else -> TransportClass.UNKNOWN
        }
        readings.clear()
        readings += reading
        return DetectedSegment(first.timeMillis, reading.timeMillis, distance, speed, steps, reading.activity, classification)
    }
}
