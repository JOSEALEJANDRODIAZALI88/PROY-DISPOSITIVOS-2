package com.pacefit.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pacefit.ai.data.PaceFitModulesDao
import com.pacefit.ai.data.PaceParameterEntity
import com.pacefit.ai.data.WaterLogEntity
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.modules.ActivityGoal
import com.pacefit.ai.modules.DailyHealthEngine
import com.pacefit.ai.modules.DeviceEnvironmentClient
import com.pacefit.ai.modules.DeviceLocationReading
import com.pacefit.ai.modules.DevicePermissionHelper
import com.pacefit.ai.modules.LaPazAltitudePresets
import com.pacefit.ai.modules.LaPazPlace
import com.pacefit.ai.modules.LaPazRecommendedRoutes
import com.pacefit.ai.modules.LaPazStreetGraphProvider
import com.pacefit.ai.modules.LiveWeatherSnapshot
import com.pacefit.ai.modules.LocalPreviewSocialRepository
import com.pacefit.ai.modules.LocationPermissionStatus
import com.pacefit.ai.modules.MatchConversation
import com.pacefit.ai.modules.OpenMeteoUvProvider
import com.pacefit.ai.modules.ParameterCatalogDefaults
import com.pacefit.ai.modules.ParameterCategory
import com.pacefit.ai.modules.RecommendedRoute
import com.pacefit.ai.modules.RouteOption
import com.pacefit.ai.modules.SavedPlace
import com.pacefit.ai.modules.SavedPlacesStorage
import com.pacefit.ai.modules.UvRiskLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val LimeAccent = Color(0xFFB9F66B)
private val WaterBlue = Color(0xFF38BDF8)
private val SoftEmerald = Color(0xFF34D399)
private val ShortAmber = Color(0xFFFBBF24)
private val AlertRed = Color(0xFFF87171)
private val UvOrange = Color(0xFFFB923C)
private val UvPurple = Color(0xFFC084FC)
private val MutedText = Color(0xFFBDC9CD)
private val CardBg = Color(0xFF19252D)
private val CardBgElevated = Color(0xFF22343E)

private data class PendingPlaceDraft(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int,
    val sourceNote: String,
    val suggestedName: String,
)

