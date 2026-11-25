package com.pacefit.ai.ui

import android.app.Application
import androidx.lifecycle.*
import com.pacefit.ai.PaceFitApplication
import com.pacefit.ai.data.*
import com.pacefit.ai.domain.ActivityHint
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PaceFitApplication>()
    val sessions = app.activityRepository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recording = ActivityRecording.state
    private val mutableHint = MutableStateFlow(ActivityHint.WALK)
    val hint = mutableHint.asStateFlow()
    val selectedId = MutableStateFlow<String?>(null)
    val points = selectedId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else app.activityRepository.points(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val segments = selectedId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else app.activityRepository.segments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun choose(hint: ActivityHint) {
        mutableHint.value = hint
        if (recording.value.sessionId != null) ActivityRecording.command(app, "HINT", hint)
    }
    fun command(action: String) {
        try { ActivityRecording.command(app, action, hint.value) }
        catch (_: RuntimeException) { ActivityRecording.mutable.update { it.copy(error = "No se pudo iniciar. Abre la app y revisa GPS y permisos.") } }
    }
    fun select(id: String) { selectedId.value = id }
}
