package com.pacefit.ai.ui

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.data.*
import com.pacefit.ai.domain.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CityDraft(val latitude: String = "", val longitude: String = "", val photoUri: String? = null,
    val quantity: WasteQuantity = WasteQuantity.LITTLE, val kind: ReportKind = ReportKind.WASTE,
    val description: String = "", val consent: Boolean = false, val publishConsent: Boolean = false)

data class LocalUiState(val loaded: Boolean = false, val storageError: Boolean = false, val busy: Boolean = false,
    val message: String? = null, val segments: List<TransportSegmentEntity> = emptyList(),
    val plogging: List<PloggingEntity> = emptyList(), val reports: List<UrbanReportEntity> = emptyList(),
    val draft: CityDraft = CityDraft(), val windowSeconds: Int = 30, val windowDraft: String = "30",
    val capturingLocation: Boolean = false, val table: String = "health_profile", val snapshot: TableSnapshot? = null)

/** City drafts and storage inspection only; activity lifecycle and cloud/account have their own VMs. */
class LocalViewModel(application: Application, private val repository: LocalRepository) : AndroidViewModel(application) {
    private val app get() = getApplication<PaceFitApplication>()
    private val mutable = MutableStateFlow(LocalUiState())
    val state = mutable.asStateFlow()
    private var locationJob: Job? = null
    private val drafts = mutableMapOf<Boolean, CityDraft>()
    private var draftIsPlogging = true
    val databasePath get() = repository.databaseFile.absolutePath
    val filesPath get() = repository.photos.root.absolutePath
    init {
        viewModelScope.launch {
            try {
                app.initialization.join()
                repository.initialize()
                val window = repository.windowSeconds()
                mutable.update { it.copy(loaded = true, windowSeconds = window, windowDraft = window.toString()) }
                combine(repository.segments, repository.plogging, repository.reports) { segments, plogging, reports ->
                    Triple(segments, plogging, reports)
                }.collect { (segments, plogging, reports) -> mutable.update { it.copy(segments = segments, plogging = plogging, reports = reports) } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(storageError = true, message = "No se pudo abrir el almacenamiento. Vuelve a abrir la app.") }
            }
        }
    }
    fun editDraft(draft: CityDraft) {
        if (!state.value.busy) { drafts[draftIsPlogging] = draft; mutable.update { it.copy(draft = draft) } }
    }
    fun openDraft(plogging: Boolean) {
        locationJob?.cancel()
        drafts[draftIsPlogging] = state.value.draft
        draftIsPlogging = plogging
        mutable.update { it.copy(draft = drafts[plogging] ?: CityDraft()) }
    }
    fun clearDrafts() { locationJob?.cancel(); drafts.clear(); mutable.update { it.copy(draft = CityDraft(), snapshot = null) } }
    fun editWindow(value: String) { if (!state.value.busy) mutable.update { it.copy(windowDraft = value) } }
    fun saveWindow() {
        val seconds = LocalInput.windowSeconds(state.value.windowDraft)
        if (seconds == null) { showMessage("La ventana debe ser de 15 a 120 segundos."); return }
        action {
            require(app.activityRepository.unfinished() == null) { "Finaliza primero la actividad." }
            repository.saveWindow(seconds)
            mutable.update { it.copy(windowSeconds = seconds, message = "Ventana guardada en SQLite.") }
        }
    }
    fun captureLocation() {
        if (!ready() || state.value.capturingLocation) return
        mutable.update { it.copy(capturingLocation = true) }
        locationJob = viewModelScope.launch {
            try {
                val coordinates = captureCoordinates(app)
                editDraft(state.value.draft.copy(latitude = coordinates.latitude.toString(), longitude = coordinates.longitude.toString(), consent = false))
            } catch (_: TimeoutCancellationException) { showMessage("No se obtuvo GPS preciso en 30 segundos. Puedes escribir las coordenadas.") }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showMessage("Activa el GPS y el permiso preciso, o escribe las coordenadas.")
            } finally { mutable.update { it.copy(capturingLocation = false) } }
        }
    }
    fun saveCityEntry(plogging: Boolean, onSaved: () -> Unit = {}) {
        val draft = state.value.draft
        val coordinates = LocalInput.coordinates(draft.latitude, draft.longitude)
        if (coordinates == null) { showMessage("Escribe coordenadas válidas; se acepta coma decimal."); return }
        val photo = draft.photoUri
        if (photo == null) { showMessage("Selecciona una foto."); return }
        if (!draft.consent) { showMessage("Confirma la clasificación y el permiso para guardar foto y ubicación."); return }
        action {
            val id = if (plogging) repository.addPlogging(draft.quantity, coordinates, photo.toUri(), true)
                else repository.addReport(draft.kind, draft.description, coordinates, photo.toUri(), true)
            // Local success is durable even if cloud publication fails: retry the SAME id from history.
            drafts[plogging] = CityDraft()
            mutable.update { it.copy(draft = CityDraft()) }
            var message = "Registro guardado en este teléfono."
            if (draft.publishConsent) try {
                app.remoteRepository.publish(id, plogging, true)
                message = "Guardado y publicado. Puntos calculados por el servidor."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "Guardado localmente; no se publicó. Reintenta desde Mis registros con conexión y cuenta."
            }
            showMessage(message); onSaved()
        }
    }
    fun resolveReport(report: UrbanReportEntity) = action { repository.resolveReport(report.id, !report.resolved) }
    fun deleteReport(id: String) = action { repository.deleteReport(id); showMessage("Copia local y foto eliminadas.") }
    fun deletePlogging(id: String) = action { repository.deletePlogging(id); showMessage("Copia local y foto eliminadas.") }
    fun selectTable(table: String) {
        if (table !in LocalRepository.TABLES || state.value.busy) return
        mutable.update { it.copy(table = table, snapshot = null) }; refreshTable()
    }
    fun refreshTable() = action {
        val table = state.value.table
        val snapshot = repository.inspect(table)
        mutable.update { if (it.table == table) it.copy(snapshot = snapshot) else it }
    }
    fun eraseAll(onErased: () -> Unit) = action {
        require(app.activityRepository.unfinished() == null) { "Finaliza primero la actividad." }
        locationJob?.cancelAndJoin()
        repository.eraseAll()
        drafts.clear()
        mutable.value = LocalUiState(loaded = true, busy = true, message = "Datos locales eliminados. La cuenta remota se conserva.")
        onErased()
    }
    fun photoFile(kind: PhotoDirectory, name: String): File = repository.photos.file(kind, name)
    fun showMessage(message: String) = mutable.update { it.copy(message = message) }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
    private fun ready() = state.value.loaded && !state.value.busy && !state.value.storageError
    private fun action(block: suspend () -> Unit) {
        if (!ready()) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showMessage(if (error is IllegalArgumentException || error is IllegalStateException)
                    error.message ?: "Entrada no válida." else "No se pudo completar el cambio. Reintenta; tus datos no se sustituyen por valores inventados.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    class Factory(private val application: Application, private val repository: LocalRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocalViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return LocalViewModel(application, repository) as T
        }
    }
}
