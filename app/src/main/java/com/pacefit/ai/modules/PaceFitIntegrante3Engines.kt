package com.pacefit.ai.modules

import android.content.Context
import com.pacefit.ai.data.PaceParameterEntity
import com.pacefit.ai.domain.HealthProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// PF-002 · Catálogo de parámetros y fuentes (Anexo A)
// ============================================================================

enum class ParameterCategory(val label: String) {
    ALL("Todos"),
    HYDRATION("Hidratación"),
    UV("Radiación UV"),
    CALORIES("Calorías"),
    SOCIAL("Social"),
}

object ParameterCatalogDefaults {
    fun buildDefaults(now: Long = System.currentTimeMillis()): List<PaceParameterEntity> = listOf(
        PaceParameterEntity(
            key = "water_base",
            label = "Agua base por peso",
            value = 35.0,
            unit = "ml/kg/día",
            source = "Referencia común nutricional (Anexo A · agua.a_base_ml_por_kg)",
            status = "REFERENCIA",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "water_per_kcal",
            label = "Agua extra por ejercicio",
            value = 1.0,
            unit = "ml/kcal",
            source = "Provisional; calibrar con tasa de sudoración ACSM (Sawka et al., 2007)",
            status = "PROVISIONAL",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "water_sun_factor",
            label = "Factor solar de hidratación",
            value = 0.03,
            unit = "factor/UV",
            source = "Provisional del proyecto (agua.f_sol_por_uv); ajustar con pruebas",
            status = "PROVISIONAL",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "water_altitude_extra",
            label = "Extra de agua por altitud",
            value = 100.0,
            unit = "ml/1000m (>2500m)",
            source = "Provisional por pérdida respiratoria sobre 2.500 msnm",
            status = "PROVISIONAL",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "altitude",
            label = "Altitud de referencia",
            value = 3600.0,
            unit = "msnm",
            source = "Contexto geográfico aproximado de La Paz",
            status = "REFERENCIA",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "uv_altitude_increase",
            label = "Aumento UV por altitud",
            value = 0.10,
            unit = "fracción/1000m",
            source = "OMS / WMO (2002): +10 % a +12 % por cada 1.000 m",
            status = "RESPALDADO",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "uv_ozone_dobson",
            label = "Ozono atmosférico local",
            value = 260.0,
            unit = "DU",
            source = "Provisional para La Paz (uv.ozono_dobson); verificar con fuente local",
            status = "PROVISIONAL",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "uv_high",
            label = "Umbral de alerta UV",
            value = 8.0,
            unit = "índice UV",
            source = "Categoría Muy Alto de la OMS (2002)",
            status = "RESPALDADO",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "calories_f_alt",
            label = "Factor metabólico de altura",
            value = 1.00,
            unit = "multiplicador",
            source = "Valor neutro provisional hasta validar literatura en altura",
            status = "PROVISIONAL",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "social_radius_m",
            label = "Radio de corredores cercanos",
            value = 1500.0,
            unit = "m",
            source = "Decisión de diseño PaceFit AI (social.radio_busqueda_m)",
            status = "DISENO",
            updatedAt = now,
        ),
        PaceParameterEntity(
            key = "social_visible_sec",
            label = "Expiración de ubicación en vivo",
            value = 60.0,
            unit = "s",
            source = "Decisión de diseño de privacidad (RNF10 · social.visible_segundos)",
            status = "DISENO",
            updatedAt = now,
        ),
    )

    fun categoryFor(key: String): ParameterCategory = when {
        key.startsWith("water_") || key == "altitude" -> ParameterCategory.HYDRATION
        key.startsWith("uv_") -> ParameterCategory.UV
        key.startsWith("calories_") -> ParameterCategory.CALORIES
        key.startsWith("social_") -> ParameterCategory.SOCIAL
        else -> ParameterCategory.HYDRATION
    }

    fun helperFor(key: String): String = when (key) {
        "water_base" -> "Multiplica tu peso corporal para obtener la base diaria de líquidos."
        "water_per_kcal" -> "Suma líquido adicional según la energía estimada en tus caminatas o carreras."
        "water_sun_factor" -> "Ajusta la hidratación recomendada cuando la radiación solar es intensa."
        "water_altitude_extra" -> "Compensa la pérdida respiratoria extra por encima de 2.500 msnm."
        "altitude" -> "Altitud inicial usada cuando el teléfono no aporta barómetro o GPS."
        "uv_altitude_increase" -> "Incremento porcentual del índice UV por cada 1.000 m de altitud."
        "uv_ozone_dobson" -> "Columna de ozono en unidades Dobson para la estimación solar local."
        "uv_high" -> "A partir de este índice la app resalta la alerta de protección solar."
        "calories_f_alt" -> "Multiplicador sobre Mifflin-St Jeor (1.00 = sin alteración provisional)."
        "social_radius_m" -> "Distancia máxima para mostrar corredores visibles cerca de ti."
        "social_visible_sec" -> "Segundos antes de que tu ubicación aproximada expire automáticamente."
        else -> "Parámetro configurable guardado localmente en Room."
    }
}

// ============================================================================
// PF-005 · Motor de altitud, hidratación, UV y resumen diario
// ============================================================================

enum class UvRiskLevel(val label: String, val advice: String) {
    NIGHT("Sin radiación", "Es de noche o el sol está bajo el horizonte."),
    LOW("Bajo", "Exposición segura. Gafas de sol si hay brillo."),
    MODERATE("Moderado", "Usa bloqueador FPS 30+ si estarás más de 30 min al aire libre."),
    HIGH("Alto", "Usa bloqueador FPS 50+, gorra y busca sombra entre descansos."),
    VERY_HIGH("Muy alto", "Alerta OMS: bloqueador FPS 50+, manga larga y evita el sol directo prolongado."),
    EXTREME("Extremo", "Radiación intensa de altura: protección máxima y rehidrata con frecuencia."),
}

data class DailyHydrationEstimate(
    val baseWaterMl: Int,
    val altitudeExtraMl: Int,
    val activityExtraMl: Int,
    val solarExtraMl: Int,
    val totalTargetMl: Int,
    val electrolytesRecommended: Boolean,
    val electrolyteNote: String,
    val basalKcal: Int?,
    val totalEstimatedKcal: Int?,
    val uvIndex: Double,
    val uvLevel: UvRiskLevel,
    val uvAlertActive: Boolean,
    val uvSourceNote: String,
)

data class LaPazAltitudePreset(val label: String, val altitudeMeters: Int)

object LaPazAltitudePresets {
    val all = listOf(
        LaPazAltitudePreset("Zona Sur", 3250),
        LaPazAltitudePreset("Centro / Prado", 3600),
        LaPazAltitudePreset("Sopocachi / Miraflores", 3660),
        LaPazAltitudePreset("El Alto", 4050),
    )
}

