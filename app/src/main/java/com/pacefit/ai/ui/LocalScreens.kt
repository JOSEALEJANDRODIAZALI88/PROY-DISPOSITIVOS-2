package com.pacefit.ai.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pacefit.ai.data.LocalRepository
import com.pacefit.ai.data.PhotoDirectory
import com.pacefit.ai.domain.ActivityHint
import com.pacefit.ai.domain.ReportKind
import com.pacefit.ai.domain.TransportClass
import com.pacefit.ai.domain.WasteQuantity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TransportScreen(state: LocalUiState, viewModel: LocalViewModel, padding: PaddingValues, requestGps: () -> Unit) {
    FeaturePage("TRANSPORTE LOCAL", padding, state) {
        Text("Solo mientras la app está visible. Al salir o apagar la pantalla, se detiene. No es el registro en segundo plano de PF-003.")
        OutlinedTextField(
            value = state.windowDraft, onValueChange = { if (it.length <= 3) viewModel.editWindow(it) },
            label = { Text("Ventana de detección (15–120 segundos)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = state.loaded && !state.busy && !state.monitoring && !state.storageError,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedButton(onClick = viewModel::saveWindow, enabled = state.loaded && !state.busy && !state.monitoring && !state.storageError) {
            Text("Guardar ventana")
        }
        Text("Actividad indicada por ti (no reconocimiento por IA):")
        ChoiceRow(ActivityHint.entries, state.activity, { it.label }, viewModel::chooseActivity, !state.busy)
        Text("Combina velocidad sostenida, pasos disponibles y tu indicación. Sin evidencia suficiente conserva «No determinado». Correr a más de 7 km/h no se marca por sí solo como vehículo.")
        if (state.monitoring) {
            Button(onClick = viewModel::stopMonitoring) { Text("Detener detección") }
        } else {
            Button(onClick = requestGps, enabled = state.loaded && !state.busy && !state.storageError) { Text("Iniciar detección con GPS") }
        }
        Text("Ventana guardada: ${state.windowSeconds} s · ${state.movementStatus}")
        state.speedKmh?.let { Text("Velocidad actual: ${number(it)} km/h") }
        Text("Historial: últimos 200 tramos. Se muestran los 50 más recientes.", fontWeight = FontWeight.Bold)
        if (state.segments.isEmpty()) Text("No hay tramos guardados. Se necesita completar una ventana con GPS preciso.")
        state.segments.take(50).forEach { segment ->
            FeatureCard {
                Text(TransportClass.entries.firstOrNull { it.name == segment.classification }?.label ?: segment.classification, fontWeight = FontWeight.Bold)
                Text("${date(segment.startedAt)} → ${date(segment.endedAt)}")
                Text("${number(segment.distanceMeters)} m · ${number(segment.averageSpeedKmh)} km/h · pasos: ${segment.steps ?: "sin sensor"}")
                Text(if (segment.includedInMetrics) "Apto para futuras métricas deportivas." else "Excluido de metros, energía y zonas deportivas.")
            }
        }
        Text("No se calculan calorías ni zonas en esta entrega. Las distancias se estiman a partir de la velocidad GPS y no están validadas en campo.")
    }
}

@Composable
fun CityScreen(state: LocalUiState, viewModel: LocalViewModel, padding: PaddingValues, requestGps: () -> Unit) {
    var plogging by rememberSaveable { mutableStateOf(true) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteIsPlogging by rememberSaveable { mutableStateOf(false) }
    val enabled = state.loaded && !state.busy && !state.storageError
    val draft = state.draft
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.editDraft(viewModel.state.value.draft.copy(photoUri = uri.toString(), consent = false))
    }
    FeaturePage("CIUDAD · SOLO EN ESTE TELÉFONO", padding, state) {
        Text("Registros privados, sin publicación ni mapa compartido. Las fotos se recomprimen como JPEG, sin EXIF. No fotografíes personas ni incluyas datos personales sin consentimiento.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(plogging, { plogging = true }, label = { Text("Plogging") }, enabled = enabled)
            FilterChip(!plogging, { plogging = false }, label = { Text("Reporte urbano") }, enabled = enabled)
        }
        if (plogging) {
            Text("Confirma la cantidad recogida:")
            ChoiceRow(WasteQuantity.entries, draft.quantity, { "${it.label} · ${it.points} pts" },
                { viewModel.editDraft(draft.copy(quantity = it, consent = false)) }, enabled)
            Text("Puntos locales orientativos, recalculados desde la cantidad. No son puntos verificados por un servidor.")
        } else {
            Text("Confirma la clasificación:")
            ChoiceRow(ReportKind.entries, draft.kind, { it.label },
                { viewModel.editDraft(draft.copy(kind = it, consent = false)) }, enabled)
            OutlinedTextField(
                value = draft.description,
                onValueChange = { if (it.length <= 500) viewModel.editDraft(draft.copy(description = it)) },
                label = { Text("Descripción opcional (${draft.description.length}/500)") },
                enabled = enabled, modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(onClick = {
            try { picker.launch("image/*") } catch (_: Exception) { viewModel.showMessage("No se pudo abrir el selector de fotos.") }
        }, enabled = enabled) { Text(if (draft.photoUri == null) "Seleccionar foto" else "Cambiar foto seleccionada") }
        if (draft.photoUri != null) Text("Foto seleccionada. Se guardará una copia privada sin metadatos al confirmar.")
        OutlinedTextField(
            value = draft.latitude, onValueChange = { if (it.length <= 24) viewModel.editDraft(draft.copy(latitude = it)) },
            label = { Text("Latitud (-90 a 90)") }, enabled = enabled && !state.capturingLocation,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = draft.longitude, onValueChange = { if (it.length <= 24) viewModel.editDraft(draft.copy(longitude = it)) },
            label = { Text("Longitud (-180 a 180)") }, enabled = enabled && !state.capturingLocation,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedButton(onClick = requestGps, enabled = enabled && !state.capturingLocation) {
            Text(if (state.capturingLocation) "Buscando GPS (hasta 30 s)…" else "Usar ubicación GPS actual")
        }
        Text("También puedes escribir las coordenadas sin conceder permisos.")
        Row {
            Checkbox(checked = draft.consent, onCheckedChange = { viewModel.editDraft(draft.copy(consent = it)) }, enabled = enabled)
            Text("Confirmo la clasificación y tengo permiso para guardar esta foto y ubicación solo en este teléfono.", Modifier.padding(top = 12.dp))
        }
        Button(onClick = { viewModel.saveCityEntry(plogging) }, enabled = enabled && !state.capturingLocation) {
            Text(if (plogging) "Guardar plogging" else "Guardar reporte")
        }
        Text("Historial local · se muestran los 50 registros más recientes de cada tipo", style = MaterialTheme.typography.titleMedium)
        if (plogging) {
            if (state.plogging.isEmpty()) Text("Todavía no hay registros de plogging.")
            Text("Puntos en los registros mostrados: ${state.plogging.take(50).sumOf { it.points }}")
            state.plogging.take(50).forEach { entry ->
                FeatureCard {
                    Text("${WasteQuantity.entries.firstOrNull { it.name == entry.quantity }?.label ?: entry.quantity} · ${entry.points} puntos", fontWeight = FontWeight.Bold)
                    Text(date(entry.createdAt))
                    Text("${entry.latitude}, ${entry.longitude}")
                    PhotoPreview(viewModel.photoFile(PhotoDirectory.PLOGGING, entry.photoName))
                    OutlinedButton(onClick = { deleteId = entry.id; deleteIsPlogging = true }, enabled = enabled) { Text("Eliminar registro") }
                }
            }
        } else {
            if (state.reports.isEmpty()) Text("Todavía no hay reportes.")
            state.reports.take(50).forEach { report ->
                FeatureCard {
                    Text("${ReportKind.entries.firstOrNull { it.name == report.kind }?.label ?: report.kind} · ${if (report.resolved) "Resuelto" else "Pendiente"}", fontWeight = FontWeight.Bold)
                    Text(date(report.createdAt))
                    Text("${report.latitude}, ${report.longitude}")
                    if (report.description.isNotBlank()) Text(report.description)
                    PhotoPreview(viewModel.photoFile(PhotoDirectory.REPORTS, report.photoName))
                    OutlinedButton(onClick = { viewModel.resolveReport(report) }, enabled = enabled) {
                        Text(if (report.resolved) "Marcar pendiente" else "Marcar resuelto")
                    }
                    OutlinedButton(onClick = { deleteId = report.id; deleteIsPlogging = false }, enabled = enabled) { Text("Eliminar reporte") }
                }
            }
        }
    }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("¿Eliminar registro y foto?") },
            text = { Text("Se eliminarán de este teléfono. No se puede deshacer desde la app.") },
            confirmButton = { TextButton(onClick = {
                deleteId?.let { if (deleteIsPlogging) viewModel.deletePlogging(it) else viewModel.deleteReport(it) }
                deleteId = null
            }, enabled = enabled) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun DataScreen(state: LocalUiState, viewModel: LocalViewModel, padding: PaddingValues, profileSaving: Boolean, onErased: () -> Unit) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    val enabled = state.loaded && !state.busy && !state.storageError && !profileSaving
    LaunchedEffect(state.loaded) { if (state.loaded && state.snapshot == null) viewModel.refreshTable() }
    FeaturePage("SQLITE · TABLAS Y DIRECTORIOS", padding, state) {
        Text("SQLite real mediante Room, esquema versión 2. Persiste al cerrar la app y reiniciar el teléfono; desinstalar o borrar datos de Android lo elimina.")
        Text("Rutas privadas", fontWeight = FontWeight.Bold)
        SelectionContainer {
            Text("Base de datos:\n${viewModel.databasePath}\n\nFotos de plogging:\n${viewModel.filesPath}/photos/plogging\n\nFotos de reportes:\n${viewModel.filesPath}/photos/reports")
        }
        Text("Sin exportación automática a almacenamiento público. Room/SQLite no añade cifrado a la base. No compartas bases de datos con información de salud.")
        ChoiceRow(LocalRepository.TABLES, state.table, { it }, viewModel::selectTable, enabled)
        OutlinedButton(onClick = viewModel::refreshTable, enabled = enabled) { Text("Actualizar tabla") }
        state.snapshot?.let { snapshot ->
            Text("${snapshot.name}: ${snapshot.count} registros. Vista de hasta 50 filas recientes.", fontWeight = FontWeight.Bold)
            SelectionContainer {
                Column(Modifier.testTag("sqlite-table").horizontalScroll(rememberScrollState())) {
                    Row {
                        snapshot.columns.forEach { Text(it, Modifier.width(175.dp).padding(8.dp), fontWeight = FontWeight.Bold) }
                    }
                    snapshot.rows.forEach { row ->
                        Row { row.forEach { Text(it, Modifier.width(175.dp).padding(8.dp)) } }
                    }
                }
            }
            if (snapshot.rows.isEmpty()) Text("Tabla vacía. Las columnas se muestran arriba.")
        }
        Text("Privacidad y eliminación local", style = MaterialTheme.typography.titleMedium)
        Text("No hay cuenta remota. Este control elimina el perfil, tramos, plogging, reportes, configuración y fotos de esta instalación. No borra fotos originales del selector ni garantiza borrado forense.")
        OutlinedButton(onClick = { confirmErase = true }, enabled = enabled) { Text("Eliminar todos mis datos locales") }
        Text("PF-011 remoto (cuentas, RLS, Storage y cascadas en servidor) sigue pendiente. Este borrado es únicamente local.")
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("¿Eliminar todos los datos locales?") },
            text = { Text("Se eliminarán el perfil de salud, los tramos, el plogging, los reportes, sus fotos y la configuración. Esta acción no se puede deshacer desde la app.") },
            confirmButton = { TextButton(onClick = { confirmErase = false; viewModel.eraseAll(onErased) }, enabled = enabled) { Text("Eliminar todo") } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FeaturePage(title: String, padding: PaddingValues, state: LocalUiState, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!state.loaded || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.storageError) Text("Almacenamiento no disponible. Vuelve a abrir la app antes de modificar datos.")
        content()
    }
}

@Composable
private fun FeatureCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected == value, { onSelect(value) }, label = { Text(label(value)) }, enabled = enabled)
        }
    }
}

@Composable
private fun PhotoPreview(file: File) {
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, "Foto local del registro", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit) }
}

private fun number(value: Double): String = String.format(Locale.forLanguageTag("es"), "%.1f", value)
private fun date(value: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
