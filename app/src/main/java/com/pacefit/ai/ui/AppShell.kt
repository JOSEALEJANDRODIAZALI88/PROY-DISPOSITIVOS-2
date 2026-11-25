package com.pacefit.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.pacefit.ai.data.ActivitySessionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Forest = Color(0xFF175E49)
private val AppColors = lightColorScheme(primary = Forest, onPrimary = Color.White,
    secondary = Color(0xFF556A31), primaryContainer = Color(0xFFD9EDE2), onPrimaryContainer = Color(0xFF123B2F),
    secondaryContainer = Color(0xFFE2EBCF), onSecondaryContainer = Color(0xFF123B2F),
    tertiary = Color(0xFF406A77), tertiaryContainer = Color(0xFFD9EEF4), onTertiaryContainer = Color(0xFF123A44),
    background = Color(0xFFF6F8F5), surface = Color.White, surfaceVariant = Color(0xFFEAF0E9), surfaceTint = Forest)
private data class MainTab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(MainTab("home", "Inicio", Icons.Default.Home),
    MainTab("activity", "Actividad", Icons.AutoMirrored.Filled.DirectionsRun), MainTab("city", "Ciudad", Icons.Default.LocationCity),
    MainTab("profile", "Perfil", Icons.Default.Person))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceFitRoot(profile: ProfileViewModel, local: LocalViewModel, activity: ActivityViewModel, cloud: CloudViewModel) {
    val health by profile.state.collectAsStateWithLifecycle()
    val storage by local.state.collectAsStateWithLifecycle()
    val remote by cloud.state.collectAsStateWithLifecycle()
    val account by cloud.session.collectAsStateWithLifecycle()
    val sessions by activity.sessions.collectAsStateWithLifecycle()
    val recording by activity.recording.collectAsStateWithLifecycle()
    val ready = remote.loaded && (storage.loaded || storage.storageError)
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val main = tabs.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingAction by rememberSaveable { mutableStateOf("START") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (remote.busy || storage.busy || health.saving) {
            local.showMessage("Espera a que termine la operación actual antes de iniciar el GPS.")
        } else if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (pendingAction == "CITY") local.captureLocation() else activity.command(pendingAction)
        } else local.showMessage("El GPS requiere ubicación precisa. Puedes seguir usando el perfil y las coordenadas manuales.")
    }
    val requestGps: (String) -> Unit = { action ->
        if (remote.busy || storage.busy || health.saving) {
            local.showMessage("Espera a que termine la operación actual antes de iniciar el GPS.")
        } else {
            pendingAction = action
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (action != "CITY" && Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (action != "CITY" && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                if (action == "CITY") local.captureLocation() else activity.command(action)
            } else permission.launch(permissions.toTypedArray())
        }
    }
    LaunchedEffect(health.message) { health.message?.let { snackbar.showSnackbar(it, actionLabel = "Cerrar"); profile.dismissMessage() } }
    LaunchedEffect(storage.message) { storage.message?.let { snackbar.showSnackbar(it, actionLabel = "Cerrar"); local.dismissMessage() } }
    LaunchedEffect(remote.message) { remote.message?.let { snackbar.showSnackbar(it, actionLabel = "Cerrar"); cloud.dismiss() } }
    LaunchedEffect(recording.error) { recording.error?.let { snackbar.showSnackbar(it) } }
    fun newCity(plogging: Boolean) {
        local.openDraft(plogging); cloud.resetSuggestion()
        nav.navigate(if (plogging) "city/new/plogging" else "city/new/report")
    }
    MaterialTheme(colorScheme = AppColors, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        // In particular, an account deletion must not overlap new activity/profile/city actions.
        if (remote.busy) Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Procesando tu solicitud…", style = MaterialTheme.typography.titleMedium)
                    Text("Espera antes de realizar otra operación.")
                }
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(if (main) "pacefit" else when {
                    route == "health" -> "Perfil de salud"
                    route == "account" -> "Tu cuenta"
                    route == "data" -> "Datos SQLite"
                    route == "settings" -> "Ajustes y privacidad"
                    route == "city/local" -> "Mis registros"
                    route.startsWith("city/new") -> "Nuevo registro"
                    route.startsWith("city/shared") -> "Publicación"
                    else -> "Detalle de actividad"
                }, fontWeight = FontWeight.Bold) },
                    navigationIcon = { if (!main) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                    actions = { if (main) IconButton({ nav.navigate("settings") }, enabled = ready) { Icon(Icons.Default.Settings, "Ajustes") } })
            },
            bottomBar = {
                if (main) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(route == tab.route, {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) }, enabled = ready)
                    }
                }
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (!ready) {
                Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Preparando tu espacio privado…")
                }
                return@Scaffold
            }
            NavHost(nav, "home", Modifier.padding(padding)) {
                composable("home") {
                    Dashboard(health, storage, sessions, { nav.navigate("health") }, { nav.navigate("activity") },
                        { newCity(true) }, { id -> nav.navigate("activity/$id") })
                }
                composable("activity") { ActivityScreen(activity, local, requestGps, { nav.navigate("activity/$it") }) }
                composable("activity/{id}") { entry -> ActivityDetail(entry.arguments?.getString("id").orEmpty(), activity) }
                composable("city") { CityHub(local, cloud, { newCity(it) }, { nav.navigate("city/local") },
                    { nav.navigate("account") }, { nav.navigate("city/shared/$it") }) }
                composable("city/new/plogging") { CityWizard(true, local, cloud, { requestGps("CITY") }) { nav.popBackStack() } }
                composable("city/new/report") { CityWizard(false, local, cloud, { requestGps("CITY") }) { nav.popBackStack() } }
                composable("city/local") { CityHistory(local, cloud) }
                composable("city/shared/{id}") { entry -> SharedEntryDetail(entry.arguments?.getString("id").orEmpty(), cloud) { nav.popBackStack() } }
                composable("profile") {
                    Page("Tu espacio", "Salud privada y cuenta pública, siempre separadas.") {
                        SurfaceCard {
                            Icon(Icons.Default.FavoriteBorder, null, tint = Forest)
                            Text("Perfil de salud", style = MaterialTheme.typography.titleLarge)
                            Text(if (health.profile == null) "Aún no has creado tu perfil." else "Guardado solo en este teléfono.")
                            Button({ nav.navigate("health") }) { Text(if (health.profile == null) "Crear mi perfil" else "Editar mi perfil") }
                        }
                        SurfaceCard {
                            Icon(Icons.Default.AccountCircle, null, tint = Forest)
                            Text("Cuenta de la comunidad", style = MaterialTheme.typography.titleLarge)
                            Text(account?.email ?: "Inicia sesión para publicar y consultar el mapa compartido.")
                            OutlinedButton({ nav.navigate("account") }) { Text(if (account == null) "Acceder a mi cuenta" else "Gestionar cuenta") }
                        }
                        MenuCard(Icons.Default.Storage, "Datos SQLite", "Tablas y directorios privados") { nav.navigate("data") }
                        MenuCard(Icons.Default.Shield, "Privacidad y ajustes", "Permisos, detección y eliminación") { nav.navigate("settings") }
                    }
                }
                composable("health") { ProfileEditor(health, profile, PaddingValues(), storage.busy || remote.busy || storage.storageError) }
                composable("account") { AccountScreen(cloud, sessions.any { it.endedAt == null }) {
                    profile.resetAfterLocalErase(); local.clearDrafts()
                } }
                composable("settings") {
                    SettingsScreen(local, { nav.navigate("data") }, { nav.navigate("account") }, sessions.any { it.endedAt == null })
                }
                composable("data") {
                    DataScreen(storage, local, PaddingValues(), health.saving || remote.busy || sessions.any { it.endedAt == null },
                        profile::resetAfterLocalErase)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(health: ProfileUiState, local: LocalUiState, sessions: List<ActivitySessionEntity>,
    profile: () -> Unit, activity: () -> Unit, plogging: () -> Unit, detail: (String) -> Unit) {
    Page("Un paso por ti.\nOtro por tu ciudad.", "Tu actividad, tu bienestar y una ciudad más limpia.") {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = Forest, modifier = Modifier.size(36.dp))
                Text(if (sessions.any { it.endedAt == null }) "Tienes una actividad en curso" else "¿Salimos a movernos?",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Tu recorrido se guarda en privado, incluso con la pantalla apagada.")
                Button(activity) { Text(if (sessions.any { it.endedAt == null }) "Continuar actividad" else "Ir a mi actividad") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("${sessions.count { it.endedAt != null }}", "actividades", Modifier.weight(1f))
            Metric("${local.plogging.size}", "recogidas locales", Modifier.weight(1f))
            Metric("${local.reports.count { !it.resolved }}", "avisos locales", Modifier.weight(1f))
        }
        if (health.profile == null) MenuCard(Icons.Default.FavoriteBorder, "Completa tu perfil", "Tus datos se quedan en SQLite", profile)
        MenuCard(Icons.Default.Eco, "Deja una huella positiva", "Registra una recogida de residuos", plogging)
        Text("Últimas actividades", style = MaterialTheme.typography.titleLarge)
        if (sessions.isEmpty()) Notice("Aquí verás tus recorridos reales. Empieza tu primera actividad para crear uno.")
        sessions.take(3).forEach { session -> SessionCard(session) { detail(session.id) } }
        Text("Herramienta de orientación, no un dispositivo médico.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Page(title: String, subtitle: String = "", padding: PaddingValues = PaddingValues(), content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
        Spacer(Modifier.height(12.dp))
    }
}
@Composable
fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
fun Notice(message: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun MenuCard(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) {
    Card(onClick = action, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = Forest)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Forest)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
fun ConfirmDialog(title: String, description: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(confirm) { Text("Eliminar") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}
fun formatDate(time: Long): String = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es"))
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
fun decimal(value: Double): String = String.format(Locale.forLanguageTag("es"), "%.1f", value)