class LaPazUvEstimator(
    private val altitudeMeters: Double = 3600.0,
    private val ozoneDobson: Double = 260.0,
    private val increasePer1000m: Double = 0.10,
) : UvProvider {
    override suspend fun currentUv(latitude: Double, longitude: Double): UvReading {
        val now = LocalTime.now()
        val hourDecimal = now.hour + now.minute / 60.0
        val value = estimateForHour(
            hourDecimal = hourDecimal,
            dayOfYear = LocalDate.now().dayOfYear,
            latitudeDeg = latitude,
            altitudeMeters = altitudeMeters,
            ozoneDobson = ozoneDobson,
            increasePer1000m = increasePer1000m,
        )
        return UvReading(
            index = value,
            observedAtEpochMillis = System.currentTimeMillis(),
            source = "Estimación solar por hora y altitud (cielo despejado)",
        )
    }

    companion object {
        fun estimateForHour(
            hourDecimal: Double,
            dayOfYear: Int = LocalDate.now().dayOfYear,
            latitudeDeg: Double = -16.500,
            altitudeMeters: Double = 3600.0,
            ozoneDobson: Double = 260.0,
            increasePer1000m: Double = 0.10,
        ): Double {
            val latRad = latitudeDeg * PI / 180.0
            val declDeg = 23.44 * sin(2.0 * PI * (284 + dayOfYear) / 365.0)
            val declRad = declDeg * PI / 180.0
            val hourAngleRad = ((hourDecimal - 12.33) * 15.0) * PI / 180.0
            val cosZenith = sin(latRad) * sin(declRad) + cos(latRad) * cos(declRad) * cos(hourAngleRad)
            if (cosZenith <= 0.01) return 0.0
            val safeOzone = ozoneDobson.coerceIn(180.0, 450.0)
            val baseSeaLevel = 12.5 * cosZenith.pow(2.42) * (safeOzone / 300.0).pow(-1.23)
            val altFactor = 1.0 + increasePer1000m.coerceIn(0.0, 0.25) * (altitudeMeters.coerceAtLeast(0.0) / 1000.0)
            return (baseSeaLevel * altFactor).coerceIn(0.0, 16.0)
        }

        fun classify(uv: Double): UvRiskLevel = when {
            uv < 0.5 -> UvRiskLevel.NIGHT
            uv < 3.0 -> UvRiskLevel.LOW
            uv < 6.0 -> UvRiskLevel.MODERATE
            uv < 8.0 -> UvRiskLevel.HIGH
            uv < 11.0 -> UvRiskLevel.VERY_HIGH
            else -> UvRiskLevel.EXTREME
        }
    }
}

object DailyHealthEngine {
    fun calculate(
        profile: HealthProfile?,
        parameters: List<PaceParameterEntity>,
        altitudeMeters: Int,
        activityMinutes: Int,
        uvHourDecimal: Double,
        uvOverrideValue: Double?,
        uvOverrideSource: String? = null,
    ): DailyHydrationEstimate {
        fun param(key: String, fallback: Double): Double =
            parameters.firstOrNull { it.key == key }?.value ?: fallback

        val waterBase = param("water_base", 35.0).coerceAtLeast(10.0)
        val waterPerKcal = param("water_per_kcal", 1.0).coerceAtLeast(0.0)
        val waterSunFactor = param("water_sun_factor", 0.03).coerceAtLeast(0.0)
        val waterAltExtraPer1000 = param("water_altitude_extra", 100.0).coerceAtLeast(0.0)
        val uvAltIncrease = param("uv_altitude_increase", 0.10).coerceAtLeast(0.0)
        val ozone = param("uv_ozone_dobson", 260.0)
        val uvAlertThreshold = param("uv_high", 8.0).coerceAtLeast(1.0)
        val fAltCalories = param("calories_f_alt", 1.00).coerceAtLeast(0.5)

        val uvValue = uvOverrideValue?.coerceIn(0.0, 20.0) ?: LaPazUvEstimator.estimateForHour(
            hourDecimal = uvHourDecimal,
            altitudeMeters = altitudeMeters.toDouble(),
            ozoneDobson = ozone,
            increasePer1000m = uvAltIncrease,
        )
        val uvLevel = LaPazUvEstimator.classify(uvValue)
        val uvSourceNote = when {
            uvOverrideSource != null -> uvOverrideSource
            uvOverrideValue != null -> "Valor ingresado manualmente"
            else -> "Estimación solar para $altitudeMeters msnm"
        }

        val weight = profile?.weightKg ?: 70.0
        val estimatedExerciseKcal = (activityMinutes * (weight * 0.095)).roundToInt()

        val baseWater = (weight * waterBase).roundToInt()
        val altAbove2500Km = max(0.0, (altitudeMeters - 2500.0) / 1000.0)
        val altitudeExtra = (altAbove2500Km * waterAltExtraPer1000).roundToInt()
        val activityExtra = (estimatedExerciseKcal * waterPerKcal).roundToInt()
        val solarExtra = ((baseWater * 0.5) * waterSunFactor * uvValue.coerceAtMost(12.0)).roundToInt()

        val totalTarget = baseWater + altitudeExtra + activityExtra + solarExtra

        val electrolytesRecommended = activityMinutes >= 60 || estimatedExerciseKcal >= 450
        val electrolyteNote = if (electrolytesRecommended) {
            "Actividad prolongada ($activityMinutes min): conviene reponer electrolitos junto con agua."
        } else {
            "Con menos de 60 min de actividad moderada suele bastar con agua."
        }

        val basalKcal = profile?.let { p ->
            val sexName = p.sex.name.uppercase()
            val sexLabel = p.sex.label.lowercase()
            val sexOffset = when {
                sexName.contains("FEMALE") || sexName.contains("WOMAN") || sexLabel.contains("muj") || sexLabel.contains("fem") -> -161.0
                sexName.contains("MALE") || sexName.contains("MAN") || sexLabel.contains("hom") || sexLabel.contains("masc") -> 5.0
                else -> -78.0
            }
            val rawTmb = (10.0 * p.weightKg) + (6.25 * p.heightCm) - (5.0 * p.age) + sexOffset
            (rawTmb * fAltCalories).roundToInt().coerceAtLeast(800)
        }

        return DailyHydrationEstimate(
            baseWaterMl = baseWater,
            altitudeExtraMl = altitudeExtra,
            activityExtraMl = activityExtra,
            solarExtraMl = solarExtra,
            totalTargetMl = totalTarget,
            electrolytesRecommended = electrolytesRecommended,
            electrolyteNote = electrolyteNote,
            basalKcal = basalKcal,
            totalEstimatedKcal = basalKcal?.plus(estimatedExerciseKcal),
            uvIndex = uvValue,
            uvLevel = uvLevel,
            uvAlertActive = uvValue >= uvAlertThreshold,
            uvSourceNote = uvSourceNote,
        )
    }
}

