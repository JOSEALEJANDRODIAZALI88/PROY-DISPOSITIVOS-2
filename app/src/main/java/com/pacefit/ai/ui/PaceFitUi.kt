package com.pacefit.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pacefit.ai.domain.ProfileSex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Lime = Color(0xFFB9F66B)
private val Ink = Color(0xFF101820)
private val Muted = Color(0xFFBDC9CD)
private val Palette = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    background = Ink,
    surface = Color(0xFF19252D),
    surfaceVariant = Color(0xFF22343E),
    onBackground = Color(0xFFF1F6F7),
    onSurface = Color(0xFFF1F6F7),
    onSurfaceVariant = Muted,
)

@Composable
fun PaceFitRoot(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    MaterialTheme(colorScheme = Palette) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Palette.surface) {
                    listOf("Inicio", "Mi perfil", "Próximos tickets").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(listOf("01", "02", "03")[index], fontWeight = FontWeight.Bold) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                0 -> Home(state, padding) { tab = 1 }
                1 -> ProfileEditor(state, viewModel, padding)
                else -> Roadmap(padding)
            }
        }
    }
}

@Composable
private fun Home(state: ProfileUiState, padding: PaddingValues, openProfile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header("PACEFIT AI", "Tu próximo paso empieza aquí.")
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF304C3B), Color(0xFF1B343C))), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("HECHO PARA LA PAZ", color = Lime, style = MaterialTheme.typography.labelLarge)
                Text("Tu salud.\nEn tu teléfono.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Primera beta · PF-001\nPerfil privado, editable y disponible sin internet.", color = Color(0xFFDFEBE7))
                Button(onClick = openProfile, enabled = state.loaded) {
                    Text(if (state.profile == null) "Crear mi perfil" else "Editar mi perfil")
                }
            }
        }
        if (!state.loaded && !state.storageError) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.profile?.let { profile ->
            InfoCard("Tu perfil local", "${profile.weightKg} kg  ·  ${profile.heightCm} cm  ·  ${profile.age} años\n${profile.sex.label}")
        }
        if (state.storageError) InfoCard("Almacenamiento no disponible", "Cierra y vuelve a abrir la aplicación. No sobrescribiremos tu perfil.")
        InfoCard("Privado desde el primer ticket", "Sin cuenta, anuncios ni conexión a servidores. Esta beta no solicita permisos y excluye sus datos de las copias de seguridad y transferencias Android.")
        InfoCard("Qué incluye esta versión", "Crear, consultar, editar y eliminar el perfil de salud. GPS, IA, BLE, funciones sociales y reportes todavía no están implementados.")
        Text("Herramienta de orientación, no un dispositivo médico.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("0.1.0-beta.1 · Android 8.0 o posterior", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProfileEditor(state: ProfileUiState, viewModel: ProfileViewModel, padding: PaddingValues) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val enabled = state.loaded && !state.saving && !state.storageError
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("MI PERFIL", "Solo tú tienes acceso.")
        Text("El perfil se guarda en este teléfono. Puedes modificarlo o eliminarlo cuando quieras.", color = Muted)
        if (!state.loaded && !state.storageError) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.storageError) InfoCard("No se pudo abrir el perfil", "Cierra y vuelve a abrir la app antes de editar.")
        ProfileField(
            label = "Peso (kg)", value = state.draft.weight, enabled = enabled,
            error = state.errors["weight"],
        ) { viewModel.edit(state.draft.copy(weight = it)) }
        ProfileField(
            label = "Estatura (cm)", value = state.draft.height, enabled = enabled,
            error = state.errors["height"],
        ) { viewModel.edit(state.draft.copy(height = it)) }
        ProfileField(
            label = "Edad (años)", value = state.draft.age, enabled = enabled,
            error = state.errors["age"], keyboard = KeyboardType.Number,
        ) { viewModel.edit(state.draft.copy(age = it)) }
        Text("Sexo · opcional", style = MaterialTheme.typography.labelLarge)
        Column {
            ProfileSex.entries.forEach { sex ->
                FilterChip(
                    selected = state.draft.sex == sex,
                    onClick = { viewModel.edit(state.draft.copy(sex = sex)) },
                    enabled = enabled,
                    label = { Text(sex.label) },
                )
            }
        }
        Text("Los rangos del formulario evitan errores de escritura; no son rangos clínicos ni una evaluación de salud.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Guardando…" else "Guardar perfil")
        }
        state.profile?.let { profile ->
            val updated = remember(profile.updatedAt) {
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(profile.updatedAt))
            }
            Text("Último guardado: $updated", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmDelete = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Eliminar mi perfil")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar el perfil?") },
            text = { Text("Se eliminará el perfil guardado en esta aplicación. No podrás recuperarlo desde la app.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    enabled: Boolean,
    error: String?,
    keyboard: KeyboardType = KeyboardType.Decimal,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 16) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun Roadmap(padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("PASO A PASO", "Una beta, tickets verificables.")
        InfoCard("PF-001 · Implementado", "Base Android, navegación y perfil local con Room. La verificación y sus límites se registran en TICKETS.md.")
        Text("Lo siguiente todavía no está implementado:", color = Muted)
        listOf(
            "PF-002 · Parámetros y fuentes" to "Catálogo trazable; supuestos y límites explícitos.",
            "PF-003 · GPS y actividad" to "Rutas, servicio en primer plano, barómetro y pasos.",
            "PF-004 / 005 · Esfuerzo y altitud" to "Tobler, Minetti, agua, electrolitos, calorías y UV.",
            "PF-006 / 007 · Banda y transporte" to "Pulso BLE y tramos de vehículo.",
            "PF-008 / 009 · Alimentación" to "Alimentos locales y comidas; confirmación de fotos.",
            "PF-010 / 011 · Cuenta y seguridad" to "Supabase, RLS, consentimiento y borrado.",
            "PF-012 · Asesor de IA" to "Gateway, límite por usuario y memoria de siete días.",
            "PF-013 / 014 · Comunidad" to "Corredores, solicitudes, chat, bloqueo y zonas.",
            "PF-015 · Ciudad" to "Plogging, fotos y reportes en el mapa.",
            "PF-016 / 017 · Reportes y aclimatación" to "PDF sin internet y semáforo orientativo.",
            "PF-018 / 019 · Rutas creativas" to "Menor esfuerzo y dibujo GPS.",
            "PF-020 · Verificación" to "Pruebas en dispositivos y entrega beta integrada.",
        ).forEach { (title, body) -> InfoCard(title, body) }
        Text("Consulta TICKETS.md en el repositorio para dependencias, alcance y criterios de aceptación.", color = Muted)
    }
}

@Composable
private fun Header(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kicker, color = Lime, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
