package com.pacefit.ai.ui

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pacefit.ai.data.ForegroundMovementMonitor
import com.pacefit.ai.data.LocalRepository
import com.pacefit.ai.data.PhotoDirectory
import com.pacefit.ai.data.PloggingEntity
import com.pacefit.ai.data.TableSnapshot
import com.pacefit.ai.data.TransportSegmentEntity
import com.pacefit.ai.data.UrbanReportEntity
import com.pacefit.ai.data.captureCoordinates
import com.pacefit.ai.domain.ActivityHint
import com.pacefit.ai.domain.LocalInput
import com.pacefit.ai.domain.ReportKind
import com.pacefit.ai.domain.WasteQuantity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CityDraft(
    val latitude: String = "",
    val longitude: String = "",
    val photoUri: String? = null,
    val quantity: WasteQuantity = WasteQuantity.LITTLE,
    val kind: ReportKind = ReportKind.WASTE,
    val description: String = "",
    val consent: Boolean = false,
)

data class LocalUiState(
    val loaded: Boolean = false,
    val storageError: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val segments: List<TransportSegmentEntity> = emptyList(),
    val plogging: List<PloggingEntity> = emptyList(),
    val reports: List<UrbanReportEntity> = emptyList(),
    val draft: CityDraft = CityDraft(),
    val windowSeconds: Int = 30,
    val windowDraft: String = "30",
    val activity: ActivityHint = ActivityHint.UNKNOWN,
    val monitoring: Boolean = false,
    val movementStatus: String = "Detección detenida.",
    val speedKmh: Double? = null,
    val capturingLocation: Boolean = false,
    val table: String = "health_profile",
    val snapshot: TableSnapshot? = null,
)