// ============================================================================
// PF-018 · Mis lugares guardados + Rutas recomendadas + Comparador A → B
// ============================================================================

data class SavedPlace(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int,
)

/**
 * Guarda múltiples puntos personalizados del usuario (Mi casa, Casa tía, Trabajo, etc.)
 * localmente en el teléfono sin modificar el esquema Room v2.
 */
object SavedPlacesStorage {
    private const val PREFS = "pacefit_saved_places_prefs"
    private const val KEY_LIST = "places_json"
    private const val LEGACY_PREFS = "pacefit_home_location_prefs"

    fun load(context: Context): List<SavedPlace> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, null)
        if (raw != null) {
            return parseJson(raw)
        }

        // Migra automáticamente si el usuario ya tenía guardada "Mi casa" en la versión previa
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.getBoolean("saved", false)) {
            val migrated = listOf(
                SavedPlace(
                    id = "saved_home",
                    label = legacy.getString("label", "Mi casa") ?: "Mi casa",
                    latitude = Double.fromBits(legacy.getLong("lat", 0L)),
                    longitude = Double.fromBits(legacy.getLong("lng", 0L)),
                    altitudeMeters = legacy.getInt("alt", 3600),
                ),
            )
            saveAll(context, migrated)
            return migrated
        }
        return emptyList()
    }

    fun addOrUpdate(context: Context, place: SavedPlace): Pair<List<SavedPlace>, SavedPlace> {
        val current = load(context).toMutableList()
        val existingIdx = current.indexOfFirst {
            it.id == place.id || it.label.equals(place.label, ignoreCase = true)
        }
        val finalPlace = if (existingIdx >= 0) {
            val kept = place.copy(id = current[existingIdx].id)
            current[existingIdx] = kept
            kept
        } else {
            current.add(0, place)
            place
        }
        saveAll(context, current)
        return current to finalPlace
    }

    fun remove(context: Context, placeId: String): List<SavedPlace> {
        val updated = load(context).filterNot { it.id == placeId }
        saveAll(context, updated)
        return updated
    }

    private fun saveAll(context: Context, places: List<SavedPlace>) {
        val array = JSONArray()
        places.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("lat", p.latitude)
                    .put("lng", p.longitude)
                    .put("alt", p.altitudeMeters),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, array.toString())
            .apply()
    }

    private fun parseJson(raw: String): List<SavedPlace> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    SavedPlace(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lng"),
                        altitudeMeters = obj.getInt("alt"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

enum class ActivityGoal(val label: String) {
    ALL("Todas"),
    WALK("Caminar"),
    JOG("Trotar"),
    RUN("Correr"),
}

data class RecommendedRoute(
    val id: String,
    val name: String,
    val zone: String,
    val description: String,
    val tip: String,
    val distanceMeters: Int,
    val elevationGainMeters: Int,
    val avgSlope: Double,
    val maxSlopePercent: Int,
    val altitudeMeters: Int,
    val latitude: Double,
    val longitude: Double,
    val waypoints: List<Pair<Double, Double>>,
    val terrainLabel: String,
    val goals: List<ActivityGoal>,
    val isFromHome: Boolean = false,
)

data class EstimatedRouteStats(
    val minutes: Int,
    val kcal: Int,
    val waterExtraMl: Int,
)

object LaPazRecommendedRoutes {
    val featured: List<RecommendedRoute> = listOf(
        RecommendedRoute(
            id = "rec_parque_urbano",
            name = "Parque Urbano Central",
            zone = "Centro · La Paz",
            description = "Circuito clásico y seguro lejos del tráfico intenso, ideal para trotar, correr o caminar a ritmo constante.",
            tip = "Excelente para dar 2 o 3 vueltas controlando respiración en altura; lleva agua si entrenas al mediodía.",
            distanceMeters = 1800,
            elevationGainMeters = 28,
            avgSlope = 0.02,
            maxSlopePercent = 5,
            altitudeMeters = 3570,
            latitude = -16.5018,
            longitude = -68.1242,
            waypoints = listOf(
                -16.5030 to -68.1256,
                -16.5010 to -68.1251,
                -16.5003 to -68.1232,
                -16.5018 to -68.1219,
                -16.5035 to -68.1236,
                -16.5030 to -68.1256,
            ),
            terrainLabel = "Terreno suave",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG, ActivityGoal.RUN),
        ),
        RecommendedRoute(
            id = "rec_el_prado",
            name = "El Prado · Av. 16 de Julio",
            zone = "Centro · La Paz",
            description = "Paseo amplio y casi plano desde Plaza Venezuela hasta Plaza del Estudiante, perfecto para empezar sin fatiga.",
            tip = "Ideal para caminata activa o trote de recuperación; evita horas pico por mayor flujo peatonal.",
            distanceMeters = 2000,
            elevationGainMeters = 22,
            avgSlope = 0.015,
            maxSlopePercent = 3,
            altitudeMeters = 3600,
            latitude = -16.5042,
            longitude = -68.1306,
            waypoints = listOf(
                -16.5010 to -68.1332,
                -16.5026 to -68.1319,
                -16.5042 to -68.1306,
                -16.5056 to -68.1294,
                -16.5068 to -68.1282,
            ),
            terrainLabel = "Terreno plano",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG),
        ),
        RecommendedRoute(
            id = "rec_av_poeta",
            name = "Av. del Poeta · Parque Laikacota",
            zone = "Centro / Miraflores",
            description = "Ruta escénica con inclinación progresiva y buena vista; muy elegida para trote matutino.",
            tip = "El tramo de ida sube suavemente; mantén pasos cortos para cuidar el pulso a 3.570 m.",
            distanceMeters = 2600,
            elevationGainMeters = 62,
            avgSlope = 0.035,
            maxSlopePercent = 7,
            altitudeMeters = 3565,
            latitude = -16.5065,
            longitude = -68.1220,
            waypoints = listOf(
                -16.5138 to -68.1208,
                -16.5105 to -68.1211,
                -16.5072 to -68.1219,
                -16.5042 to -68.1228,
                -16.5019 to -68.1241,
            ),
            terrainLabel = "Subida suave",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG, ActivityGoal.RUN),
        ),
        RecommendedRoute(
            id = "rec_estadio_miraflores",
            name = "Circuito Estadio Hernando Siles",
            zone = "Miraflores",
            description = "Anillo urbano alrededor de la plaza y estadio, ideal para correr series o mantener ritmo parejo.",
            tip = "Buena iluminación al atardecer; combina 1 vuelta de calentamiento caminando y 2 de trote.",
            distanceMeters = 2200,
            elevationGainMeters = 38,
            avgSlope = 0.025,
            maxSlopePercent = 6,
            altitudeMeters = 3645,
            latitude = -16.4995,
            longitude = -68.1227,
            waypoints = listOf(
                -16.4981 to -68.1238,
                -16.4979 to -68.1214,
                -16.5008 to -68.1212,
                -16.5010 to -68.1237,
                -16.4981 to -68.1238,
            ),
            terrainLabel = "Terreno suave",
            goals = listOf(ActivityGoal.JOG, ActivityGoal.RUN),
        ),
        RecommendedRoute(
            id = "rec_sopocachi",
            name = "Sopocachi · Abaroa a Plaza España",
            zone = "Sopocachi",
            description = "Recorrido arbolado con cuestas moderadas para fortalecer piernas sin pendientes extremas.",
            tip = "Sube por Av. Sánchez Lima para evitar las calles transversales más empinadas.",
            distanceMeters = 1600,
            elevationGainMeters = 74,
            avgSlope = 0.055,
            maxSlopePercent = 10,
            altitudeMeters = 3670,
            latitude = -16.5117,
            longitude = -68.1319,
            waypoints = listOf(
                -16.5096 to -68.1322,
                -16.5117 to -68.1319,
                -16.5136 to -68.1310,
                -16.5152 to -68.1292,
                -16.5142 to -68.1275,
            ),
            terrainLabel = "Subida moderada",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG),
        ),
        RecommendedRoute(
            id = "rec_costanera_sur",
            name = "Av. Costanera · Calacoto",
            zone = "Zona Sur · La Paz",
            description = "Ruta larga, plana y a menor altitud (~3.260 m), ideal para distancias mayores o correr más rápido.",
            tip = "Al estar ~350 m más abajo que el centro, notarás mejor recuperación respiratoria.",
            distanceMeters = 3500,
            elevationGainMeters = 35,
            avgSlope = 0.015,
            maxSlopePercent = 4,
            altitudeMeters = 3260,
            latitude = -16.5390,
            longitude = -68.0860,
            waypoints = listOf(
                -16.5342 to -68.0940,
                -16.5368 to -68.0902,
                -16.5390 to -68.0860,
                -16.5415 to -68.0812,
                -16.5432 to -68.0765,
            ),
            terrainLabel = "Terreno plano",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG, ActivityGoal.RUN),
        ),
        RecommendedRoute(
            id = "rec_el_alto_panoramica",
            name = "Av. Panorámica · Faro Murillo",
            zone = "El Alto",
            description = "Entrenamiento en gran altitud (~4.010 m) con vista a la ciudad y tramos amplios.",
            tip = "A más de 4.000 m el aire es muy seco y el UV es alto: hidrátate antes de salir y usa bloqueador.",
            distanceMeters = 2400,
            elevationGainMeters = 52,
            avgSlope = 0.03,
            maxSlopePercent = 7,
            altitudeMeters = 4010,
            latitude = -16.5105,
            longitude = -68.1494,
            waypoints = listOf(
                -16.5046 to -68.1558,
                -16.5076 to -68.1524,
                -16.5105 to -68.1494,
                -16.5136 to -68.1480,
                -16.5162 to -68.1502,
            ),
            terrainLabel = "Subida suave",
            goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG, ActivityGoal.RUN),
        ),
    )

    fun estimateAltitudeAt(latitude: Double, longitude: Double): Int = when {
        longitude < -68.145 -> 4030 // El Alto
        latitude < -16.528 -> 3260  // Zona Sur
        longitude > -68.116 -> 3680 // Laderas Este (Kupini / San Antonio / Pampahasi)
        else -> 3600                // Centro / Laderas centrales
    }

    fun buildLocalCircuitsAround(
        label: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Int,
    ): List<RecommendedRoute> {
        fun loopAround(distanceMeters: Int, latDir: Double, lngDir: Double): List<Pair<Double, Double>> {
            val sideLat = (distanceMeters / 4.0) / 111_000.0
            val sideLng = (distanceMeters / 4.0) / 106_000.0
            return listOf(
                latitude to longitude,
                (latitude + sideLat * latDir) to (longitude + sideLng * 0.25 * lngDir),
                (latitude + sideLat * latDir) to (longitude + sideLng * lngDir),
                (latitude + sideLat * 0.25 * latDir) to (longitude + sideLng * lngDir),
                latitude to longitude,
            )
        }

        return listOf(
            RecommendedRoute(
                id = "home_walk",
                name = "Caminata suave cerca de $label",
                zone = "Desde $label · $altitudeMeters m",
                description = "Vuelta de ida y regreso por calles cercanas evitando subidas bruscas, ideal para activarte hoy.",
                tip = "Mantén un paso cómodo donde puedas conversar sin agitarte.",
                distanceMeters = 1500,
                elevationGainMeters = 24,
                avgSlope = 0.02,
                maxSlopePercent = 5,
                altitudeMeters = altitudeMeters,
                latitude = latitude,
                longitude = longitude,
                waypoints = loopAround(1500, latDir = 1.0, lngDir = 1.0),
                terrainLabel = "Suave / local",
                goals = listOf(ActivityGoal.WALK),
                isFromHome = true,
            ),
            RecommendedRoute(
                id = "home_jog",
                name = "Trote constante cerca de $label",
                zone = "Desde $label · $altitudeMeters m",
                description = "Circuito de 3 km saliendo desde este punto, calibrado para tu altitud ($altitudeMeters msnm).",
                tip = "Empieza 5 min caminando para aclimatar la respiración antes de trotar.",
                distanceMeters = 3000,
                elevationGainMeters = 58,
                avgSlope = 0.035,
                maxSlopePercent = 8,
                altitudeMeters = altitudeMeters,
                latitude = latitude,
                longitude = longitude,
                waypoints = loopAround(3000, latDir = -1.0, lngDir = 1.0),
                terrainLabel = "Desnivel moderado",
                goals = listOf(ActivityGoal.JOG, ActivityGoal.RUN),
                isFromHome = true,
            ),
            RecommendedRoute(
                id = "home_climb",
                name = "Entrenamiento en cuesta cerca de $label",
                zone = "Desde $label · $altitudeMeters m",
                description = "Trayecto corto con subida para trabajar fuerza de piernas y resistencia en altura.",
                tip = "Sube controlado y recupera bajando despacio para cuidar rodillas.",
                distanceMeters = 2200,
                elevationGainMeters = 110,
                avgSlope = 0.075,
                maxSlopePercent = 13,
                altitudeMeters = altitudeMeters,
                latitude = latitude,
                longitude = longitude,
                waypoints = loopAround(2200, latDir = 1.0, lngDir = -1.0),
                terrainLabel = "Subida exigente",
                goals = listOf(ActivityGoal.WALK, ActivityGoal.JOG, ActivityGoal.RUN),
                isFromHome = true,
            ),
        )
    }

    fun estimateStats(
        route: RecommendedRoute,
        weightKg: Double,
        selectedGoal: ActivityGoal,
    ): EstimatedRouteStats {
        val effectiveGoal = if (selectedGoal == ActivityGoal.ALL) {
            route.goals.firstOrNull() ?: ActivityGoal.WALK
        } else {
            selectedGoal
        }
        val slope = route.avgSlope.coerceIn(-0.45, 0.45)
        val walkSpeed = LaPazStreetGraphProvider.toblerSpeedKmh(slope).coerceAtLeast(1.5)
        val speedKmh = when (effectiveGoal) {
            ActivityGoal.RUN -> walkSpeed * 1.95
            ActivityGoal.JOG -> walkSpeed * 1.45
            else -> walkSpeed
        }
        val minutes = (((route.distanceMeters / 1000.0) / speedKmh) * 60.0).roundToInt().coerceAtLeast(5)
        val costPerMeter = when (effectiveGoal) {
            ActivityGoal.RUN, ActivityGoal.JOG -> minettiRunningCost(slope)
            else -> LaPazStreetGraphProvider.minettiWalkingCost(slope)
        }
        val kcal = (costPerMeter * weightKg * route.distanceMeters * 0.000239006).roundToInt().coerceAtLeast(25)
        val altBonus = if (route.altitudeMeters > 3800) 100 else 50
        val waterExtra = (kcal + altBonus).coerceAtLeast(150)
        return EstimatedRouteStats(minutes = minutes, kcal = kcal, waterExtraMl = waterExtra)
    }

    private fun minettiRunningCost(slope: Double): Double {
        val i = slope.coerceIn(-0.45, 0.45)
        val cost = 155.4 * i.pow(5) - 30.4 * i.pow(4) - 43.3 * i.pow(3) +
                46.3 * i.pow(2) + 19.5 * i + 3.6
        return max(1.0, cost)
    }

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLng = (lng2 - lng1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).roundToInt()
    }

    fun formatDistanceFromUser(meters: Int): String = when {
        meters < 150 -> "Aquí mismo"
        meters < 1000 -> "A $meters m"
        else -> "A ${"%.1f".format(meters / 1000.0)} km"
    }
}

