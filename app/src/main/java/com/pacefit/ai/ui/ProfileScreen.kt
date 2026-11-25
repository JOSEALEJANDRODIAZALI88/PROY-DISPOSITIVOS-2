package com.pacefit.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pacefit.ai.domain.ProfileSex

@Composable
fun ProfileEditor(state: ProfileUiState, viewModel: ProfileViewModel, padding: PaddingValues, blocked: Boolean) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val enabled = state.loaded && !state.saving && !state.storageError && !blocked
    Page("Tu salud, en privado", "Este perfil nunca se envía a Supabase ni a la IA.", padding) {
        if (!state.loaded || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.storageError) Notice("No se pudo abrir el perfil. Vuelve a abrir la app antes de editar.", true)
        SurfaceCard {
            ProfileField("Peso (kg)", state.draft.weight, enabled, state.errors["weight"]) { viewModel.edit(state.draft.copy(weight = it)) }
            ProfileField("Estatura (cm)", state.draft.height, enabled, state.errors["height"]) { viewModel.edit(state.draft.copy(height = it)) }
            ProfileField("Edad (años)", state.draft.age, enabled, state.errors["age"], KeyboardType.Number) { viewModel.edit(state.draft.copy(age = it)) }
            Text("Sexo · opcional", style = MaterialTheme.typography.labelLarge)
            ProfileSex.entries.forEach { sex ->
                FilterChip(state.draft.sex == sex, { viewModel.edit(state.draft.copy(sex = sex)) },
                    enabled = enabled, label = { Text(sex.label) })
            }
        }
        Text("Aceptamos coma decimal. Los límites evitan errores de escritura; no son una evaluación médica.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = viewModel::save, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Guardando…" else "Guardar perfil")
        }
        state.profile?.let {
            Text("Último guardado: ${formatDate(it.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ confirmDelete = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Eliminar mi perfil") }
        }
    }
    if (confirmDelete) ConfirmDialog("¿Eliminar el perfil?", "Se elimina solo el perfil de salud de este teléfono.",
        { confirmDelete = false }, { confirmDelete = false; viewModel.delete() })
}

@Composable
private fun ProfileField(label: String, value: String, enabled: Boolean, error: String?,
    keyboard: KeyboardType = KeyboardType.Decimal, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 16) onChange(it) }, Modifier.fillMaxWidth(), label = { Text(label) },
        enabled = enabled, singleLine = true, isError = error != null,
        supportingText = if (error == null) null else ({ Text(error) }),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}
