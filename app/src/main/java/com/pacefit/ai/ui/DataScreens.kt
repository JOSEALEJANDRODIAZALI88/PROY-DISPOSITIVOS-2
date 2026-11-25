package com.pacefit.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pacefit.ai.BuildConfig
import com.pacefit.ai.data.LocalRepository

@Composable
fun SettingsScreen(local: LocalViewModel, data: () -> Unit, account: () -> Unit, unfinished: Boolean) {
    val state by local.state.collectAsStateWithLifecycle()
    Page("A tu manera", "Controla cómo registras y qué compartes.") {
        SurfaceCard {
            Text("Detección de transporte", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(state.windowDraft, { if (it.length <= 3) local.editWindow(it) },
                Modifier.fillMaxWidth(), label = { Text("Ventana de detección (15–120 segundos)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = state.loaded && !state.busy && !unfinished && !state.storageError, singleLine = true)
            OutlinedButton(local::saveWindow, enabled = state.loaded && !state.busy && !unfinished && !state.storageError) { Text("Guardar ventana") }
            Text("Los cambios se aplican a la siguiente actividad. No se clasifica correr como vehículo solo por superar 7 km/h.")
        }
        SurfaceCard {
            Text("Privado por defecto", style = MaterialTheme.typography.titleLarge)
            Text("• Salud y rutas en SQLite del teléfono.\n• Fotos privadas, comprimidas y sin EXIF.\n• Publicación solo con consentimiento.\n• Sin permiso de cámara, almacenamiento ni ubicación «siempre».\n• Backup y transferencia Android deshabilitados.")
            Text("Abrir mapas consulta teselas de OpenStreetMap: el proveedor recibe tu IP y el área visible, no el perfil de salud ni una carga de tu recorrido.")
        }
        OutlinedButton(data, Modifier.fillMaxWidth()) { Text("Ver tablas SQLite y directorios") }
        OutlinedButton(account, Modifier.fillMaxWidth()) { Text("Cuenta y eliminación remota") }
        Notice("La clasificación de transporte es una heurística no clínica. La IA solo sugiere etiquetas; tú las confirmas. Sin servicios configurados no hay funciones remotas.")
        Text("${BuildConfig.VERSION_NAME} · Android 8 o posterior", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DataScreen(state: LocalUiState, viewModel: LocalViewModel, padding: PaddingValues,
    profileSaving: Boolean, onErased: () -> Unit) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    val enabled = state.loaded && !state.busy && !state.storageError && !profileSaving
    LaunchedEffect(state.loaded) { if (state.loaded && state.snapshot == null) viewModel.refreshTable() }
    Page("Tus datos, a la vista", "SQLite real mediante Room · esquema 3", padding) {
        if (!state.loaded || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.storageError) Notice("No se pudo abrir el almacenamiento.", true)
        SurfaceCard {
            Text("Rutas privadas", style = MaterialTheme.typography.titleLarge)
            SelectionContainer {
                Text("Base de datos:\n${viewModel.databasePath}\n\nFotos de plogging:\n${viewModel.filesPath}/photos/plogging\n\nFotos de reportes:\n${viewModel.filesPath}/photos/reports")
            }
            Text("Persisten al cerrar la app. Desinstalar o borrar los datos de Android elimina esta instalación.")
        }
        Text("Elige una tabla", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalRepository.TABLES.forEach { table ->
                FilterChip(state.table == table, { viewModel.selectTable(table) }, label = { Text(table) }, enabled = enabled)
            }
        }
        OutlinedButton(viewModel::refreshTable, enabled = enabled) { Text("Actualizar tabla") }
        state.snapshot?.let { snapshot ->
            Text("${snapshot.name}: ${snapshot.count} registros. Vista de hasta 50 filas recientes.", fontWeight = FontWeight.Bold)
            SelectionContainer {
                Column(Modifier.testTag("sqlite-table").horizontalScroll(rememberScrollState())) {
                    Row { snapshot.columns.forEach { Text(it, Modifier.width(175.dp).padding(8.dp), fontWeight = FontWeight.Bold) } }
                    snapshot.rows.forEach { row -> Row { row.forEach { Text(it, Modifier.width(175.dp).padding(8.dp)) } } }
                }
            }
            if (snapshot.rows.isEmpty()) Text("Tabla vacía. Las columnas se muestran arriba.")
        }
        Notice("El visor es de solo lectura. No exporta datos a carpetas públicas. SQLite no cifra el perfil; no compartas la base con información de salud.")
        HorizontalDivider()
        Text("Eliminar del teléfono", style = MaterialTheme.typography.titleLarge)
        Text("No elimina la cuenta ni las publicaciones del servidor. Para eso usa Cuenta → Eliminar mi cuenta. Tampoco borra fotos originales ni garantiza borrado forense.")
        OutlinedButton({ confirmErase = true }, enabled = enabled) { Text("Eliminar todos mis datos locales") }
        if (profileSaving) Text("Finaliza la actividad y espera a que terminen las operaciones antes de borrar.")
    }
    if (confirmErase) ConfirmDialog("¿Eliminar todos los datos locales?",
        "Se eliminarán salud, rutas, tramos, registros, fotos y ajustes de este teléfono. La cuenta remota se conserva.",
        { confirmErase = false }, { confirmErase = false; viewModel.eraseAll(onErased) })
}