data class LaPazPlace(
    val id: String,
    val name: String,
    val zone: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Int,
)

data class RouteOption(
    val title: String,
    val subtitle: String,
    val viaStreets: List<String>,
    val placeNames: List<String>,
    val coordinates: List<Pair<Double, Double>>,
    val distanceMeters: Int,
    val elevationGainMeters: Int,
    val elevationLossMeters: Int,
    val maxSlopePercent: Int,
    val terrainLabel: String,
    val estimatedMinutes: Int,
    val estimatedKcal: Int,
    val elevationProfile: List<Double>,
)

data class RouteComparison(
    val origin: LaPazPlace,
    val destination: LaPazPlace,
    val shortest: RouteOption,
    val gentlest: RouteOption,
    val sameRoute: Boolean,
    val savingsSummary: String,
)

private data class LaPazCorridor(
    val fromId: String,
    val toId: String,
    val streetName: String,
    val distanceMeters: Double,
    val slope: Double,
    val midpoints: List<Pair<Double, Double>> = emptyList(),
)

class LaPazStreetGraphProvider : StreetGraphProvider {
    val places: List<LaPazPlace> = listOf(
        LaPazPlace("parque_urbano", "Parque Urbano Central", "Centro", -16.5018, -68.1242, 3570),
        LaPazPlace("el_prado", "El Prado (UMSA)", "Centro", -16.5042, -68.1306, 3600),
        LaPazPlace("plaza_estudiante", "Plaza del Estudiante", "Centro", -16.5068, -68.1282, 3585),
        LaPazPlace("plaza_abaroa", "Plaza Abaroa", "Sopocachi", -16.5117, -68.1319, 3655),
        LaPazPlace("plaza_espana", "Plaza España", "Sopocachi", -16.5152, -68.1292, 3690),
        LaPazPlace("san_jorge", "San Jorge (Av. Arce)", "San Jorge", -16.5150, -68.1219, 3535),
        LaPazPlace("miraflores", "Estadio Hernando Siles", "Miraflores", -16.4995, -68.1227, 3645),
        LaPazPlace("plaza_murillo", "Plaza Murillo", "Casco Viejo", -16.4957, -68.1335, 3685),
        LaPazPlace("ceja_el_alto", "La Ceja (El Alto)", "El Alto", -16.5046, -68.1636, 4085),
        LaPazPlace("ciudad_satelite", "Ciudad Satélite", "El Alto", -16.5165, -68.1538, 4015),
        LaPazPlace("faro_murillo", "Faro Murillo", "El Alto", -16.5105, -68.1494, 3980),
    )