@Composable
fun Integrante3Screen(
    dao: PaceFitModulesDao,
    profile: HealthProfile?,
    padding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceClient = remember(context) { DeviceEnvironmentClient(context) }

    var section by rememberSaveable { mutableIntStateOf(0) }
    var parameters by remember { mutableStateOf(emptyList<PaceParameterEntity>()) }
    var waterLogs by remember { mutableStateOf(emptyList<WaterLogEntity>()) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    var permissionStatus by remember {
        mutableStateOf(DevicePermissionHelper.checkLocationPermission(context))
    }
    var locationReading by remember { mutableStateOf<DeviceLocationReading?>(null) }
    var savedPlaces by remember { mutableStateOf(SavedPlacesStorage.load(context)) }
    var liveWeather by remember { mutableStateOf<LiveWeatherSnapshot?>(null) }
    var detectingEnvironment by remember { mutableStateOf(false) }
    var permissionDeniedOnce by rememberSaveable { mutableStateOf(false) }

    val routeEngine = remember { LaPazStreetGraphProvider() }
    val socialRepo = remember { LocalPreviewSocialRepository() }

    val startDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val endDay = remember {
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun refreshEnvironment() {
        scope.launch {
            detectingEnvironment = true
            val reading = deviceClient.readCurrentLocationAndAltitude()
            if (reading != null) {
                locationReading = reading
                socialRepo.updateApproximateCell(reading.approxCellLabel)
                val ozone = parameters.firstOrNull { it.key == "uv_ozone_dobson" }?.value ?: 260.0
                val uvInc = parameters.firstOrNull { it.key == "uv_altitude_increase" }?.value ?: 0.10
                val uvProvider = OpenMeteoUvProvider(
                    altitudeMeters = reading.altitudeMeters.toDouble(),
                    ozoneDobson = ozone,
                    increasePer1000m = uvInc,
                )
                liveWeather = uvProvider.fetchWeatherSnapshot(reading.latitude, reading.longitude)
                feedbackBanner = "Detectado: ${reading.zoneLabel} (${reading.altitudeMeters} m)"
            } else {
                feedbackBanner = "No se obtuvo señal GPS todavía. Puedes elegir tu zona manualmente."
            }
            detectingEnvironment = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionStatus = DevicePermissionHelper.checkLocationPermission(context)
        if (permissionStatus != LocationPermissionStatus.DENIED) {
            permissionDeniedOnce = false
            refreshEnvironment()
        } else {
            permissionDeniedOnce = true
            feedbackBanner = "Permiso denegado: la app seguirá funcionando en modo manual."
        }
    }

    fun requestOrRefreshLocation() {
        permissionStatus = DevicePermissionHelper.checkLocationPermission(context)
        if (permissionStatus == LocationPermissionStatus.DENIED) {
            locationPermissionLauncher.launch(DevicePermissionHelper.locationPermissions)
        } else {
            refreshEnvironment()
        }
    }

    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(dao) {
        dao.observeParameters().collect { list ->
            if (!seeded) {
                seeded = true
                val existingKeys = list.map { it.key }.toSet()
                val missing = ParameterCatalogDefaults.buildDefaults().filter { it.key !in existingKeys }
                missing.forEach { dao.saveParameter(it) }
            }
            parameters = list
        }
    }

    LaunchedEffect(dao) {
        dao.observeWaterLogs(startDay, endDay).collect { waterLogs = it }
    }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(3200)
            feedbackBanner = null
        }
    }

    val socialState by socialRepo.state.collectAsState()
    val pendingBadge = socialState.incomingRequests.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "PACEFIT · LA PAZ Y EL ALTO",
                        color = LimeAccent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (section) {
                            0 -> "Tu día en altura"
                            1 -> "Rutas y mapa"
                            2 -> "Comunidad cercana"
                            else -> "Parámetros y fuentes"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = CardBgElevated,
                ) {
                    Text(
                        text = profile?.let { "${it.weightKg.roundToInt()} kg" } ?: "Sin perfil",
                        color = if (profile != null) LimeAccent else MutedText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val tabs = listOf("Hoy", "Rutas", "Social", "Ajustes")
            TabRow(
                selectedTabIndex = section,
                containerColor = Color.Transparent,
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = section == index,
                        onClick = { section = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (section == index) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (index == 2 && pendingBadge > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(LimeAccent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$pendingBadge",
                                            color = Color(0xFF101820),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        feedbackBanner?.let { banner ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = CardBgElevated,
            ) {
                Text(
                    text = banner,
                    color = LimeAccent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        when (section) {
            0 -> TodayDashboardSection(
                parameters = parameters,
                logs = waterLogs,
                profile = profile,
                dao = dao,
                permissionStatus = permissionStatus,
                permissionDeniedOnce = permissionDeniedOnce,
                locationReading = locationReading,
                liveWeather = liveWeather,
                detectingEnvironment = detectingEnvironment,
                onDetectLocation = ::requestOrRefreshLocation,
                onOpenAppSettings = { DevicePermissionHelper.openAppSettings(context) },
                onNotify = { feedbackBanner = it },
                onOpenSettings = { section = 3 },
            )
            1 -> GentleRoutesSection(
                provider = routeEngine,
                profile = profile,
                locationReading = locationReading,
                savedPlaces = savedPlaces,
                detectingLocation = detectingEnvironment,
                onDetectLocation = ::requestOrRefreshLocation,
                onSavePlace = { place ->
                    val (updatedList, saved) = SavedPlacesStorage.addOrUpdate(context, place)
                    savedPlaces = updatedList
                    feedbackBanner = "Lugar guardado: ${saved.label} (${saved.altitudeMeters} m)"
                    saved
                },
                onDeletePlace = { place ->
                    savedPlaces = SavedPlacesStorage.remove(context, place.id)
                    feedbackBanner = "Lugar eliminado: ${place.label}"
                },
                onNotify = { feedbackBanner = it },
            )
            2 -> SocialCommunitySection(
                repository = socialRepo,
                parameters = parameters,
                onRequestLocation = ::requestOrRefreshLocation,
                onNotify = { feedbackBanner = it },
            )
            else -> ParametersSettingsSection(
                items = parameters,
                dao = dao,
                onNotify = { feedbackBanner = it },
            )
        }
    }
}

// ============================================================================
// PESTAÑA 1: HOY (PF-005 · Ubicación/altitud real, clima/UV, hidratación)
// ============================================================================

@Composable
private fun TodayDashboardSection(
    parameters: List<PaceParameterEntity>,
    logs: List<WaterLogEntity>,
    profile: HealthProfile?,
    dao: PaceFitModulesDao,
    permissionStatus: LocationPermissionStatus,
    permissionDeniedOnce: Boolean,
    locationReading: DeviceLocationReading?,
    liveWeather: LiveWeatherSnapshot?,
    detectingEnvironment: Boolean,
    onDetectLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNotify: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultAlt = parameters.firstOrNull { it.key == "altitude" }?.value?.roundToInt() ?: 3600
    var selectedAltitude by rememberSaveable { mutableIntStateOf(defaultAlt) }
    var activityMinutes by rememberSaveable { mutableIntStateOf(30) }
    var uvTimePreset by rememberSaveable { mutableIntStateOf(0) }
    var manualUvText by rememberSaveable { mutableStateOf("") }
    var showCustomWaterDialog by remember { mutableStateOf(false) }
    var showManualUvField by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(locationReading) {
        locationReading?.let { selectedAltitude = it.altitudeMeters }
    }

    val uvHourDecimal = when (uvTimePreset) {
        1 -> 8.0
        2 -> 12.5
        3 -> 16.5
        else -> {
            val now = LocalTime.now()
            now.hour + now.minute / 60.0
        }
    }

    val manualUv = if (showManualUvField) manualUvText.replace(',', '.').toDoubleOrNull() else null
    val useLiveWeatherUv = !showManualUvField && uvTimePreset == 0 && liveWeather != null

    val estimate = remember(
        profile,
        parameters,
        selectedAltitude,
        activityMinutes,
        uvHourDecimal,
        manualUv,
        liveWeather,
        useLiveWeatherUv,
    ) {
        DailyHealthEngine.calculate(
            profile = profile,
            parameters = parameters,
            altitudeMeters = selectedAltitude,
            activityMinutes = activityMinutes,
            uvHourDecimal = uvHourDecimal,
            uvOverrideValue = when {
                manualUv != null -> manualUv
                useLiveWeatherUv -> liveWeather?.uvIndex
                else -> null
            },
            uvOverrideSource = when {
                manualUv != null -> "Valor ingresado manualmente"
                useLiveWeatherUv -> liveWeather?.sourceLabel
                else -> null
            },
        )
    }

    val totalWater = logs.sumOf { it.milliliters }
    val progress = (totalWater.toFloat() / max(1, estimate.totalTargetMl)).coerceIn(0f, 1f)
    val remaining = (estimate.totalTargetMl - totalWater).coerceAtLeast(0)

    fun logWater(ml: Int) {
        scope.launch {
            dao.addWaterLog(WaterLogEntity(milliliters = ml, loggedAt = System.currentTimeMillis()))
            onNotify("+$ml ml registrados")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            locationReading?.zoneLabel ?: "Ubicación y clima actual",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                locationReading != null -> "${locationReading.altitudeMeters} msnm · ${locationReading.altitudeSource}"
                                permissionStatus == LocationPermissionStatus.DENIED -> "Activa ubicación para detectar altitud (La Paz / El Alto) y clima real."
                                else -> "Toca para leer tu altitud y radiación UV real."
                            },
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    StatusPill(
                        text = when {
                            liveWeather?.isOnline == true -> "En vivo"
                            locationReading != null -> "Offline"
                            permissionStatus == LocationPermissionStatus.DENIED -> "Manual"
                            else -> "Listo"
                        },
                        color = when {
                            liveWeather?.isOnline == true -> SoftEmerald
                            locationReading != null -> WaterBlue
                            else -> ShortAmber
                        },
                    )
                }

                if (locationReading?.isLowPrecision == true) {
                    Text(
                        "Aviso: la altitud GPS sin barómetro puede variar varios metros.",
                        color = ShortAmber,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                liveWeather?.let { weather ->
                    if (weather.temperatureC != null || weather.cloudCoverPercent != null) {
                        val tempPart = weather.temperatureC?.let { "${it.roundToInt()} °C" } ?: "—"
                        val cloudPart = weather.cloudCoverPercent?.let { "$it% nubosidad" } ?: ""
                        Text(
                            "Clima actual: $tempPart${if (cloudPart.isNotBlank()) " · $cloudPart" else ""}",
                            color = WaterBlue,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDetectLocation,
                        enabled = !detectingEnvironment,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (detectingEnvironment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF101820),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Detectando...")
                        } else {
                            Text(
                                if (locationReading == null) "Detectar mi ubicación y clima" else "Actualizar lectura",
                            )
                        }
                    }
                    if (permissionDeniedOnce && permissionStatus == LocationPermissionStatus.DENIED) {
                        OutlinedButton(onClick = onOpenAppSettings) {
                            Text("Ajustes")
                        }
                    }
                }

                if (permissionDeniedOnce && permissionStatus == LocationPermissionStatus.DENIED) {
                    Text(
                        "Sin permiso de ubicación no podemos medir tu altitud ni el clima en vivo, pero puedes elegir La Paz o El Alto manualmente abajo.",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Hidratación de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (remaining == 0) "¡Meta diaria alcanzada!" else "Te faltan $remaining ml aprox.",
                            color = if (remaining == 0) SoftEmerald else MutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    StatusPill(text = "Estimación", color = WaterBlue)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    HydrationRing(
                        progress = progress,
                        consumedMl = totalWater,
                        targetMl = estimate.totalTargetMl,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BreakdownRow("Base por peso", "${estimate.baseWaterMl} ml")
                        BreakdownRow("Altura ($selectedAltitude m)", "+${estimate.altitudeExtraMl} ml")
                        BreakdownRow("Actividad ($activityMinutes min)", "+${estimate.activityExtraMl} ml")
                        BreakdownRow("Sol / UV", "+${estimate.solarExtraMl} ml")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { logWater(250) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Text("+250 ml", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { logWater(500) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Text("+500 ml", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showCustomWaterDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Text("Otro")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (estimate.electrolytesRecommended) ShortAmber.copy(alpha = 0.16f) else CardBgElevated,
                ) {
                    Text(
                        text = estimate.electrolyteNote,
                        color = if (estimate.electrolytesRecommended) ShortAmber else MutedText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }

                if (logs.isNotEmpty()) {
                    HorizontalDivider(color = CardBgElevated)
                    Text("Últimos registros de hoy", style = MaterialTheme.typography.labelMedium, color = MutedText)
                    val formatter = remember {
                        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                    }
                    logs.take(4).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "+${log.milliliters} ml · ${formatter.format(Instant.ofEpochMilli(log.loggedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        dao.deleteWaterLog(log.id)
                                        onNotify("Registro eliminado")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("Quitar", color = AlertRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        val uvColor = uvColorFor(estimate.uvLevel)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Radiación UV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(estimate.uvSourceNote, color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    StatusPill(text = estimate.uvLevel.label, color = uvColor)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(uvColor.copy(alpha = 0.18f))
                            .border(1.5.dp, uvColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "%.1f".format(estimate.uvIndex),
                            color = uvColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (estimate.uvAlertActive) {
                            Text(
                                "Alerta UV activa",
                                color = uvColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(estimate.uvLevel.advice, style = MaterialTheme.typography.bodySmall, color = MutedText)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Ahora", "8:00", "12:30", "16:30").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = !showManualUvField && uvTimePreset == idx,
                            onClick = {
                                showManualUvField = false
                                uvTimePreset = idx
                            },
                            label = { Text(label) },
                        )
                    }
                    FilterChip(
                        selected = showManualUvField,
                        onClick = { showManualUvField = !showManualUvField },
                        label = { Text("Manual") },
                    )
                }

                if (showManualUvField) {
                    OutlinedTextField(
                        value = manualUvText,
                        onValueChange = { manualUvText = it.take(5) },
                        label = { Text("Índice UV manual (ej. 10.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Efecto altura · La Paz y El Alto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Puedes cambiar la zona manualmente para comparar cómo sube el agua y el UV entre La Paz y El Alto.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LaPazAltitudePresets.all.forEach { preset ->
                        FilterChip(
                            selected = selectedAltitude == preset.altitudeMeters,
                            onClick = { selectedAltitude = preset.altitudeMeters },
                            label = { Text("${preset.label} · ${preset.altitudeMeters} m") },
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Actividad estimada hoy", style = MaterialTheme.typography.labelLarge)
                    Text("$activityMinutes min", color = LimeAccent, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = activityMinutes.toFloat(),
                    onValueChange = { activityMinutes = (it / 5f).roundToInt() * 5 },
                    valueRange = 0f..120f,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Resumen del día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onOpenSettings) {
                        Text("Ver fórmulas", color = LimeAccent)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricTile(
                        title = "Agua",
                        value = "$totalWater ml",
                        subtitle = "Meta ${estimate.totalTargetMl}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Calorías est.",
                        value = estimate.totalEstimatedKcal?.let { "$it" } ?: "—",
                        subtitle = if (estimate.basalKcal != null) "Basal + act." else "Crea perfil",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Altitud",
                        value = "$selectedAltitude m",
                        subtitle = "UV ${"%.1f".format(estimate.uvIndex)}",
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Todos los valores son estimaciones orientativas locales, no diagnóstico ni prescripción médica.",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showCustomWaterDialog) {
        var customAmount by remember { mutableStateOf("350") }
        AlertDialog(
            onDismissRequest = { showCustomWaterDialog = false },
            title = { Text("Registrar agua") },
            text = {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                    label = { Text("Cantidad en ml (ej. 350)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ml = customAmount.toIntOrNull()
                        if (ml == null || ml !in 10..5000) {
                            onNotify("Ingresa un valor entre 10 y 5000 ml")
                        } else {
                            logWater(ml)
                            showCustomWaterDialog = false
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomWaterDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

// ============================================================================
// PESTAÑA 2: RUTAS (PF-018 · Mis lugares guardados + Recomendadas + A → B exacto)
// ============================================================================

@Composable
private fun GentleRoutesSection(
    provider: LaPazStreetGraphProvider,
    profile: HealthProfile?,
    locationReading: DeviceLocationReading?,
    savedPlaces: List<SavedPlace>,
    detectingLocation: Boolean,
    onDetectLocation: () -> Unit,
    onSavePlace: (SavedPlace) -> SavedPlace,
    onDeletePlace: (SavedPlace) -> Unit,
    onNotify: (String) -> Unit,
) {
    var routesMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Cerca y recomendadas, 1 = Comparar A -> B
    var selectedGoal by rememberSaveable { mutableStateOf(ActivityGoal.ALL) }
    var activeSavedPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMapRouteId by rememberSaveable { mutableStateOf("rec_parque_urbano") }
    var selectedDetailRoute by remember { mutableStateOf<RecommendedRoute?>(null) }
    var pendingSaveFromGps by remember { mutableStateOf(false) }
    var placeDraftToSave by remember { mutableStateOf<PendingPlaceDraft?>(null) }
    var recenterCounter by remember { mutableIntStateOf(0) }

    val places = provider.places

    // Nodos propios con coordenadas y altitud exactas (nunca se sustituyen por San Jorge)
    val extraPlaces = remember(savedPlaces, locationReading) {
        buildList {
            locationReading?.let { gps ->
                add(
                    LaPazPlace(
                        id = LaPazStreetGraphProvider.GPS_NODE_ID,
                        name = "Mi ubicación actual",
                        zone = "GPS en vivo · ${gps.zoneLabel}",
                        latitude = gps.latitude,
                        longitude = gps.longitude,
                        elevationMeters = gps.altitudeMeters,
                    ),
                )
            }
            savedPlaces.forEach { saved ->
                add(
                    LaPazPlace(
                        id = saved.id,
                        name = saved.label,
                        zone = "Lugar guardado",
                        latitude = saved.latitude,
                        longitude = saved.longitude,
                        elevationMeters = saved.altitudeMeters,
                    ),
                )
            }
        }
    }
    val allSelectablePlaces = remember(places, extraPlaces) { extraPlaces + places }

    var originId by rememberSaveable { mutableStateOf("el_prado") }
    var destinationId by rememberSaveable { mutableStateOf("plaza_murillo") }
    var selectedCompareCard by rememberSaveable { mutableIntStateOf(0) }
    var pendingUseGpsOrigin by remember { mutableStateOf(false) }

    val weightKg = profile?.weightKg ?: 70.0

    LaunchedEffect(savedPlaces) {
        if (activeSavedPlaceId == null || savedPlaces.none { it.id == activeSavedPlaceId }) {
            activeSavedPlaceId = savedPlaces.firstOrNull()?.id
        }
    }

    LaunchedEffect(allSelectablePlaces) {
        if (allSelectablePlaces.none { it.id == originId }) {
            originId = "el_prado"
        }
        if (allSelectablePlaces.none { it.id == destinationId }) {
            destinationId = "plaza_murillo"
        }
    }

    LaunchedEffect(locationReading, pendingSaveFromGps) {
        if (pendingSaveFromGps && locationReading != null) {
            pendingSaveFromGps = false
            val suggested = if (savedPlaces.none { it.label.equals("Mi casa", ignoreCase = true) }) {
                "Mi casa"
            } else {
                ""
            }
            placeDraftToSave = PendingPlaceDraft(
                latitude = locationReading.latitude,
                longitude = locationReading.longitude,
                altitudeMeters = locationReading.altitudeMeters,
                sourceNote = "GPS actual · ${locationReading.zoneLabel}",
                suggestedName = suggested,
            )
        }
    }

    LaunchedEffect(locationReading, pendingUseGpsOrigin) {
        if (pendingUseGpsOrigin && locationReading != null) {
            originId = LaPazStreetGraphProvider.GPS_NODE_ID
            if (destinationId == LaPazStreetGraphProvider.GPS_NODE_ID) {
                destinationId = "parque_urbano"
            }
            recenterCounter++
            onNotify("Origen fijado en Mi ubicación actual (${locationReading.altitudeMeters} m)")
            pendingUseGpsOrigin = false
        }
    }

    fun openSaveFromMapPoint(lat: Double, lng: Double) {
        val estAlt = LaPazRecommendedRoutes.estimateAltitudeAt(lat, lng)
        placeDraftToSave = PendingPlaceDraft(
            latitude = lat,
            longitude = lng,
            altitudeMeters = estAlt,
            sourceNote = "Punto marcado en el mapa (${String.format(Locale.US, "%.4f, %.4f", lat, lng)})",
            suggestedName = "",
        )
    }

    val activeSavedPlace = savedPlaces.firstOrNull { it.id == activeSavedPlaceId } ?: savedPlaces.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = routesMode == 0,
                onClick = { routesMode = 0 },
                label = { Text("Cerca y recomendadas") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = routesMode == 1,
                onClick = { routesMode = 1 },
                label = { Text("Comparar A → B") },
                modifier = Modifier.weight(1f),
            )
        }

        if (routesMode == 0) {
            val anchorLat = activeSavedPlace?.latitude ?: locationReading?.latitude
            val anchorLng = activeSavedPlace?.longitude ?: locationReading?.longitude
            val anchorAlt = activeSavedPlace?.altitudeMeters ?: locationReading?.altitudeMeters
            val anchorLabel = activeSavedPlace?.label ?: locationReading?.let { "tu ubicación" }

            val localCircuits = remember(anchorLabel, anchorLat, anchorLng, anchorAlt, selectedGoal) {
                if (anchorLat != null && anchorLng != null && anchorAlt != null && anchorLabel != null) {
                    LaPazRecommendedRoutes.buildLocalCircuitsAround(
                        label = anchorLabel,
                        latitude = anchorLat,
                        longitude = anchorLng,
                        altitudeMeters = anchorAlt,
                    ).filter { selectedGoal == ActivityGoal.ALL || selectedGoal in it.goals }
                } else {
                    emptyList()
                }
            }

            val featuredFiltered = remember(selectedGoal) {
                LaPazRecommendedRoutes.featured.filter {
                    selectedGoal == ActivityGoal.ALL || selectedGoal in it.goals
                }
            }

            val allVisibleRoutes = localCircuits + featuredFiltered
            val activeRoute = allVisibleRoutes.firstOrNull { it.id == selectedMapRouteId }
                ?: featuredFiltered.firstOrNull()
                ?: LaPazRecommendedRoutes.featured.first()

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeRoute.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Mantén presionado el mapa para guardar un lugar (ej. Casa tía)",
                                color = MutedText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        TextButton(
                            onClick = { recenterCounter++ },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Centrar", color = LimeAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    val mapLines = remember(activeRoute) {
                        listOf(
                            MapRouteLine(
                                points = activeRoute.waypoints,
                                color = if (activeRoute.isFromHome) WaterBlue else LimeAccent,
                                widthDp = 5.5f,
                                title = activeRoute.name,
                            ),
                        )
                    }

                    val mapPins = remember(activeRoute, savedPlaces, activeSavedPlace, locationReading, featuredFiltered) {
                        buildList {
                            featuredFiltered.forEach { rec ->
                                add(
                                    MapPin(
                                        latitude = rec.latitude,
                                        longitude = rec.longitude,
                                        title = rec.name,
                                        subtitle = "${rec.zone} · ${rec.altitudeMeters} m",
                                        color = if (rec.id == activeRoute.id) LimeAccent else SoftEmerald,
                                        sizeDp = if (rec.id == activeRoute.id) 18 else 12,
                                    ),
                                )
                            }
                            savedPlaces.forEach { saved ->
                                add(
                                    MapPin(
                                        latitude = saved.latitude,
                                        longitude = saved.longitude,
                                        title = saved.label,
                                        subtitle = "Guardado · ${saved.altitudeMeters} m",
                                        color = UvPurple,
                                        sizeDp = if (saved.id == activeSavedPlace?.id) 19 else 14,
                                    ),
                                )
                            }
                            locationReading?.let { gps ->
                                add(
                                    MapPin(
                                        latitude = gps.latitude,
                                        longitude = gps.longitude,
                                        title = "Mi ubicación actual",
                                        subtitle = "${gps.altitudeMeters} msnm",
                                        color = WaterBlue,
                                        sizeDp = 16,
                                    ),
                                )
                            }
                        }
                    }

                    PaceFitRoutesMap(
                        lines = mapLines,
                        pins = mapPins,
                        focusPoints = activeRoute.waypoints,
                        cameraKey = "rec_${activeRoute.id}_${activeSavedPlace?.id}_$recenterCounter",
                        height = 240.dp,
                        onMapLongPress = ::openSaveFromMapPoint,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LegendDot(color = if (activeRoute.isFromHome) WaterBlue else LimeAccent, label = "Ruta")
                        if (savedPlaces.isNotEmpty()) LegendDot(color = UvPurple, label = "Guardados")
                        if (locationReading != null) LegendDot(color = WaterBlue, label = "Tu GPS")
                        LegendDot(color = SoftEmerald, label = "Parques")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mis lugares guardados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (savedPlaces.isEmpty()) {
                                    "Guarda Mi casa, Casa tía o Trabajo usando tu GPS o manteniendo presionado el mapa."
                                } else {
                                    "Elige un punto para ver circuitos cercanos o úsalo en Comparar A → B."
                                },
                                color = MutedText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        StatusPill(
                            text = "${savedPlaces.size}",
                            color = if (savedPlaces.isNotEmpty()) UvPurple else WaterBlue,
                        )
                    }

                    if (savedPlaces.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            savedPlaces.forEach { saved ->
                                FilterChip(
                                    selected = activeSavedPlace?.id == saved.id,
                                    onClick = {
                                        activeSavedPlaceId = saved.id
                                        selectedMapRouteId = "home_walk"
                                        recenterCounter++
                                    },
                                    label = { Text("${saved.label} · ${saved.altitudeMeters} m") },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled = !detectingLocation,
                            onClick = {
                                if (locationReading != null) {
                                    val suggested = if (savedPlaces.none { it.label.equals("Mi casa", ignoreCase = true) }) {
                                        "Mi casa"
                                    } else {
                                        ""
                                    }
                                    placeDraftToSave = PendingPlaceDraft(
                                        latitude = locationReading.latitude,
                                        longitude = locationReading.longitude,
                                        altitudeMeters = locationReading.altitudeMeters,
                                        sourceNote = "GPS actual · ${locationReading.zoneLabel}",
                                        suggestedName = suggested,
                                    )
                                } else {
                                    pendingSaveFromGps = true
                                    onDetectLocation()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (detectingLocation) "Ubicando..." else "+ Guardar ubicación actual")
                        }
                        if (activeSavedPlace != null) {
                            OutlinedButton(onClick = { onDeletePlace(activeSavedPlace) }) {
                                Text("Quitar")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = selectedGoal == goal,
                        onClick = { selectedGoal = goal },
                        label = { Text(goal.label) },
                    )
                }
            }

            if (localCircuits.isNotEmpty() && anchorLabel != null) {
                Text(
                    "Rutas desde $anchorLabel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                localCircuits.forEach { route ->
                    RecommendedRouteCard(
                        route = route,
                        weightKg = weightKg,
                        selectedGoal = selectedGoal,
                        selectedOnMap = route.id == activeRoute.id,
                        distanceFromAnchorText = "Sale desde $anchorLabel",
                        onSelectOnMap = {
                            selectedMapRouteId = route.id
                            recenterCounter++
                        },
                        onOpenAdvice = { selectedDetailRoute = route },
                    )
                }
            }

            Text(
                "Recomendadas en La Paz y El Alto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Incluye Parque Urbano Central y circuitos conocidos para caminar, trotar o correr.",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )

            featuredFiltered.forEach { route ->
                val distLabel = if (anchorLat != null && anchorLng != null) {
                    val meters = LaPazRecommendedRoutes.haversineMeters(
                        anchorLat,
                        anchorLng,
                        route.latitude,
                        route.longitude,
                    )
                    val suffix = activeSavedPlace?.let { "de ${it.label}" } ?: "de ti"
                    "${LaPazRecommendedRoutes.formatDistanceFromUser(meters)} $suffix"
                } else {
                    route.zone
                }

                RecommendedRouteCard(
                    route = route,
                    weightKg = weightKg,
                    selectedGoal = selectedGoal,
                    selectedOnMap = route.id == activeRoute.id,
                    distanceFromAnchorText = distLabel,
                    onSelectOnMap = {
                        selectedMapRouteId = route.id
                        recenterCounter++
                    },
                    onOpenAdvice = { selectedDetailRoute = route },
                )
            }
        } else {
            val originPlace = allSelectablePlaces.firstOrNull { it.id == originId }
            val destinationPlace = allSelectablePlaces.firstOrNull { it.id == destinationId }
            val pointsTooClose = originPlace != null && destinationPlace != null &&
                    originPlace.id != destinationPlace.id &&
                    LaPazRecommendedRoutes.haversineMeters(
                        originPlace.latitude,
                        originPlace.longitude,
                        destinationPlace.latitude,
                        destinationPlace.longitude,
                    ) < 40

            val comparison = remember(originId, destinationId, weightKg, extraPlaces) {
                provider.comparePlaces(originId, destinationId, weightKg, extraPlaces)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Comparar recorrido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                val tmp = originId
                                originId = destinationId
                                destinationId = tmp
                                recenterCounter++
                            },
                        ) {
                            Text("⇅ Invertir", color = LimeAccent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Accesos rápidos para \"Desde\":", color = MutedText, style = MaterialTheme.typography.labelSmall)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            enabled = !detectingLocation,
                            onClick = {
                                if (locationReading != null) {
                                    originId = LaPazStreetGraphProvider.GPS_NODE_ID
                                    if (destinationId == LaPazStreetGraphProvider.GPS_NODE_ID) {
                                        destinationId = "parque_urbano"
                                    }
                                    recenterCounter++
                                    onNotify("Origen: Mi ubicación actual (${locationReading.altitudeMeters} m)")
                                } else {
                                    pendingUseGpsOrigin = true
                                    onDetectLocation()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(if (detectingLocation) "Buscando..." else "Mi ubicación")
                        }
                        savedPlaces.forEach { saved ->
                            OutlinedButton(
                                onClick = {
                                    originId = saved.id
                                    if (destinationId == saved.id) {
                                        destinationId = "parque_urbano"
                                    }
                                    recenterCounter++
                                    onNotify("Origen: ${saved.label} (${saved.altitudeMeters} m)")
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(saved.label)
                            }
                        }
                    }

                    PlaceDropdownSelector(
                        label = "Desde",
                        selectedId = originId,
                        places = allSelectablePlaces,
                        dotColor = WaterBlue,
                        onSelect = {
                            originId = it
                            recenterCounter++
                        },
                    )

                    PlaceDropdownSelector(
                        label = "Hasta",
                        selectedId = destinationId,
                        places = allSelectablePlaces,
                        dotColor = LimeAccent,
                        onSelect = {
                            destinationId = it
                            recenterCounter++
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Triple("Prado → Murillo", "el_prado", "plaza_murillo"),
                            Triple("Prado → Parque Urbano", "el_prado", "parque_urbano"),
                            Triple("Ceja → Murillo", "ceja_el_alto", "plaza_murillo"),
                            Triple("Satélite → Abaroa", "ciudad_satelite", "plaza_abaroa"),
                        ).forEach { (label, from, to) ->
                            FilterChip(
                                selected = originId == from && destinationId == to,
                                onClick = {
                                    originId = from
                                    destinationId = to
                                    recenterCounter++
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            if (comparison == null) {
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                    Text(
                        if (pointsTooClose) {
                            "El punto de inicio y el destino están prácticamente en el mismo lugar (menos de 40 m). Cambia \"Hasta\" para comparar rutas."
                        } else {
                            "Selecciona un origen y un destino distintos para comparar la ruta más corta frente a la más suave."
                        },
                        color = MutedText,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${comparison.origin.name} → ${comparison.destination.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Mantén presionado el mapa si quieres guardar otro punto",
                                    color = MutedText,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(
                                onClick = { recenterCounter++ },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("Centrar", color = LimeAccent, fontWeight = FontWeight.Bold)
                            }
                        }

                        val compareLines = remember(comparison, selectedCompareCard) {
                            val shortLine = MapRouteLine(
                                points = comparison.shortest.coordinates,
                                color = if (selectedCompareCard == 1) ShortAmber else ShortAmber.copy(alpha = 0.72f),
                                widthDp = if (selectedCompareCard == 1) 6.5f else 4.5f,
                                title = "Ruta más corta",
                            )
                            val gentleLine = MapRouteLine(
                                points = comparison.gentlest.coordinates,
                                color = if (selectedCompareCard == 0) SoftEmerald else SoftEmerald.copy(alpha = 0.72f),
                                widthDp = if (selectedCompareCard == 0) 6.5f else 4.5f,
                                title = "Ruta más suave",
                            )
                            if (selectedCompareCard == 0) listOf(shortLine, gentleLine) else listOf(gentleLine, shortLine)
                        }

                        val comparePins = remember(comparison) {
                            listOf(
                                MapPin(
                                    latitude = comparison.origin.latitude,
                                    longitude = comparison.origin.longitude,
                                    title = "Inicio: ${comparison.origin.name}",
                                    subtitle = "${comparison.origin.elevationMeters} msnm",
                                    color = WaterBlue,
                                    sizeDp = 18,
                                ),
                                MapPin(
                                    latitude = comparison.destination.latitude,
                                    longitude = comparison.destination.longitude,
                                    title = "Destino: ${comparison.destination.name}",
                                    subtitle = "${comparison.destination.elevationMeters} msnm",
                                    color = LimeAccent,
                                    sizeDp = 18,
                                ),
                            )
                        }

                        val allComparePoints = remember(comparison) {
                            comparison.shortest.coordinates + comparison.gentlest.coordinates
                        }

                        PaceFitRoutesMap(
                            lines = compareLines,
                            pins = comparePins,
                            focusPoints = allComparePoints,
                            cameraKey = "cmp_${comparison.origin.id}_${comparison.destination.id}_${comparison.origin.latitude}_${comparison.destination.latitude}_$recenterCounter",
                            height = 250.dp,
                            onMapLongPress = ::openSaveFromMapPoint,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            LegendDot(color = SoftEmerald, label = "Más suave")
                            LegendDot(color = ShortAmber, label = "Más corta")
                            LegendDot(color = WaterBlue, label = "Inicio")
                            LegendDot(color = LimeAccent, label = "Destino")
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SoftEmerald.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoftEmerald.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Recomendación en altura",
                            color = SoftEmerald,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(comparison.savingsSummary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                RouteComparisonCard(
                    option = comparison.gentlest,
                    accentColor = SoftEmerald,
                    badge = "Recomendada",
                    highlighted = selectedCompareCard == 0,
                    onClick = { selectedCompareCard = 0 },
                )

                RouteComparisonCard(
                    option = comparison.shortest,
                    accentColor = ShortAmber,
                    badge = "Más directa",
                    highlighted = selectedCompareCard == 1,
                    onClick = { selectedCompareCard = 1 },
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Perfil de elevación comparado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        ElevationComparisonChart(
                            gentleProfile = comparison.gentlest.elevationProfile,
                            shortProfile = comparison.shortest.elevationProfile,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            LegendDot(color = SoftEmerald, label = "Ruta más suave")
                            LegendDot(color = ShortAmber, label = "Ruta más corta")
                        }
                        Text(
                            "Zona de prueba en La Paz y El Alto (estimación con Tobler y Minetti usando ${weightKg.roundToInt()} kg). Mapa base: OpenStreetMap. No refleja escalinatas.",
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    placeDraftToSave?.let { draft ->
        var placeName by remember(draft) { mutableStateOf(draft.suggestedName) }
        var altText by remember(draft) { mutableStateOf(draft.altitudeMeters.toString()) }

        AlertDialog(
            onDismissRequest = { placeDraftToSave = null },
            title = { Text("Guardar punto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(draft.sourceNote, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it.take(30) },
                        label = { Text("Nombre del lugar") },
                        placeholder = { Text("Ej. Mi casa, Casa tía, Trabajo") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("Mi casa", "Casa tía", "Trabajo", "Universidad").forEach { suggestion ->
                            FilterChip(
                                selected = placeName.equals(suggestion, ignoreCase = true),
                                onClick = { placeName = suggestion },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = altText,
                        onValueChange = { altText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Altitud aproximada (msnm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanName = placeName.trim()
                        if (cleanName.isBlank()) {
                            onNotify("Escribe un nombre para el lugar")
                        } else {
                            val alt = altText.toIntOrNull()?.coerceIn(500, 5500) ?: draft.altitudeMeters
                            val persisted = onSavePlace(
                                SavedPlace(
                                    id = "saved_${System.currentTimeMillis()}",
                                    label = cleanName,
                                    latitude = draft.latitude,
                                    longitude = draft.longitude,
                                    altitudeMeters = alt,
                                ),
                            )
                            activeSavedPlaceId = persisted.id
                            if (routesMode == 0) {
                                selectedMapRouteId = "home_walk"
                            }
                            recenterCounter++
                            placeDraftToSave = null
                        }
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { placeDraftToSave = null }) { Text("Cancelar") }
            },
        )
    }

    selectedDetailRoute?.let { route ->
        val stats = remember(route, weightKg, selectedGoal) {
            LaPazRecommendedRoutes.estimateStats(route, weightKg, selectedGoal)
        }
        AlertDialog(
            onDismissRequest = { selectedDetailRoute = null },
            title = { Text(route.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(route.description, style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(12.dp), color = CardBgElevated) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Estimación para ${weightKg.roundToInt()} kg:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("• Distancia: ${"%.1f".format(route.distanceMeters / 1000.0)} km · Subida ↑${route.elevationGainMeters} m", style = MaterialTheme.typography.bodySmall)
                            Text("• Tiempo aprox.: ${stats.minutes} min · Esfuerzo: ~${stats.kcal} kcal", style = MaterialTheme.typography.bodySmall)
                            Text("• Agua sugerida para la salida: +${stats.waterExtraMl} ml", color = WaterBlue, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Consejo en altura: ${route.tip}", color = LimeAccent, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetailRoute = null }) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun RecommendedRouteCard(
    route: RecommendedRoute,
    weightKg: Double,
    selectedGoal: ActivityGoal,
    selectedOnMap: Boolean,
    distanceFromAnchorText: String,
    onSelectOnMap: () -> Unit,
    onOpenAdvice: () -> Unit,
) {
    val stats = remember(route, weightKg, selectedGoal) {
        LaPazRecommendedRoutes.estimateStats(route, weightKg, selectedGoal)
    }
    val accent = when {
        route.isFromHome -> WaterBlue
        route.id == "rec_parque_urbano" -> LimeAccent
        else -> SoftEmerald
    }
    val terrainColor = when {
        route.terrainLabel.contains("exigente", ignoreCase = true) -> AlertRed
        route.terrainLabel.contains("Subida", ignoreCase = true) || route.terrainLabel.contains("moderado", ignoreCase = true) -> ShortAmber
        else -> SoftEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelectOnMap)
            .border(
                width = if (selectedOnMap) 1.5.dp else 0.dp,
                color = if (selectedOnMap) accent else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            ),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(route.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$distanceFromAnchorText · ${route.altitudeMeters} m",
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                StatusPill(text = route.terrainLabel, color = terrainColor)
            }

            Text(route.description, color = MutedText, style = MaterialTheme.typography.bodySmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile("Distancia", "${"%.1f".format(route.distanceMeters / 1000.0)} km", "↑${route.elevationGainMeters} m", Modifier.weight(1f))
                MetricTile("Tiempo", "${stats.minutes} min", "Est.", Modifier.weight(1f))
                MetricTile("Esfuerzo", "${stats.kcal}", "kcal", Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedOnMap) "Mostrando en mapa" else "Ideal: ${route.goals.joinToString(" · ") { it.label }}",
                    color = if (selectedOnMap) accent else MutedText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedOnMap) FontWeight.Bold else FontWeight.Normal,
                )
                TextButton(
                    onClick = onOpenAdvice,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Ver consejo ›", color = LimeAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlaceDropdownSelector(
    label: String,
    selectedId: String,
    places: List<LaPazPlace>,
    dotColor: Color,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = places.firstOrNull { it.id == selectedId } ?: places.first()

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true },
            color = CardBgElevated,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MutedText)
                    Text(selected.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                }
                Text("${selected.elevationMeters} m ▾", color = MutedText, style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            places.forEach { place ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(place.name, fontWeight = FontWeight.Medium)
                            Text("${place.zone} · ${place.elevationMeters} msnm", style = MaterialTheme.typography.labelSmall, color = MutedText)
                        }
                    },
                    onClick = {
                        onSelect(place.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RouteComparisonCard(
    option: RouteOption,
    accentColor: Color,
    badge: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val terrainColor = when {
        option.terrainLabel.contains("exigente", ignoreCase = true) -> AlertRed
        option.terrainLabel.contains("Subida", ignoreCase = true) -> ShortAmber
        option.terrainLabel.contains("Bajada", ignoreCase = true) || option.terrainLabel.contains("bajada", ignoreCase = true) -> WaterBlue
        else -> SoftEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (highlighted) 1.5.dp else 0.dp,
                color = if (highlighted) accentColor else Color.Transparent,
                shape = RoundedCornerShape(22.dp),
            ),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(option.subtitle, color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
                StatusPill(text = badge, color = accentColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(text = option.terrainLabel, color = terrainColor)
                Text(
                    "↑${option.elevationGainMeters} m  ·  ↓${option.elevationLossMeters} m  ·  máx ${option.maxSlopePercent}%",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricTile("Distancia", "${option.distanceMeters} m", "Aprox.", Modifier.weight(1f))
                MetricTile("Tiempo", "${option.estimatedMinutes} min", "Tobler", Modifier.weight(1f))
                MetricTile("Esfuerzo", "${option.estimatedKcal}", "kcal", Modifier.weight(1f))
            }

            Text(
                text = "Por: ${option.viaStreets.joinToString(" → ")}",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ============================================================================
// PESTAÑA 3: SOCIAL (PF-013 · Corredores, solicitudes, chat y bloqueo)
// ============================================================================

@Composable
private fun SocialCommunitySection(
    repository: LocalPreviewSocialRepository,
    parameters: List<PaceParameterEntity>,
    onRequestLocation: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var activeChatId by rememberSaveable { mutableStateOf<String?>(null) }

    val visibilityDuration = parameters.firstOrNull { it.key == "social_visible_sec" }?.value?.roundToInt() ?: 60
    val searchRadius = parameters.firstOrNull { it.key == "social_radius_m" }?.value?.roundToInt() ?: 1500

    LaunchedEffect(state.visibleWhileRunning) {
        while (state.visibleWhileRunning) {
            delay(1000)
            repository.tickVisibility()
        }
    }

    val openConversation = activeChatId?.let { id ->
        state.conversations.firstOrNull { it.conversationId == id }
    }

    if (openConversation != null) {
        SocialChatView(
            conversation = openConversation,
            repository = repository,
            onBack = { activeChatId = null },
            onNotify = onNotify,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Visible mientras corro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.visibleWhileRunning) {
                                "Activo · expira en ${state.visibilitySecondsRemaining} s"
                            } else {
                                "Desactivado · nadie ve tu ubicación"
                            },
                            color = if (state.visibleWhileRunning) SoftEmerald else MutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.visibleWhileRunning,
                        onCheckedChange = { checked ->
                            repository.setVisible(checked, visibilityDuration)
                            if (checked) onRequestLocation()
                            onNotify(if (checked) "Visible con ubicación aproximada (${visibilityDuration}s)" else "Visibilidad desactivada")
                        },
                    )
                }
                Text(
                    "Privacidad: celda redondeada ${state.approximateCellLabel}, radio $searchRadius m. Modo vista previa local (listo para conectar Supabase + RLS).",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Cerca (${state.nearbyRunners.size})",
                "Solicitudes (${state.incomingRequests.size})",
                "Chats (${state.conversations.size})",
            ).forEachIndexed { idx, label ->
                FilterChip(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (subTab) {
            0 -> {
                if (!state.visibleWhileRunning) {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Activa tu visibilidad para descubrir corredores", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Por privacidad (opt-in), solo ves corredores cercanos cuando activas \"Visible mientras corro\".",
                                color = MutedText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = {
                                    repository.setVisible(true, visibilityDuration)
                                    onRequestLocation()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Activar visibilidad (${visibilityDuration}s)")
                            }
                        }
                    }
                } else if (state.nearbyRunners.isEmpty()) {
                    EmptySocialCard("No hay corredores visibles en este momento en tu radio de $searchRadius m.")
                } else {
                    state.nearbyRunners.forEach { runner ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AvatarCircle(runner.initials, LimeAccent)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(runner.summary.displayName, fontWeight = FontWeight.Bold)
                                    Text(
                                        runner.summary.activityLabel ?: "Corriendo cerca",
                                        color = MutedText,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "Aprox. ${runner.approxDistanceMeters} m",
                                        color = WaterBlue,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                when {
                                    runner.isMatched -> {
                                        FilledTonalButton(
                                            onClick = {
                                                val match = state.conversations.firstOrNull { it.peerUserId == runner.summary.userId }
                                                if (match != null) activeChatId = match.conversationId
                                                else subTab = 2
                                            },
                                        ) { Text("Chat") }
                                    }
                                    runner.requestSent -> {
                                        StatusPill(text = "Enviada", color = MutedText)
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    repository.sendConnectionRequest(runner.summary.userId)
                                                    onNotify("Solicitud enviada a ${runner.summary.displayName}")
                                                }
                                            },
                                        ) { Text("Seguir") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (state.incomingRequests.isEmpty()) {
                    EmptySocialCard("No tienes solicitudes pendientes.")
                } else {
                    state.incomingRequests.forEach { req ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AvatarCircle(req.fromInitials, WaterBlue)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(req.fromName, fontWeight = FontWeight.Bold)
                                        Text(req.activityLabel, color = MutedText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                repository.respondToRequest(req.request.id, accept = false)
                                                onNotify("Solicitud ignorada en silencio")
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Ignorar") }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                repository.respondToRequest(req.request.id, accept = true)
                                                onNotify("¡Match con ${req.fromName}! Chat habilitado")
                                                subTab = 2
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Aceptar") }
                                }
                                Text(
                                    "Si ignoras, el remitente no recibe aviso y deja de aparecerte.",
                                    color = MutedText,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                if (state.conversations.isEmpty()) {
                    EmptySocialCard("Aún no tienes chats activos. Acepta una solicitud o haz match con alguien cercano.")
                } else {
                    state.conversations.forEach { conv ->
                        val lastMsg = conv.messages.lastOrNull()?.body ?: "Match creado · saluda para empezar"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { activeChatId = conv.conversationId },
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AvatarCircle(conv.peerInitials, SoftEmerald)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.peerName, fontWeight = FontWeight.Bold)
                                    Text(
                                        lastMsg,
                                        color = MutedText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text("Abrir ›", color = LimeAccent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    repository.resetPreview()
                    onNotify("Vista previa social reiniciada")
                },
            ) {
                Text("Reiniciar vista previa", color = MutedText, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SocialChatView(
    conversation: MatchConversation,
    repository: LocalPreviewSocialRepository,
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(conversation.conversationId) { mutableStateOf("") }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (repository.isMessageBlockedByModeration(clean)) {
            onNotify("Mensaje bloqueado por moderación (lenguaje ofensivo)")
            return
        }
        scope.launch {
            repository.sendMessage(conversation.conversationId, clean)
            draft = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("‹ Volver", color = LimeAccent, fontWeight = FontWeight.Bold)
                }
                AvatarCircle(conversation.peerInitials, SoftEmerald, sizeDp = 36)
                Column(modifier = Modifier.weight(1f)) {
                    Text(conversation.peerName, fontWeight = FontWeight.Bold)
                    Text("Solo entre los dos · moderado", color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { showReportDialog = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Reportar", color = ShortAmber, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { showBlockDialog = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Bloquear", color = AlertRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (conversation.messages.isEmpty()) {
                Text(
                    "¡Hicieron match! Elige un saludo rápido o escribe un mensaje.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            conversation.messages.forEach { msg ->
                val isMine = msg.senderId == "me_local"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isMine) LimeAccent else CardBgElevated,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    ) {
                        Text(
                            text = msg.body,
                            color = if (isMine) Color(0xFF101820) else Color(0xFFF1F6F7),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "¿Trotamos en el Parque Urbano?",
                "¿Qué ritmo manejas en altura?",
                "¡Buena ruta hoy!",
            ).forEach { chip ->
                FilterChip(
                    selected = false,
                    onClick = { send(chip) },
                    label = { Text(chip, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(280) },
                placeholder = { Text("Escribe un mensaje...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { send(draft) },
                enabled = draft.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Enviar")
            }
        }
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("¿Bloquear a ${conversation.peerName}?") },
            text = { Text("Se eliminará el match, el historial del chat y dejarán de verse mutuamente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.blockUser(conversation.peerUserId)
                            showBlockDialog = false
                            onBack()
                            onNotify("${conversation.peerName} fue bloqueado")
                        }
                    },
                ) { Text("Bloquear", color = AlertRed) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Reportar usuario") },
            text = { Text("El reporte se enviará a moderación junto con la regla de seguridad de PaceFit AI.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.reportUser(conversation.peerUserId, "Comportamiento inapropiado")
                            showReportDialog = false
                            onNotify("Reporte registrado")
                        }
                    },
                ) { Text("Enviar reporte", color = ShortAmber) }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

// ============================================================================
// PESTAÑA 4: AJUSTES / PARÁMETROS Y FUENTES (PF-002)
// ============================================================================

@Composable
private fun ParametersSettingsSection(
    items: List<PaceParameterEntity>,
    dao: PaceFitModulesDao,
    onNotify: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by rememberSaveable { mutableStateOf(ParameterCategory.ALL) }
    var expandedSourceKey by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PaceParameterEntity?>(null) }

    val filtered = remember(items, selectedCategory) {
        if (selectedCategory == ParameterCategory.ALL) items
        else items.filter { ParameterCatalogDefaults.categoryFor(it.key) == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Los cambios se guardan en Room y actualizan tus cálculos de Hoy al instante sin tocar código.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ParameterCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.label) },
                )
            }
        }

        filtered.forEach { item ->
            val badgeColor = when (item.status.uppercase()) {
                "RESPALDADO", "VALIDADO" -> SoftEmerald
                "DISENO", "DISEÑO", "REFERENCIA", "REFERENCIAL" -> WaterBlue
                else -> ShortAmber
            }
            val expanded = expandedSourceKey == item.key
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.label,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(text = item.status, color = badgeColor)
                    }
                    Text(
                        "${formatParamValue(item.value)} ${item.unit}",
                        style = MaterialTheme.typography.titleLarge,
                        color = LimeAccent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        ParameterCatalogDefaults.helperFor(item.key),
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (expanded) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CardBgElevated,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Fuente: ${item.source}",
                                color = Color(0xFFE5EEF0),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = { expandedSourceKey = if (expanded) null else item.key },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(if (expanded) "Ocultar fuente" else "Ver fuente", color = MutedText)
                        }
                        Row {
                            TextButton(onClick = { editing = item }) {
                                Text("Editar", color = LimeAccent)
                            }
                            if (item.key.startsWith("custom_")) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            dao.deleteParameter(item.key)
                                            onNotify("Parámetro eliminado")
                                        }
                                    },
                                ) {
                                    Text("Eliminar", color = AlertRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    editing = PaceParameterEntity(
                        key = "custom_${System.currentTimeMillis()}",
                        label = "",
                        value = 0.0,
                        unit = "",
                        source = "",
                        status = "PROVISIONAL",
                        updatedAt = System.currentTimeMillis(),
                    )
                },
                modifier = Modifier.weight(1f),
            ) { Text("Añadir nuevo") }

            FilledTonalButton(
                onClick = {
                    scope.launch {
                        ParameterCatalogDefaults.buildDefaults().forEach { dao.saveParameter(it) }
                        onNotify("Valores por defecto restaurados")
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Restaurar base") }
        }
        Spacer(Modifier.height(12.dp))
    }

    editing?.let { target ->
        ParameterEditDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    dao.saveParameter(updated)
                    editing = null
                    onNotify("Parámetro actualizado")
                }
            },
            onError = onNotify,
        )
    }
}

@Composable
private fun ParameterEditDialog(
    target: PaceParameterEntity,
    onDismiss: () -> Unit,
    onSave: (PaceParameterEntity) -> Unit,
    onError: (String) -> Unit,
) {
    var label by remember(target.key) { mutableStateOf(target.label) }
    var value by remember(target.key) { mutableStateOf(formatParamValue(target.value)) }
    var unit by remember(target.key) { mutableStateOf(target.unit) }
    var source by remember(target.key) { mutableStateOf(target.source) }
    var status by remember(target.key) { mutableStateOf(target.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.label.isBlank()) "Nuevo parámetro" else "Editar parámetro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Valor numérico") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unidad") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Fuente bibliográfica / nota") },
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("RESPALDADO", "PROVISIONAL", "POR_VERIFICAR", "DISENO").forEach { option ->
                        FilterChip(
                            selected = status.equals(option, ignoreCase = true),
                            onClick = { status = option },
                            label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val number = value.replace(',', '.').toDoubleOrNull()
                    if (label.isBlank() || number == null || unit.isBlank() || source.isBlank()) {
                        onError("Completa nombre, valor numérico, unidad y fuente")
                    } else {
                        onSave(
                            target.copy(
                                label = label.trim(),
                                value = number,
                                unit = unit.trim(),
                                source = source.trim(),
                                status = status.trim().ifBlank { "PROVISIONAL" },
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ============================================================================
// Componentes visuales ligeros (Canvas + Material3)
// ============================================================================

@Composable
private fun HydrationRing(
    progress: Float,
    consumedMl: Int,
    targetMl: Int,
) {
    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = CardBgElevated,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = if (progress >= 1f) SoftEmerald else WaterBlue,
                startAngle = -90f,
                sweepAngle = (360f * progress).coerceIn(0f, 360f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$consumedMl",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF1F6F7),
            )
            Text(
                text = "/ $targetMl ml",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun ElevationComparisonChart(
    gentleProfile: List<Double>,
    shortProfile: List<Double>,
) {
    val allPoints = gentleProfile + shortProfile
    val minElev = (allPoints.minOrNull() ?: 3500.0) - 10.0
    val maxElev = (allPoints.maxOrNull() ?: 3700.0) + 10.0
    val span = max(1.0, maxElev - minElev)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBgElevated)
            .padding(12.dp),
    ) {
        fun drawProfile(points: List<Double>, color: Color) {
            if (points.size < 2) return
            val path = Path()
            points.forEachIndexed { idx, elev ->
                val x = size.width * (idx.toFloat() / (points.size - 1).coerceAtLeast(1))
                val y = size.height * (1f - ((elev - minElev) / span).toFloat())
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        drawProfile(shortProfile, ShortAmber)
        drawProfile(gentleProfile, SoftEmerald)
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MutedText, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CardBgElevated,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(subtitle, color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AvatarCircle(initials: String, color: Color, sizeDp: Int = 44) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, color = MutedText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptySocialCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(text, color = MutedText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(18.dp))
    }
}

private fun uvColorFor(level: UvRiskLevel): Color = when (level) {
    UvRiskLevel.NIGHT -> MutedText
    UvRiskLevel.LOW -> SoftEmerald
    UvRiskLevel.MODERATE -> ShortAmber
    UvRiskLevel.HIGH -> UvOrange
    UvRiskLevel.VERY_HIGH -> AlertRed
    UvRiskLevel.EXTREME -> UvPurple
}

private fun formatParamValue(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else value.toString()