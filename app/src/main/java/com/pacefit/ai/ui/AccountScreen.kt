package com.pacefit.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountScreen(cloud: CloudViewModel, unfinishedActivity: Boolean, onErased: () -> Unit) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val session by cloud.session.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords deliberately do not enter saved instance state or disk.
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var verify by rememberSaveable { mutableStateOf(false) }
    // Confirmation codes are credentials too; never put them in saved instance state.
    var emailCode by remember { mutableStateOf("") }
    LaunchedEffect(session?.userId) {
        password = ""; consent = false; emailCode = ""
        if (session != null) verify = false
    }
    LaunchedEffect(state.verificationEmail) {
        state.verificationEmail?.let { email = it; verify = true; password = "" }
    }
    Page(if (session == null) "Conecta con tu comunidad" else "Tu cuenta pública",
        "La cuenta no sincroniza tu salud ni tus rutas. Solo compartes lo que autorizas.") {
        if (!cloud.configured) {
            Notice("Servidor sin conectar", true)
            Text("La app no contiene claves privadas ni inventa una sesión. Para activar cuentas, publicaciones e IA:")
            Text("1. Crea tu proyecto Supabase y aplica supabase/migrations.\n2. Despliega las cuatro Edge Functions.\n3. Configura SUPABASE_URL y SUPABASE_ANON_KEY en local.properties y recompila.\n4. Guarda GEMINI_API_KEY y GEMINI_MODEL solo como secretos del servidor.")
            Text("Instrucciones completas: docs/SERVIDOR.md en el repositorio.")
            Notice("El perfil de salud, las actividades y los registros locales sí están disponibles sin cuenta.")
        }
        if (state.busy || !state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            Notice(it, true)
            TextButton({ cloud.retryRecovery(onErased) }, enabled = !state.busy) { Text("Reintentar recuperación") }
        }
        val enabled = cloud.configured && state.loaded && !state.busy
        if (session == null) {
            if (!verify) Row {
                FilterChip(!create, { create = false }, label = { Text("Iniciar sesión") })
                Spacer(Modifier.width(androidx.compose.ui.unit.Dp(8f)))
                FilterChip(create, { create = true }, label = { Text("Crear cuenta") })
            }
            OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), label = { Text("Correo electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = enabled)
            if (verify) {
                Notice("Introduce aquí el código completo que recibiste por correo. No lo envíes al chat.")
                OutlinedTextField(emailCode, { emailCode = it.filter { char -> char in '0'..'9' }.take(8) },
                    Modifier.fillMaxWidth(), label = { Text("Código de confirmación") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = enabled)
                Button({ cloud.verifyEmail(email, emailCode) },
                    enabled = enabled && emailCode.length in setOf(6, 8) && email.contains('@'),
                    modifier = Modifier.fillMaxWidth()) { Text("Confirmar mi correo") }
                OutlinedButton({ cloud.resendConfirmation(email) }, enabled = enabled && email.contains('@')) {
                    Text("Solicitar otro código")
                }
                TextButton({ verify = false; emailCode = "" }, enabled = enabled) { Text("Volver a iniciar sesión") }
            } else {
                OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Contraseña") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, enabled = enabled)
                if (create) {
                    OutlinedTextField(name, { name = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Nombre público (2–50 caracteres)") },
                        enabled = enabled, singleLine = true)
                    CheckLine(consent, { consent = it }, "Acepto crear una cuenta pública. Solo se publicarán las fotos y ubicaciones que confirme; mi salud y mis rutas no se envían.", enabled)
                    Text("Usa al menos 10 caracteres. Recibirás un código para confirmar tu correo.", style = MaterialTheme.typography.bodySmall)
                }
                Button({ cloud.signIn(email, password, create, name, consent) },
                    enabled = enabled && (!create || consent && name.trim().length >= 2),
                    modifier = Modifier.fillMaxWidth()) { Text(if (create) "Crear mi cuenta" else "Iniciar sesión") }
                TextButton({ verify = true; password = ""; emailCode = "" }, enabled = enabled) {
                    Text("Ya tengo un código de confirmación")
                }
            }
        } else {
            SurfaceCard {
                Text(session!!.email, style = MaterialTheme.typography.titleLarge)
                Text("Sesión persistente cifrada con Android Keystore.")
            }
            Text("Perfil de la comunidad", style = MaterialTheme.typography.titleLarge)
            Text("Confirma aquí tu nombre público antes de publicar; no se comparte tu perfil de salud.")
            OutlinedTextField(name, { name = it.take(50) }, Modifier.fillMaxWidth(), label = { Text("Nombre público (2–50 caracteres)") }, enabled = enabled)
            CheckLine(consent, { consent = it }, "Acepto guardar este nombre en el perfil público de mi cuenta.", enabled)
            OutlinedButton({ cloud.acceptProfile(name, consent) }, enabled = enabled && consent && name.trim().length >= 2) { Text("Guardar perfil público") }
            OutlinedButton({ cloud.logout() }, enabled = enabled) { Text("Cerrar sesión en todos los dispositivos") }
            HorizontalDivider()
            Text("Eliminar cuenta y datos", style = MaterialTheme.typography.titleLarge)
            Notice("Se eliminarán cuenta, publicaciones, fotos remotas y todos los datos locales de esta instalación. Las copias originales del selector no se borran. Las copias de seguridad del servidor deben expirar en un máximo de 30 días.")
            if (unfinishedActivity) Text("Finaliza la actividad antes de eliminar la cuenta.")
            OutlinedButton({ delete = true }, enabled = enabled && !unfinishedActivity) { Text("Eliminar mi cuenta") }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { if (!state.busy) delete = false },
        title = { Text("Eliminación definitiva") },
        text = {
            Column {
                Text("Escribe ELIMINAR. No se puede deshacer. Requiere conexión; si falla, no se mostrará como completado.")
                OutlinedTextField(confirmation, { confirmation = it.take(8) }, label = { Text("ELIMINAR") }, enabled = !state.busy)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton({
            cloud.deleteAccount(confirmation) { delete = false; confirmation = ""; onErased() }
        }, enabled = confirmation == "ELIMINAR" && !state.busy) { Text(if (state.busy) "Eliminando…" else "Eliminar definitivamente") } },
        dismissButton = { TextButton({ delete = false }, enabled = !state.busy) { Text("Cancelar") } })
}