    private val corridors: List<LaPazCorridor> = listOf(
        LaPazCorridor("el_prado", "plaza_estudiante", "Av. 16 de Julio", 420.0, -0.02, listOf(-16.5055 to -68.1294)),
        LaPazCorridor("plaza_estudiante", "plaza_abaroa", "Calle Aspiazu (subida)", 580.0, 0.17, listOf(-16.5092 to -68.1298)),
        LaPazCorridor("el_prado", "plaza_abaroa", "Av. 20 de Octubre", 980.0, 0.04, listOf(-16.5082 to -68.1328)),
        LaPazCorridor("plaza_abaroa", "plaza_espana", "Av. Sánchez Lima", 490.0, 0.06, listOf(-16.5136 to -68.1310)),
        LaPazCorridor("plaza_estudiante", "plaza_espana", "Calle Belisario Salinas", 780.0, 0.16, listOf(-16.5110 to -68.1286)),
        LaPazCorridor("plaza_estudiante", "san_jorge", "Av. Arce", 940.0, -0.045, listOf(-16.5112 to -68.1248)),
        LaPazCorridor("san_jorge", "plaza_espana", "Av. 6 de Agosto (borde)", 1050.0, 0.05, listOf(-16.5162 to -68.1258)),
        LaPazCorridor("san_jorge", "parque_urbano", "Av. del Poeta", 960.0, 0.03, listOf(-16.5084 to -68.1216)),
        LaPazCorridor("el_prado", "parque_urbano", "Av. Camacho / Simón Bolívar", 680.0, -0.025, listOf(-16.5028 to -68.1274)),
        LaPazCorridor("parque_urbano", "miraflores", "Av. Saavedra (suave)", 790.0, 0.04, listOf(-16.5007 to -68.1216)),
        LaPazCorridor("el_prado", "miraflores", "Calle Bueno (subida directa)", 890.0, 0.165, listOf(-16.5015 to -68.1268)),
        LaPazCorridor("el_prado", "plaza_murillo", "Calle Socabaya (empinada)", 710.0, 0.19, listOf(-16.4998 to -68.1333)),
        LaPazCorridor("parque_urbano", "plaza_murillo", "Av. Camacho → Ayacucho", 1020.0, 0.045, listOf(-16.4988 to -68.1288)),
        LaPazCorridor("miraflores", "plaza_murillo", "Av. Yungas (borde suave)", 1080.0, 0.035, listOf(-16.4962 to -68.1275)),
        LaPazCorridor("ceja_el_alto", "faro_murillo", "Av. Panorámica (suave)", 1250.0, -0.04, listOf(-16.5076 to -68.1558)),
        LaPazCorridor("ceja_el_alto", "ciudad_satelite", "Av. 6 de Marzo → Satélite", 1550.0, -0.03, listOf(-16.5112 to -68.1592)),
        LaPazCorridor("ciudad_satelite", "faro_murillo", "Av. Satélite", 880.0, -0.025, listOf(-16.5135 to -68.1512)),
        LaPazCorridor("faro_murillo", "plaza_murillo", "Bajada Buenos Aires (empinada)", 1650.0, -0.175, listOf(-16.5028 to -68.1415)),
        LaPazCorridor("ciudad_satelite", "plaza_abaroa", "Av. Cotahuma / Llojeta (borde)", 2150.0, -0.11, listOf(-16.5150 to -68.1426)),
    )

