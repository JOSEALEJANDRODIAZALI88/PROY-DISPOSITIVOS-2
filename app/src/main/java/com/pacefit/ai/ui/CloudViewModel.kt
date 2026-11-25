package com.pacefit.ai.ui

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.*
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.data.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudUiState(val loaded: Boolean = false, val busy: Boolean = false,
    val message: String? = null, val error: String? = null, val entries: List<SharedCityEntry> = emptyList(),
    val more: Boolean = true, val suggestion: PhotoSuggestion? = null, val verificationEmail: String? = null)

class CloudViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PaceFitApplication>()
    private val repository get() = app.remoteRepository
    val configured get() = repository.configured
    val session = repository.session
    private val mutable = MutableStateFlow(CloudUiState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                app.initialization.join()
                mutable.update { it.copy(loaded = true, error = if (app.accountRecoveryError)
                    "No se pudo recuperar la eliminación pendiente. Reintenta la recuperación antes de compartir." else null) }
            }
            catch (_: Exception) { mutable.update { it.copy(loaded = true, error = "No se pudo restaurar la sesión. Reintenta desde Cuenta.") } }
        }
        viewModelScope.launch {
            session.collect { mutable.update { it.copy(entries = emptyList(), more = true, suggestion = null) } }
        }
    }
    fun signIn(email: String, password: String, create: Boolean, name: String, consent: Boolean) = action {
        require(!create || consent) { "Acepta la cuenta pública. Tus datos de salud no se publican." }
        require(!create || name.trim().length in 2..50) { "Elige un nombre público de 2 a 50 caracteres." }
        val signedIn = repository.signIn(email, password, create)
        if (!signedIn) mutable.update { it.copy(verificationEmail = email.trim(),
            message = "Revisa tu correo e introduce el código de confirmación. No compartas el código.") }
        else {
            if (consent && name.trim().length >= 2) repository.acceptProfile(name)
            mutable.update { it.copy(message = "Sesión iniciada. La salud y las rutas permanecen en SQLite.") }
        }
    }
    fun verifyEmail(email: String, code: String) = action {
        repository.verifyEmail(email, code)
        mutable.update { it.copy(verificationEmail = null,
            message = "Correo confirmado y sesión guardada. Acepta tu nombre público antes de publicar.") }
    }
    fun resendConfirmation(email: String) = action {
        repository.resendConfirmation(email)
        mutable.update { it.copy(message = "Solicitud enviada. Revisa también spam; espera antes de pedir otro código.") }
    }
    fun acceptProfile(name: String, consent: Boolean) = action {
        require(consent) { "Acepta el perfil público." }
        repository.acceptProfile(name)
        mutable.update { it.copy(message = "Perfil público actualizado.") }
    }
    fun logout() = action { repository.logout(); mutable.update { it.copy(message = "Sesión cerrada y tokens de renovación revocados.") } }
    fun retryRecovery(onErased: () -> Unit) = action {
        if (repository.restore()) onErased()
        mutable.update { it.copy(message = "Recuperación completada.") }
    }
    fun feed(more: Boolean = false) = action {
        val old = if (more) state.value.entries else emptyList()
        val entries = repository.feed(old.size)
        mutable.update { it.copy(entries = (old + entries).distinctBy { row -> row.id }, more = entries.size == 100) }
    }
    fun classify(uri: String, plogging: Boolean, consent: Boolean) = action {
        require(consent) { "Autoriza enviar esta foto, sin ubicación ni salud, a la IA." }
        val store = PrivatePhotoStore(app, File(app.cacheDir, "ai-preview"))
        val suggestion = withContext(Dispatchers.IO) {
            val name = store.importPhoto(uri.toUri(), PhotoDirectory.REPORTS)
            try { repository.classify(store.file(PhotoDirectory.REPORTS, name), plogging) }
            finally { store.delete(PhotoDirectory.REPORTS, name) }
        }
        mutable.update { it.copy(suggestion = suggestion) }
    }
    fun resetSuggestion() = mutable.update { it.copy(suggestion = null) }
    fun publish(id: String, plogging: Boolean) = action {
        repository.publish(id, plogging, true)
        mutable.update { it.copy(message = "Publicado. Los puntos se calculan en el servidor.") }
    }
    fun resolve(entry: SharedCityEntry) = action {
        repository.resolve(entry.id, !entry.resolved)
        mutable.update { it.copy(entries = it.entries.map { row -> if (row.id == entry.id) row.copy(resolved = !row.resolved) else row }) }
    }
    fun delete(entry: SharedCityEntry) = action {
        repository.deletePublication(entry.id)
        mutable.update { it.copy(entries = it.entries.filterNot { row -> row.id == entry.id }, message = "Publicación y foto eliminadas del servidor.") }
    }
    fun deleteAccount(confirmation: String, onErased: () -> Unit) = action {
        require(confirmation == "ELIMINAR") { "Escribe ELIMINAR exactamente." }
        require(app.activityRepository.unfinished() == null) { "Finaliza primero la actividad para evitar nuevas escrituras." }
        repository.deleteAccount(confirmation)
        onErased()
        mutable.update { CloudUiState(loaded = true, busy = true, message = "Cuenta, publicaciones, fotos y datos locales eliminados.") }
    }
    suspend fun photoUrl(path: String) = repository.signedPhoto(path)
    fun dismiss() = mutable.update { it.copy(message = null) }
    private fun action(block: suspend () -> Unit) {
        if (!state.value.loaded || state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = if (error is RemoteError || error is IllegalArgumentException)
                    error.message else "No se pudo completar la operación. Tus datos locales se conservan.") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