class LocalViewModel(application: Application, private val repository: LocalRepository) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(LocalUiState())
    val state = mutableState.asStateFlow()
    private var monitor: ForegroundMovementMonitor? = null
    private var locationJob: Job? = null
    private val pendingSegments = mutableSetOf<Job>()
    private val applicationContext get() = getApplication<Application>()
    val databasePath: String get() = repository.databaseFile.absolutePath
    val filesPath: String get() = repository.photos.root.absolutePath

    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                val seconds = repository.windowSeconds()
                mutableState.update { it.copy(loaded = true, windowSeconds = seconds, windowDraft = seconds.toString()) }
                combine(repository.segments, repository.plogging, repository.reports) { segments, plogging, reports ->
                    Triple(segments, plogging, reports)
                }.collect { (segments, plogging, reports) ->
                    mutableState.update { it.copy(segments = segments, plogging = plogging, reports = reports) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(storageError = true, message = "No se pudo abrir el almacenamiento. Vuelve a abrir la app para reintentar.") }
            }
        }
    }

    fun editDraft(draft: CityDraft) {
        if (!state.value.busy) mutableState.update { it.copy(draft = draft) }
    }

    fun editWindow(value: String) {
        if (!state.value.busy && !state.value.monitoring) mutableState.update { it.copy(windowDraft = value) }
    }

    fun chooseActivity(activity: ActivityHint) {
        mutableState.update { it.copy(activity = activity) }
    }

    fun saveWindow() {
        if (state.value.monitoring) return
        val seconds = LocalInput.windowSeconds(state.value.windowDraft)
        if (seconds == null) { showMessage("La ventana debe ser de 15 a 120 segundos."); return }
        action("Ventana guardada en SQLite.") {
            repository.saveWindow(seconds)
            mutableState.update { it.copy(windowSeconds = seconds) }
        }
    }

    fun startMonitoring() {
        if (!ready() || state.value.monitoring) return
        try {
            val newMonitor = ForegroundMovementMonitor(
                applicationContext, state.value.windowSeconds, { state.value.activity },
                onReading = { speed, hasSteps ->
                    mutableState.update {
                        it.copy(speedKmh = speed, movementStatus = if (hasSteps) "GPS y pasos disponibles." else "GPS disponible; sin lectura de pasos.")
                    }
                },
                onSegment = { segment ->
                    val job = viewModelScope.launch {
                        try {
                            repository.saveSegment(segment)
                            mutableState.update { it.copy(movementStatus = "Tramo guardado: ${segment.classification.label}.") }
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            stopMonitoring()
                            showMessage("No se pudo guardar el tramo. Se detuvo la detección.")
                        }
                    }
                    pendingSegments += job
                    job.invokeOnCompletion { pendingSegments -= job }
                },
                onStatus = { status -> mutableState.update { it.copy(movementStatus = status) } },
            )
            newMonitor.start()
            monitor = newMonitor
            mutableState.update { it.copy(monitoring = true) }
        } catch (error: Exception) {
            showMessage(if (error is IllegalStateException) error.message ?: "Activa el GPS." else "No se pudo iniciar: comprueba el permiso de ubicación precisa.")
        }
    }

    fun stopMonitoring() {
        monitor?.stop()
        monitor = null
        mutableState.update { it.copy(monitoring = false, speedKmh = null, movementStatus = "Detección detenida. Las ventanas incompletas no se guardan.") }
    }

    fun captureLocation() {
        if (!ready() || state.value.capturingLocation) return
        mutableState.update { it.copy(capturingLocation = true) }
        locationJob = viewModelScope.launch {
            try {
                val coordinates = captureCoordinates(applicationContext)
                mutableState.update {
                    it.copy(draft = it.draft.copy(latitude = coordinates.latitude.toString(), longitude = coordinates.longitude.toString()))
                }
            } catch (_: TimeoutCancellationException) {
                showMessage("No se obtuvo GPS preciso en 30 segundos. Puedes escribir las coordenadas.")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showMessage("No se pudo obtener la ubicación. Activa el GPS o escribe las coordenadas.")
            } finally {
                mutableState.update { it.copy(capturingLocation = false) }
            }
        }
    }

    fun stopForegroundWork() {
        stopMonitoring()
        locationJob?.cancel()
    }

    fun saveCityEntry(plogging: Boolean) {
        val draft = state.value.draft
        val coordinates = LocalInput.coordinates(draft.latitude, draft.longitude)
        if (coordinates == null) { showMessage("Escribe latitud y longitud válidas; se acepta coma decimal."); return }
        val photo = draft.photoUri
        if (photo == null) { showMessage("Selecciona una foto."); return }
        if (!draft.consent) { showMessage("Confirma la clasificación y el permiso para guardar foto y ubicación."); return }
        action("Registro guardado solo en este teléfono.") {
            if (plogging) repository.addPlogging(draft.quantity, coordinates, photo.toUri(), draft.consent)
            else repository.addReport(draft.kind, draft.description, coordinates, photo.toUri(), draft.consent)
            mutableState.update { it.copy(draft = CityDraft()) }
        }
    }

    fun resolveReport(report: UrbanReportEntity) = action("Estado actualizado.") {
        repository.resolveReport(report.id, !report.resolved)
    }

    fun deleteReport(id: String) = action("Reporte y foto eliminados.") { repository.deleteReport(id) }
    fun deletePlogging(id: String) = action("Registro y foto eliminados.") { repository.deletePlogging(id) }

    fun selectTable(table: String) {
        if (table !in LocalRepository.TABLES || state.value.busy) return
        mutableState.update { it.copy(table = table, snapshot = null) }
        refreshTable()
    }

    fun refreshTable() = action(null) { refreshSnapshot() }

    private suspend fun refreshSnapshot() {
        val table = state.value.table
        val snapshot = repository.inspect(table)
        mutableState.update { if (it.table == table) it.copy(snapshot = snapshot) else it }
    }

    fun eraseAll(onErased: () -> Unit) {
        if (!ready()) return
        stopForegroundWork()
        action("Datos y fotos locales eliminados. No existía una cuenta remota.") {
            val writes = pendingSegments.toList()
            writes.forEach { it.cancel() }
            writes.joinAll()
            repository.eraseAll()
            mutableState.update { LocalUiState(loaded = true, busy = true) }
            onErased()
        }
    }

    fun photoFile(kind: PhotoDirectory, name: String): File = repository.photos.file(kind, name)
    fun showMessage(message: String) = mutableState.update { it.copy(message = message) }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    private fun ready() = state.value.loaded && !state.value.busy && !state.value.storageError

    private fun action(success: String?, block: suspend () -> Unit) {
        if (!ready()) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
                if (success != null) mutableState.update { it.copy(message = success) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showMessage(if (error is IllegalArgumentException) error.message ?: "Entrada no válida."
                    else "No se pudo completar el cambio. Revisa los datos o vuelve a abrir la app; un borrado interrumpido se reintentará.")
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        monitor?.stop()
        locationJob?.cancel()
    }

    class Factory(private val application: Application, private val repository: LocalRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocalViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return LocalViewModel(application, repository) as T
        }
    }
}