    private fun buildDirectedCorridors(activeExtras: List<LaPazPlace>): List<LaPazCorridor> {
        val dynamicCorridors = mutableListOf<LaPazCorridor>()

        // Conecta cada punto propio (origen o destino) con los 3 nodos fijos más cercanos
        activeExtras.forEach { extra ->
            places.sortedBy { base ->
                LaPazRecommendedRoutes.haversineMeters(
                    extra.latitude,
                    extra.longitude,
                    base.latitude,
                    base.longitude,
                )
            }.take(3).forEach { target ->
                val straightMeters = LaPazRecommendedRoutes.haversineMeters(
                    extra.latitude,
                    extra.longitude,
                    target.latitude,
                    target.longitude,
                )
                val streetMeters = max(120.0, straightMeters * 1.25)
                val rawSlope = (target.elevationMeters - extra.elevationMeters) / streetMeters
                val clampedSlope = rawSlope.coerceIn(-0.22, 0.22)
                dynamicCorridors.add(
                    LaPazCorridor(
                        fromId = extra.id,
                        toId = target.id,
                        streetName = "Tramo ${extra.name} ↔ ${target.name}",
                        distanceMeters = streetMeters,
                        slope = clampedSlope,
                    ),
                )
            }
        }

        // Si origen y destino son ambos puntos guardados / GPS (ej. Mi casa -> Casa tía),
        // añade una conexión directa y deja que la red vial actúe como alternativa suave
        if (activeExtras.size == 2) {
            val a = activeExtras[0]
            val b = activeExtras[1]
            val straight = LaPazRecommendedRoutes.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (straight >= 80) {
                val directMeters = straight * 1.15
                val directSlope = ((b.elevationMeters - a.elevationMeters) / directMeters).coerceIn(-0.22, 0.22)
                val midLat = (a.latitude + b.latitude) / 2.0
                val midLng = (a.longitude + b.longitude) / 2.0
                dynamicCorridors.add(
                    LaPazCorridor(
                        fromId = a.id,
                        toId = b.id,
                        streetName = "Conexión directa ${a.name} ↔ ${b.name}",
                        distanceMeters = directMeters,
                        slope = directSlope,
                        midpoints = listOf(midLat to midLng),
                    ),
                )
            }
        }

        return (corridors + dynamicCorridors).flatMap { edge ->
            listOf(
                edge,
                LaPazCorridor(
                    fromId = edge.toId,
                    toId = edge.fromId,
                    streetName = edge.streetName,
                    distanceMeters = edge.distanceMeters,
                    slope = -edge.slope,
                    midpoints = edge.midpoints.reversed(),
                ),
            )
        }
    }

    override suspend fun loadGraph(
        origin: Pair<Double, Double>,
        destination: Pair<Double, Double>,
    ): StreetGraph {
        val nodes = places.map {
            StreetNode(it.id, it.latitude, it.longitude, it.elevationMeters.toDouble())
        }
        val edges = buildDirectedCorridors(emptyList()).map {
            StreetEdge(it.fromId, it.toId, it.distanceMeters, it.slope)
        }
        return StreetGraph(nodes, edges)
    }

    fun comparePlaces(
        originId: String,
        destinationId: String,
        weightKg: Double,
        extraPlaces: List<LaPazPlace> = emptyList(),
    ): RouteComparison? {
        if (originId == destinationId) return null
        val allPlaces = (extraPlaces + places).distinctBy { it.id }
        val origin = allPlaces.firstOrNull { it.id == originId } ?: return null
        val destination = allPlaces.firstOrNull { it.id == destinationId } ?: return null

        val straightDistance = LaPazRecommendedRoutes.haversineMeters(
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude,
        )
        if (straightDistance < 40) return null

        val activeExtras = extraPlaces.filter { it.id == originId || it.id == destinationId }
        val directed = buildDirectedCorridors(activeExtras)

        val shortestEdges = dijkstra(originId, destinationId, allPlaces, directed, byEffort = false) ?: return null
        val gentlestEdges = dijkstra(originId, destinationId, allPlaces, directed, byEffort = true) ?: return null

        val shortest = buildOption(
            title = "Ruta más corta",
            subtitle = "Minimiza la distancia total",
            origin = origin,
            allPlaces = allPlaces,
            edges = shortestEdges,
            weightKg = weightKg,
        )
        val gentlest = buildOption(
            title = "Ruta más suave",
            subtitle = "Recomendada · menor esfuerzo en altura",
            origin = origin,
            allPlaces = allPlaces,
            edges = gentlestEdges,
            weightKg = weightKg,
        )

        val same = shortestEdges.map { it.streetName } == gentlestEdges.map { it.streetName }
        val savedClimb = shortest.elevationGainMeters - gentlest.elevationGainMeters
        val savedKcal = shortest.estimatedKcal - gentlest.estimatedKcal
        val extraMin = gentlest.estimatedMinutes - shortest.estimatedMinutes

        val summary = when {
            same -> "Para este trayecto la ruta más corta ya es también la más suave disponible (${gentlest.terrainLabel.lowercase()})."
            savedClimb > 0 && savedKcal > 0 -> {
                val timePart = if (extraMin > 0) "por solo $extraMin min más" else "y además llegas igual o más rápido"
                "La ruta suave ahorra $savedClimb m de subida exigente y ~$savedKcal kcal $timePart."
            }
            savedKcal > 0 -> "Ahorras ~$savedKcal kcal al evitar las calles con mayor pendiente."
            else -> "Reduce la inclinación máxima (${shortest.maxSlopePercent}% → ${gentlest.maxSlopePercent}%) para caminar con ritmo más cómodo."
        }

        return RouteComparison(
            origin = origin,
            destination = destination,
            shortest = shortest,
            gentlest = gentlest,
            sameRoute = same,
            savingsSummary = summary,
        )
    }

