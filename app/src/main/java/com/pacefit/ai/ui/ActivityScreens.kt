package com.pacefit.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pacefit.ai.data.ActivitySessionEntity
import com.pacefit.ai.domain.ActivityHint
import com.pacefit.ai.domain.TransportClass

@Composable
fun ActivityScreen(viewModel: ActivityViewModel, local: LocalViewModel, requestGps: (String) -> Unit, detail: (String) -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val hint by viewModel.hint.collectAsStateWithLifecycle()
    val storage by local.state.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val current = sessions.firstOrNull { it.endedAt == null }
    var shown by rememberSaveable { mutableIntStateOf(20) }
    var finish by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(current?.id) { viewModel.selectedId.value = current?.id }
    Page("Tu próxima salida", "Camina, corre y conserva el recorrido sin mezclar viajes en vehículo.") {
        SurfaceCard {
            Text(if (current == null) "Todo listo para empezar" else when (current.state) {
                "ACTIVE" -> "Actividad en curso"
                "PAUSED" -> "Tómate un respiro"
                else -> "Actividad interrumpida · puedes continuar"
            }, style = MaterialTheme.typography.titleLarge)
            Text("Actividad indicada por ti:")
            Choices(ActivityHint.entries, hint, { it.label }, viewModel::choose)
            Text("Ventana de transporte: ${storage.windowSeconds} segundos. La detección combina GPS, pasos y tu indicación.",
                style = MaterialTheme.typography.bodySmall)
            if (current == null) {
                Button({ requestGps("START") }, enabled = storage.loaded && !storage.busy && !storage.storageError,
                    modifier = Modifier.fillMaxWidth()) { Text("Iniciar actividad") }
            } else {
                Text("${duration(current.durationMillis)} · ${decimal(segments.filter { it.includedInMetrics }.sumOf { it.distanceMeters })} m a pie",
                    style = MaterialTheme.typography.headlineSmall)
                Text(recording.status)
                recording.speedKmh?.let { Text("${decimal(it)} km/h") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recording.running) Button({ viewModel.command("PAUSE") }) { Text("Pausar") }
                    else Button({ requestGps("RESUME") }) { Text("Continuar") }
                    OutlinedButton({ finish = true }) { Text("Finalizar") }
                }
                OutlinedButton({ detail(current.id) }) { Text("Ver recorrido y tramos") }
            }
        }
        if (current != null) {
            MapPanel(points, segments)
            Text("Verde: a pie · Naranja: vehículo · Gris: sin determinar.", style = MaterialTheme.typography.bodySmall)
        }
        Notice("Con la notificación activa puedes apagar la pantalla. No se inicia GPS al arrancar el teléfono. Si Android mata el proceso, el recorrido guardado se conserva y tendrás que continuar explícitamente.")
        Text("Historial de actividades", style = MaterialTheme.typography.titleLarge)
        val history = sessions.filter { it.endedAt != null }
        if (history.isEmpty()) Notice("Todavía no hay actividades finalizadas.")
        history.take(shown).forEach { session -> SessionCard(session) { detail(session.id) } }
        if (history.size > shown) OutlinedButton({ shown += 20 }) { Text("Mostrar más actividades") }
    }
    if (finish) AlertDialog(onDismissRequest = { finish = false }, title = { Text("¿Finalizar actividad?") },
        text = { Text("Se conservarán la ruta y todos los tramos detectados. La última ventana incompleta queda sin determinar y no aporta metros deportivos.") },
        confirmButton = { TextButton({ finish = false; viewModel.command("FINISH") }) { Text("Finalizar y guardar") } },
        dismissButton = { TextButton({ finish = false }) { Text("Seguir") } })
}

@Composable
fun ActivityDetail(id: String, viewModel: ActivityViewModel) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val points by viewModel.points.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.select(id) }
    val session = sessions.firstOrNull { it.id == id }
    var shown by rememberSaveable(id) { mutableIntStateOf(30) }
    Page("Cada tramo cuenta", session?.let { formatDate(it.startedAt) } ?: "Cargando actividad…") {
        if (session == null) Notice("No se encontró la actividad o todavía se está cargando.")
        else {
            SurfaceCard {
                Text("${duration(session.durationMillis)} de actividad", style = MaterialTheme.typography.titleLarge)
                Text("${decimal(segments.filter { it.includedInMetrics }.sumOf { it.distanceMeters })} m a pie")
                Text("${decimal(segments.filter { !it.includedInMetrics }.sumOf { it.distanceMeters })} m excluidos de métricas deportivas")
                Text("${points.size} ubicaciones precisas · ${segments.size} tramos")
            }
            MapPanel(points.filter { it.sessionId == id }, segments)
            Notice("Vehículo y no determinado se conservan, pero no suman metros deportivos. No se calculan calorías ni zonas sin los módulos correspondientes; esta heurística necesita validación de campo.")
            Text("Tramos detectados", style = MaterialTheme.typography.titleLarge)
            if (segments.isEmpty()) Text("No se completó ninguna ventana. Las ubicaciones válidas sí quedan en la ruta.")
            segments.take(shown).forEach { segment ->
                SurfaceCard {
                    Text(TransportClass.entries.firstOrNull { it.name == segment.classification }?.label ?: segment.classification,
                        fontWeight = FontWeight.Bold)
                    Text("${formatDate(segment.startedAt)} → ${formatDate(segment.endedAt)}")
                    Text("${decimal(segment.distanceMeters)} m · ${decimal(segment.averageSpeedKmh)} km/h · ${segment.steps?.let { "$it pasos" } ?: "sin lectura de pasos"}")
                    Text(if (segment.includedInMetrics) "Incluido en metros a pie" else "Excluido de metros, energía y zonas deportivas")
                }
            }
            if (segments.size > shown) OutlinedButton({ shown += 30 }) { Text("Mostrar más tramos") }
        }
    }
}

@Composable
fun SessionCard(session: ActivitySessionEntity, open: () -> Unit) {
    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatDate(session.startedAt), fontWeight = FontWeight.Bold)
            Text("${duration(session.durationMillis)} · ${if (session.endedAt == null) "En curso" else "Finalizada"}")
            Text("Ver ruta y transporte →", color = MaterialTheme.colorScheme.primary)
        }
    }
}
fun duration(millis: Long): String = "%02d:%02d:%02d".format(millis / 3600000, millis / 60000 % 60, millis / 1000 % 60)

@Composable
fun <T> Choices(values: List<T>, selected: T, label: (T) -> String, choose: (T) -> Unit, enabled: Boolean = true) {
    // Vertical choices remain usable with large text, without hidden horizontal options.
    Column { values.forEach { FilterChip(selected == it, { choose(it) }, label = { Text(label(it)) }, enabled = enabled) } }
}
