package com.pacefit.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pacefit.ai.data.HealthRepository
import com.pacefit.ai.data.PaceFitModulesDao
import com.pacefit.ai.domain.HealthProfile
import com.pacefit.ai.domain.ProfileDraft
import com.pacefit.ai.domain.ProfileValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loaded: Boolean = false,
    val profile: HealthProfile? = null,
    val draft: ProfileDraft = ProfileDraft(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
    val storageError: Boolean = false,
)

class ProfileViewModel(private val repository: HealthRepository, val modulesDao: PaceFitModulesDao? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.observe().collect { profile ->
                    mutableState.update {
                        it.copy(
                            loaded = true,
                            profile = profile,
                            draft = if (!it.loaded && profile != null) ProfileDraft.from(profile) else it.draft,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update {
                    it.copy(storageError = true, message = "No se pudo leer el perfil. Cierra y vuelve a abrir la app.")
                }
            }
        }
    }

    fun edit(draft: ProfileDraft) {
        if (!state.value.loaded || state.value.saving) return
        mutableState.update { it.copy(draft = draft, errors = emptyMap()) }
    }

    fun save() {
        if (!state.value.loaded || state.value.saving || state.value.storageError) return
        val validation = ProfileValidator.validate(state.value.draft, System.currentTimeMillis())
        mutableState.update { it.copy(errors = validation.errors) }
        val profile = validation.profile ?: return
        persist("Perfil guardado solo en este teléfono.") {
            repository.save(profile)
            mutableState.update { it.copy(profile = profile) }
        }
    }

    fun delete() {
        if (!state.value.loaded || state.value.saving || state.value.storageError) return
        persist("Perfil eliminado de la aplicación.") {
            repository.delete()
            mutableState.update { it.copy(profile = null, draft = ProfileDraft(), errors = emptyMap()) }
        }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun persist(success: String, action: suspend () -> Unit) {
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                action()
                mutableState.update { it.copy(message = success) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(message = "No se pudo guardar el cambio. Tus datos anteriores se conservan.") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    class Factory(private val repository: HealthRepository, private val modulesDao: PaceFitModulesDao? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(repository, modulesDao) as T
        }
    }
}