    private fun dijkstra(
        startId: String,
        endId: String,
        allPlaces: List<LaPazPlace>,
        edges: List<LaPazCorridor>,
        byEffort: Boolean,
    ): List<LaPazCorridor>? {
        val nodeIds = allPlaces.map { it.id }
        val dist = nodeIds.associateWith { Double.POSITIVE_INFINITY }.toMutableMap()
        val prevNode = mutableMapOf<String, String>()
        val prevEdge = mutableMapOf<String, LaPazCorridor>()
        val visited = mutableSetOf<String>()
        dist[startId] = 0.0

        while (visited.size < nodeIds.size) {
            val current = dist.filterKeys { it !in visited }.minByOrNull { it.value }?.key ?: break
            if (dist[current] == Double.POSITIVE_INFINITY) break
            if (current == endId) break
            visited += current

            edges.filter { it.fromId == current }.forEach { edge ->
                val weight = if (byEffort) {
                    val costPerMeter = minettiWalkingCost(edge.slope)
                    val steepnessComfort = if (edge.slope > 0.12) 1.20 else 1.0
                    edge.distanceMeters * costPerMeter * steepnessComfort
                } else {
                    edge.distanceMeters
                }
                val candidate = (dist[current] ?: Double.POSITIVE_INFINITY) + weight
                if (candidate < (dist[edge.toId] ?: Double.POSITIVE_INFINITY)) {
                    dist[edge.toId] = candidate
                    prevNode[edge.toId] = current
                    prevEdge[edge.toId] = edge
                }
            }
        }

        if (dist[endId] == Double.POSITIVE_INFINITY) return null
        val path = mutableListOf<LaPazCorridor>()
        var cursor = endId
        while (cursor != startId) {
            val edge = prevEdge[cursor] ?: return null
            path.add(edge)
            cursor = prevNode[cursor] ?: return null
        }
        path.reverse()
        return path
    }

    private fun buildOption(
        title: String,
        subtitle: String,
        origin: LaPazPlace,
        allPlaces: List<LaPazPlace>,
        edges: List<LaPazCorridor>,
        weightKg: Double,
    ): RouteOption {
        val placeById = allPlaces.associateBy { it.id }
        val placeNames = mutableListOf(origin.name)
        val coords = mutableListOf(origin.latitude to origin.longitude)
        var totalDistance = 0.0
        var totalGain = 0.0
        var totalLoss = 0.0
        var totalMinutes = 0.0
        var totalJoules = 0.0
        var maxSlope = 0.0

        var runningElevation = origin.elevationMeters.toDouble()
        val profilePoints = mutableListOf(runningElevation)

        edges.forEach { edge ->
            totalDistance += edge.distanceMeters
            val clampedSlope = edge.slope.coerceIn(-0.45, 0.45)
            val deltaElev = edge.distanceMeters * clampedSlope
            if (deltaElev > 0) totalGain += deltaElev else totalLoss += abs(deltaElev)
            runningElevation += deltaElev
            profilePoints.add(runningElevation)

            maxSlope = max(maxSlope, abs(clampedSlope))
            val speedKmh = toblerSpeedKmh(clampedSlope).coerceAtLeast(1.0)
            totalMinutes += ((edge.distanceMeters / 1000.0) / speedKmh) * 60.0
            totalJoules += minettiWalkingCost(clampedSlope) * weightKg * edge.distanceMeters

            coords.addAll(edge.midpoints)
            placeById[edge.toId]?.let { nextPlace ->
                placeNames.add(nextPlace.name)
                coords.add(nextPlace.latitude to nextPlace.longitude)
            }
        }

        val netDelta = totalGain - totalLoss
        val terrainLabel = when {
            netDelta >= 45.0 && maxSlope >= 0.12 -> "Subida exigente"
            netDelta >= 20.0 -> "Subida suave"
            netDelta <= -45.0 -> "Bajada pronunciada"
            netDelta <= -20.0 -> "Mayormente bajada"
            else -> "Terreno plano / mixto"
        }

        val kcal = (totalJoules * 0.000239006).roundToInt().coerceAtLeast(1)
        return RouteOption(
            title = title,
            subtitle = subtitle,
            viaStreets = edges.map { it.streetName },
            placeNames = placeNames,
            coordinates = coords,
            distanceMeters = totalDistance.roundToInt(),
            elevationGainMeters = totalGain.roundToInt(),
            elevationLossMeters = totalLoss.roundToInt(),
            maxSlopePercent = (maxSlope * 100.0).roundToInt(),
            terrainLabel = terrainLabel,
            estimatedMinutes = totalMinutes.roundToInt().coerceAtLeast(1),
            estimatedKcal = kcal,
            elevationProfile = profilePoints,
        )
    }

    companion object {
        const val GPS_NODE_ID = "user_gps"

        fun minettiWalkingCost(slope: Double): Double {
            val i = slope.coerceIn(-0.45, 0.45)
            val cost = 280.5 * i.pow(5) - 58.7 * i.pow(4) - 76.8 * i.pow(3) +
                    51.9 * i.pow(2) + 19.6 * i + 2.5
            return max(0.5, cost)
        }

        fun toblerSpeedKmh(slope: Double): Double {
            val s = slope.coerceIn(-0.45, 0.45)
            return 6.0 * exp(-3.5 * abs(s + 0.05))
        }
    }
}

// ============================================================================
// PF-013 · Repositorio social local (implementa SocialRepository, listo para Supabase)
// ============================================================================

data class NearbyRunnerCard(
    val summary: RunnerSummary,
    val approxDistanceMeters: Int,
    val initials: String,
    val requestSent: Boolean,
    val isMatched: Boolean,
)

data class IncomingRequestCard(
    val request: ConnectionRequest,
    val fromName: String,
    val fromInitials: String,
    val activityLabel: String,
)

data class MatchConversation(
    val conversationId: String,
    val peerUserId: String,
    val peerName: String,
    val peerInitials: String,
    val peerActivity: String,
    val messages: List<SocialMessage>,
)

data class SocialPreviewState(
    val visibleWhileRunning: Boolean = false,
    val visibilitySecondsRemaining: Int = 0,
    val approximateCellLabel: String = "-16.504, -68.130 (~100 m)",
    val nearbyRunners: List<NearbyRunnerCard> = emptyList(),
    val incomingRequests: List<IncomingRequestCard> = emptyList(),
    val conversations: List<MatchConversation> = emptyList(),
    val blockedUserIds: Set<String> = emptySet(),
    val ignoredUserIds: Set<String> = emptySet(),
    val reportedCount: Int = 0,
)

class LocalPreviewSocialRepository : SocialRepository {
    private val currentUserId = "me_local"

