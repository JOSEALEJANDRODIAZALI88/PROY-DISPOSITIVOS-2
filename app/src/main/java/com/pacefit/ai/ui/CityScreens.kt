package com.pacefit.ai.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.pacefit.ai.data.PhotoDirectory
import com.pacefit.ai.data.SharedCityEntry
import com.pacefit.ai.domain.*

@Composable
fun CityHub(local: LocalViewModel, cloud: CloudViewModel, create: (Boolean) -> Unit,
    history: () -> Unit, account: () -> Unit, detail: (String) -> Unit) {
    val state by local.state.collectAsStateWithLifecycle()
    val remote by cloud.state.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    var map by rememberSaveable { mutableStateOf(true) }
    Page("Una ciudad que cuidamos", "Recoge residuos o avisa de un problema. Tú decides qué publicar.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ create(true) }, Modifier.weight(1f), enabled = state.loaded && !state.storageError) { Text("Plogging") }
            OutlinedButton({ create(false) }, Modifier.weight(1f), enabled = state.loaded && !state.storageError) { Text("Reportar") }
        }
        OutlinedButton(history, Modifier.fillMaxWidth()) { Text("Mis registros · ${state.plogging.size + state.reports.size}") }
        Text("Mapa de la comunidad", style = MaterialTheme.typography.titleLarge)
        if (session == null) {
            Notice("Solo las cuentas autenticadas pueden consultar las publicaciones. Tus registros locales no se publican automáticamente.")
            Button(account) { Text("Iniciar sesión") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(map, { map = true }, label = { Text("Mapa") })
                FilterChip(!map, { map = false }, label = { Text("Lista") })
                TextButton({ cloud.feed() }, enabled = !remote.busy) { Text("Actualizar") }
            }
            LaunchedEffect(session?.userId) { cloud.feed() }
            if (remote.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            remote.error?.let { Notice(it, true) }
            if (remote.entries.isEmpty() && !remote.busy && remote.error == null) Notice("No hay publicaciones disponibles. Publica tu primer registro para compartirlo aquí.")
            if (map) MapPanel(pins = remote.entries.map { MapPin(it.id, it.latitude, it.longitude, entryTitle(it)) }, onPin = detail)
            else remote.entries.forEach { entry ->
                OutlinedCard(onClick = { detail(entry.id) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entryTitle(entry), style = MaterialTheme.typography.titleMedium)
                        Text(if (entry.kind == "PLOGGING") "${entry.points} puntos · calculados en servidor" else if (entry.resolved) "Resuelto" else "Pendiente")
                        Text("${entry.latitude}, ${entry.longitude}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (remote.more && remote.entries.isNotEmpty()) OutlinedButton({ cloud.feed(true) }, enabled = !remote.busy) { Text("Cargar más publicaciones") }
        }
        Text("No publiques personas, matrículas, domicilios identificables ni imágenes sin permiso.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CityWizard(plogging: Boolean, local: LocalViewModel, cloud: CloudViewModel, gps: () -> Unit, done: () -> Unit) {
    val state by local.state.collectAsStateWithLifecycle()
    val remote by cloud.state.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    val draft = state.draft
    val enabled = state.loaded && !state.busy && !remote.busy && !state.storageError
    var step by rememberSaveable(plogging) { mutableIntStateOf(0) }
    var aiConsent by rememberSaveable(plogging) { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Durable provider permission; no broad storage permission, no raw file-path assumptions.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            local.editDraft(draft.copy(photoUri = uri.toString(), consent = false, publishConsent = false))
            cloud.resetSuggestion()
        }
    }
    Page(if (plogging) "Nueva recogida" else "Nuevo aviso", "Paso ${step + 1} de 4 · ${listOf("Foto", "Clasificación", "Ubicación", "Confirmación")[step]}") {
        LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
        if (state.busy || remote.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.storageError) Notice("Almacenamiento no disponible. No se guardarán cambios.", true)
        when (step) {
            0 -> {
                Text("Muéstranos qué has encontrado", style = MaterialTheme.typography.titleLarge)
                Notice("Usa una imagen propia, sin personas ni información privada. La copia guardada será un JPEG comprimido sin metadatos.")
                draft.photoUri?.let { Photo(it, "Vista previa de la foto seleccionada") }
                OutlinedButton({ picker.launch(arrayOf("image/*")) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(if (draft.photoUri == null) "Seleccionar foto" else "Cambiar foto")
                }
            }
            1 -> {
                Text("Tú tienes la última palabra", style = MaterialTheme.typography.titleLarge)
                if (plogging) Choices(WasteQuantity.entries, draft.quantity, { it.label },
                    { local.editDraft(draft.copy(quantity = it, consent = false)) }, enabled)
                else {
                    Choices(ReportKind.entries, draft.kind, { it.label },
                        { local.editDraft(draft.copy(kind = it, consent = false)) }, enabled)
                    OutlinedTextField(draft.description, { if (it.length <= 500) local.editDraft(draft.copy(description = it, consent = false)) },
                        Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Descripción opcional (${draft.description.length}/500)") })
                }
                Notice("Puedes clasificar manualmente, también sin conexión. Los puntos publicados se calculan en el servidor: poco 5, medio 10, mucho 20; nunca los decide la IA.")
                if (session != null && cloud.configured) {
                    CheckLine(aiConsent, { aiConsent = it }, "Autorizo enviar esta foto a Gemini para una sugerencia. No se envía mi ubicación, mi perfil de salud ni mi recorrido.", enabled)
                    OutlinedButton({ draft.photoUri?.let { cloud.classify(it, plogging, aiConsent) } },
                        enabled = enabled && aiConsent && draft.photoUri != null) { Text("Pedir sugerencia de IA") }
                } else Text("Para usar la IA necesitas una cuenta y un servidor configurado. La clasificación manual sigue disponible.")
                remote.error?.let { Notice(it, true) }
                remote.suggestion?.let { suggestion ->
                    Notice(if (suggestion.unsafe) "La foto puede contener datos identificables. Cámbiala antes de publicar." else suggestion.reason, suggestion.unsafe)
                    if (!suggestion.unsafe) OutlinedButton({
                        local.editDraft(if (plogging) draft.copy(quantity = WasteQuantity.valueOf(suggestion.label), consent = false)
                            else draft.copy(kind = ReportKind.valueOf(suggestion.label), consent = false))
                    }, enabled = enabled) { Text("Usar sugerencia: ${if (plogging) WasteQuantity.valueOf(suggestion.label).label else ReportKind.valueOf(suggestion.label).label}") }
                }
            }
            2 -> {
                Text("¿Dónde está?", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(draft.latitude, { if (it.length <= 24) local.editDraft(draft.copy(latitude = it, consent = false)) },
                    Modifier.fillMaxWidth(), label = { Text("Latitud (-90 a 90)") }, enabled = enabled && !state.capturingLocation, singleLine = true)
                OutlinedTextField(draft.longitude, { if (it.length <= 24) local.editDraft(draft.copy(longitude = it, consent = false)) },
                    Modifier.fillMaxWidth(), label = { Text("Longitud (-180 a 180)") }, enabled = enabled && !state.capturingLocation, singleLine = true)
                OutlinedButton(gps, enabled = enabled && !state.capturingLocation) {
                    Text(if (state.capturingLocation) "Buscando GPS…" else "Usar ubicación GPS actual")
                }
                Text("También puedes escribir coordenadas con coma decimal sin conceder permisos.")
                LocalInput.coordinates(draft.latitude, draft.longitude)?.let {
                    MapPanel(pins = listOf(MapPin("draft", it.latitude, it.longitude, "Ubicación del registro")))
                }
            }
            3 -> {
                draft.photoUri?.let { Photo(it, "Foto que se guardará") }
                SurfaceCard {
                    Text(if (plogging) "Recogida · ${draft.quantity.label}" else "Aviso · ${draft.kind.label}", style = MaterialTheme.typography.titleLarge)
                    if (draft.description.isNotBlank() && !plogging) Text(draft.description)
                    Text("${draft.latitude}, ${draft.longitude}")
                    Text("Se guardará una copia privada sin EXIF.")
                }
                CheckLine(draft.consent, { local.editDraft(draft.copy(consent = it)) },
                    "Confirmo la clasificación y tengo permiso para guardar esta foto y ubicación.", enabled)
                if (session != null && cloud.configured && remote.suggestion?.unsafe != true) {
                    CheckLine(draft.publishConsent, { local.editDraft(draft.copy(publishConsent = it)) },
                        "Además, autorizo publicar foto y ubicación para otras cuentas autenticadas.", enabled)
                } else Notice("Este registro se guardará solo en el teléfono. Podrás publicarlo más tarde desde Mis registros.")
                Button({ local.saveCityEntry(plogging, done) },
                    enabled = enabled && draft.consent && !state.capturingLocation, modifier = Modifier.fillMaxWidth()) {
                    Text(if (draft.publishConsent) "Guardar y publicar" else if (plogging) "Guardar plogging" else "Guardar reporte")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton({ step-- }, enabled = enabled) { Text("Anterior") }
            if (step < 3) Button({
                when {
                    step == 0 && draft.photoUri == null -> local.showMessage("Selecciona una foto.")
                    step == 2 && LocalInput.coordinates(draft.latitude, draft.longitude) == null -> local.showMessage("Escribe coordenadas válidas.")
                    else -> step++
                }
            }, enabled = enabled && !state.capturingLocation, modifier = Modifier.weight(1f)) { Text("Continuar") }
        }
    }
}

@Composable
fun CityHistory(local: LocalViewModel, cloud: CloudViewModel) {
    val state by local.state.collectAsStateWithLifecycle()
    val remote by cloud.state.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    var plogging by rememberSaveable { mutableStateOf(true) }
    var shown by rememberSaveable { mutableIntStateOf(20) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var publishId by rememberSaveable { mutableStateOf<String?>(null) }
    Page("Lo que has aportado", "Todas tus copias locales. No se ocultan registros antiguos ni se suman puntos parciales.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(plogging, { plogging = true; shown = 20 }, label = { Text("Plogging (${state.plogging.size})") })
            FilterChip(!plogging, { plogging = false; shown = 20 }, label = { Text("Avisos (${state.reports.size})") })
        }
        remote.error?.let { Notice(it, true) }
        if (remote.busy || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (plogging) {
            if (state.plogging.isEmpty()) Notice("Todavía no hay recogidas. Crea una desde Ciudad.")
            state.plogging.take(shown).forEach { entry ->
                SurfaceCard {
                    Text("Recogida · ${WasteQuantity.valueOf(entry.quantity).label}", style = MaterialTheme.typography.titleLarge)
                    Text(formatDate(entry.createdAt))
                    Photo(local.photoFile(PhotoDirectory.PLOGGING, entry.photoName), "Foto de la recogida")
                    Text("${entry.latitude}, ${entry.longitude}")
                    if (session != null) OutlinedButton({ publishId = entry.id }, enabled = !remote.busy && !state.busy) { Text("Publicar / reintentar publicación") }
                    TextButton({ deleteId = entry.id }, enabled = !remote.busy && !state.busy) { Text("Eliminar copia local") }
                }
            }
        } else {
            if (state.reports.isEmpty()) Notice("Todavía no hay avisos. Crea uno desde Ciudad.")
            state.reports.take(shown).forEach { entry ->
                SurfaceCard {
                    Text("${ReportKind.valueOf(entry.kind).label} · ${if (entry.resolved) "Resuelto" else "Pendiente"}", style = MaterialTheme.typography.titleLarge)
                    Text(formatDate(entry.createdAt))
                    Photo(local.photoFile(PhotoDirectory.REPORTS, entry.photoName), "Foto del aviso")
                    if (entry.description.isNotBlank()) Text(entry.description)
                    Text("${entry.latitude}, ${entry.longitude}")
                    OutlinedButton({ local.resolveReport(entry) }, enabled = !state.busy && !remote.busy) { Text(if (entry.resolved) "Marcar pendiente local" else "Marcar resuelto local") }
                    if (session != null) OutlinedButton({ publishId = entry.id }, enabled = !remote.busy && !state.busy) { Text("Publicar / reintentar publicación") }
                    TextButton({ deleteId = entry.id }, enabled = !state.busy && !remote.busy) { Text("Eliminar copia local") }
                }
            }
        }
        if ((if (plogging) state.plogging.size else state.reports.size) > shown)
            OutlinedButton({ shown += 20 }) { Text("Mostrar más registros") }
        Notice("Cambiar o borrar una copia local no altera la publicación compartida. Gestiona esa publicación desde Ciudad; los puntos allí mostrados son los del servidor.")
    }
    if (deleteId != null) ConfirmDialog("¿Eliminar copia local?", "Se borrarán el registro y su foto del teléfono, no una publicación del servidor.",
        { deleteId = null }, { deleteId?.let { if (plogging) local.deletePlogging(it) else local.deleteReport(it) }; deleteId = null })
    if (publishId != null) AlertDialog(onDismissRequest = { publishId = null },
        title = { Text("¿Compartir con la comunidad?") },
        text = { Text("Autorizas publicar esta foto y su ubicación para otras cuentas autenticadas. No incluyas personas ni información privada. No se envían salud ni recorridos.") },
        confirmButton = { TextButton({ publishId?.let { cloud.publish(it, plogging) }; publishId = null }) { Text("Autorizar y publicar") } },
        dismissButton = { TextButton({ publishId = null }) { Text("Cancelar") } })
}

@Composable
fun SharedEntryDetail(id: String, cloud: CloudViewModel, done: () -> Unit) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    val entry = state.entries.firstOrNull { it.id == id }
    var url by remember(id) { mutableStateOf<String?>(null) }
    var photoError by remember(id) { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var deletionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(entry?.photoPath, session?.userId) {
        if (entry != null) try { url = cloud.photoUrl(entry.photoPath) } catch (_: Exception) { photoError = true }
    }
    Page(entry?.let(::entryTitle) ?: "Publicación no disponible") {
        if (entry != null) {
            url?.let { Photo(it, "Foto compartida") }
            if (photoError) Notice("No se pudo cargar la foto. Vuelve a abrir el detalle con conexión.", true)
            Text(if (entry.kind == "PLOGGING") "${entry.points} puntos · calculados en servidor" else if (entry.resolved) "Resuelto" else "Pendiente")
            if (entry.description.isNotBlank()) Text(entry.description)
            MapPanel(pins = listOf(MapPin(entry.id, entry.latitude, entry.longitude, entryTitle(entry))))
            state.error?.let { Notice(it, true) }
            if (entry.ownerId == session?.userId) {
                if (entry.kind == "REPORT") OutlinedButton({ cloud.resolve(entry) }, enabled = !state.busy) { Text(if (entry.resolved) "Reabrir aviso" else "Marcar resuelto") }
                OutlinedButton({ confirm = true }, enabled = !state.busy) { Text("Eliminar publicación y foto") }
            }
        } else Notice("Actualiza Ciudad para consultar las publicaciones disponibles.")
    }
    LaunchedEffect(state.entries, state.busy) { if (deletionRequested && !state.busy && entry == null) done() }
    if (confirm) ConfirmDialog("¿Eliminar publicación?", "Se eliminarán del servidor la publicación y su foto. La copia local se conserva.",
        { confirm = false }, { if (entry != null) { deletionRequested = true; cloud.delete(entry) }; confirm = false })
}

fun entryTitle(entry: SharedCityEntry) = if (entry.kind == "PLOGGING")
    "Plogging · ${WasteQuantity.entries.firstOrNull { it.name == entry.label }?.label ?: entry.label}"
else ReportKind.entries.firstOrNull { it.name == entry.label }?.label ?: entry.label

@Composable
fun Photo(model: Any, description: String) {
    var failed by remember(model) { mutableStateOf(false) }
    // Private/signed photos must not survive deletion in a separate image-loader cache.
    val context = LocalContext.current
    val request = remember(model, context) {
        ImageRequest.Builder(context).data(model).diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED).build()
    }
    AsyncImage(request, description, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit,
        onError = { failed = true })
    if (failed) Notice("No se pudo abrir esta foto. Si aún no se guardó, vuelve a seleccionarla.", true)
}

@Composable
fun CheckLine(checked: Boolean, change: (Boolean) -> Unit, text: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)) {
        Checkbox(checked, null, enabled = enabled)
        Text(text, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