    private val seedRunners = listOf(
        Triple(RunnerSummary("u_camila", "Camila R.", -16.504, -68.131, "Trotando · El Prado"), 240, "CR"),
        Triple(RunnerSummary("u_mateo", "Mateo T.", -16.508, -68.129, "Caminata rápida · Sopocachi"), 490, "MT"),
        Triple(RunnerSummary("u_valeria", "Valeria Q.", -16.501, -68.124, "Subida suave · Parque Urbano"), 760, "VQ"),
        Triple(RunnerSummary("u_diego", "Diego M.", -16.512, -68.130, "Entrenando 5K · Plaza Abaroa"), 1120, "DM"),
    )

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SocialPreviewState> = _state.asStateFlow()

    private fun initialState(): SocialPreviewState {
        val now = System.currentTimeMillis()
        val initialConv = MatchConversation(
            conversationId = "match_sofia",
            peerUserId = "u_sofia",
            peerName = "Sofía C.",
            peerInitials = "SC",
            peerActivity = "Match activo · Av. Arce",
            messages = listOf(
                SocialMessage(
                    id = "m_1",
                    conversationId = "match_sofia",
                    senderId = "u_sofia",
                    body = "¡Hola! ¿Salimos a trotar suave por el Parque Urbano esta tarde?",
                    createdAtEpochMillis = now - 12 * 60_000L,
                ),
            ),
        )
        val initialIncoming = IncomingRequestCard(
            request = ConnectionRequest("req_valeria", "u_valeria", currentUserId, "pending"),
            fromName = "Valeria Q.",
            fromInitials = "VQ",
            activityLabel = "Subida suave · Parque Urbano",
        )
        val nearby = seedRunners.map { (summary, meters, initials) ->
            NearbyRunnerCard(summary, meters, initials, requestSent = false, isMatched = false)
        }
        return SocialPreviewState(
            nearbyRunners = nearby,
            incomingRequests = listOf(initialIncoming),
            conversations = listOf(initialConv),
        )
    }

    fun resetPreview() {
        val preservedCell = _state.value.approximateCellLabel
        _state.value = initialState().copy(approximateCellLabel = preservedCell)
    }

    fun updateApproximateCell(cellLabel: String) {
        _state.update { it.copy(approximateCellLabel = cellLabel) }
    }

    fun setVisible(enabled: Boolean, durationSeconds: Int) {
        _state.update {
            it.copy(
                visibleWhileRunning = enabled,
                visibilitySecondsRemaining = if (enabled) durationSeconds.coerceAtLeast(10) else 0,
            )
        }
    }

    fun tickVisibility() {
        _state.update { current ->
            if (!current.visibleWhileRunning) return@update current
            val next = (current.visibilitySecondsRemaining - 1).coerceAtLeast(0)
            current.copy(
                visibleWhileRunning = next > 0,
                visibilitySecondsRemaining = next,
            )
        }
    }

    fun isMessageBlockedByModeration(body: String): Boolean {
        val normalized = body.lowercase()
        val forbidden = listOf(
            "idiota", "estupido", "estúpido", "imbecil", "imbécil",
            "mierda", "puta", "pendejo", "basura", "carajo", "maldito",
        )
        return forbidden.any { normalized.contains(it) }
    }

    override suspend fun findNearbyRunners(approximateCell: String): List<RunnerSummary> {
        val current = _state.value
        return current.nearbyRunners
            .filter { it.summary.userId !in current.blockedUserIds && it.summary.userId !in current.ignoredUserIds }
            .map { it.summary }
    }

    override suspend fun sendConnectionRequest(userId: String): ConnectionRequest {
        val req = ConnectionRequest("req_${System.currentTimeMillis()}", currentUserId, userId, "pending")
        _state.update { current ->
            val mutual = current.incomingRequests.firstOrNull { it.request.fromUserId == userId }
            if (mutual != null) {
                val newMatch = MatchConversation(
                    conversationId = "match_$userId",
                    peerUserId = userId,
                    peerName = mutual.fromName,
                    peerInitials = mutual.fromInitials,
                    peerActivity = mutual.activityLabel,
                    messages = emptyList(),
                )
                current.copy(
                    incomingRequests = current.incomingRequests.filterNot { it.request.fromUserId == userId },
                    conversations = listOf(newMatch) + current.conversations.filterNot { it.peerUserId == userId },
                    nearbyRunners = current.nearbyRunners.map {
                        if (it.summary.userId == userId) it.copy(requestSent = true, isMatched = true) else it
                    },
                )
            } else {
                current.copy(
                    nearbyRunners = current.nearbyRunners.map {
                        if (it.summary.userId == userId) it.copy(requestSent = true) else it
                    },
                )
            }
        }
        return req
    }

    override suspend fun respondToRequest(requestId: String, accept: Boolean) {
        _state.update { current ->
            val target = current.incomingRequests.firstOrNull { it.request.id == requestId } ?: return@update current
            val remaining = current.incomingRequests.filterNot { it.request.id == requestId }
            if (accept) {
                val conv = MatchConversation(
                    conversationId = "match_${target.request.fromUserId}",
                    peerUserId = target.request.fromUserId,
                    peerName = target.fromName,
                    peerInitials = target.fromInitials,
                    peerActivity = target.activityLabel,
                    messages = emptyList(),
                )
                current.copy(
                    incomingRequests = remaining,
                    conversations = listOf(conv) + current.conversations.filterNot { it.peerUserId == target.request.fromUserId },
                    nearbyRunners = current.nearbyRunners.map {
                        if (it.summary.userId == target.request.fromUserId) it.copy(isMatched = true, requestSent = true) else it
                    },
                )
            } else {
                current.copy(
                    incomingRequests = remaining,
                    ignoredUserIds = current.ignoredUserIds + target.request.fromUserId,
                    nearbyRunners = current.nearbyRunners.filterNot { it.summary.userId == target.request.fromUserId },
                )
            }
        }
    }

    override suspend fun sendMessage(conversationId: String, body: String): SocialMessage {
        val clean = body.trim()
        val msg = SocialMessage(
            id = "m_${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = currentUserId,
            body = clean,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        _state.update { current ->
            current.copy(
                conversations = current.conversations.map { conv ->
                    if (conv.conversationId == conversationId) {
                        conv.copy(messages = conv.messages + msg)
                    } else {
                        conv
                    }
                },
            )
        }
        return msg
    }

    override suspend fun blockUser(userId: String) {
        _state.update { current ->
            current.copy(
                blockedUserIds = current.blockedUserIds + userId,
                nearbyRunners = current.nearbyRunners.filterNot { it.summary.userId == userId },
                incomingRequests = current.incomingRequests.filterNot { it.request.fromUserId == userId },
                conversations = current.conversations.filterNot { it.peerUserId == userId },
            )
        }
    }

    override suspend fun reportUser(userId: String, reason: String) {
        _state.update { it.copy(reportedCount = it.reportedCount + 1) }
    }
}