package com.pacefit.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pacefit.ai.BuildConfig
import java.io.File
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AccountSession(val userId: String, val email: String, val accessToken: String,
    val refreshToken: String, val expiresAt: Long)
data class SharedCityEntry(val id: String, val ownerId: String, val kind: String, val label: String,
    val description: String, val latitude: Double, val longitude: Double, val points: Int,
    val resolved: Boolean, val photoPath: String)
data class PhotoSuggestion(val label: String, val reason: String, val unsafe: Boolean)
class RemoteError(message: String, val status: Int = 0) : Exception(message)

/** Tokens are encrypted with a non-exportable Android Keystore key, never prefs/plaintext/backup. */
internal class SessionStore(context: Context, namespace: String = "") {
    private val file = File(context.noBackupFilesDir, "pacefit-session$namespace.enc")
    private val alias = "pacefit.account.session.v1$namespace"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): AccountSession? {
        if (!file.exists()) return null
        return try {
            val data = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
            decodeSession(JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)))
        } catch (_: Exception) { clear(); null } // Key invalidated/restored ciphertext is unusable.
    }
    fun write(session: AccountSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val json = JSONObject().put("user_id", session.userId).put("email", session.email)
            .put("access_token", session.accessToken).put("refresh_token", session.refreshToken).put("expires_at", session.expiresAt)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        java.io.FileOutputStream(temporary).use {
            it.write(cipher.iv); it.write(cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))); it.fd.sync()
        }
        check(temporary.renameTo(file))
    }
    fun clear() { check(!file.exists() || file.delete()) }
    private fun decodeSession(json: JSONObject) = AccountSession(json.getString("user_id"), json.getString("email"),
        json.getString("access_token"), json.getString("refresh_token"), json.getLong("expires_at"))
}

class RemoteRepository(context: Context, private val database: HealthDatabase, private val local: LocalRepository) {
    private val namespace = if (context is com.pacefit.ai.PaceFitApplication &&
        context.databaseName != com.pacefit.ai.PaceFitApplication.DATABASE_NAME) "-instrumentation" else ""
    private val deletionAck = File(context.noBackupFilesDir, "pacefit-account-delete$namespace.pending")
    private val deletionRequest = File(context.noBackupFilesDir, "pacefit-account-delete$namespace.request")
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anon = BuildConfig.SUPABASE_ANON_KEY
    val configured = base.startsWith("https://") && anon.isNotBlank()
    private val store = SessionStore(context, namespace)
    private val mutableSession = MutableStateFlow<AccountSession?>(null)
    val session = mutableSession.asStateFlow()
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    suspend fun restore() = withContext(Dispatchers.IO) {
        var erased = false
        if (deletionAck.exists()) { finishLocalDeletion(); erased = true }
        else if (deletionRequest.exists() && configured) {
            // A prior explicit deletion may have succeeded with its response lost.
            val receipt = JSONObject(deletionRequest.readText()).getString("request_id")
            try {
                raw("/functions/v1/account-delete", "POST", json(JSONObject().put("confirmation", "ELIMINAR").put("requestId", receipt)))
                java.io.FileOutputStream(deletionAck).use { it.write(1); it.fd.sync() }
                finishLocalDeletion()
                erased = true
            } catch (_: RemoteError) { /* Offline/incomplete: preserve real local data and allow retry. */ }
        }
        mutableSession.value = store.read()
        erased
    }
    private fun requireConfig() {
        if (!configured) throw RemoteError("Falta conectar Supabase. Consulta Configuración del servidor; no hay un servidor simulado.")
    }
    private suspend fun raw(path: String, method: String = "GET", body: RequestBody? = null, token: String? = null): String =
        withContext(Dispatchers.IO) {
            requireConfig()
            val request = Request.Builder().url("$base$path").header("apikey", anon)
                .header("Authorization", "Bearer ${token ?: anon}").method(method, body).build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val safeMessage = if (path == "/auth/v1/verify" && response.code in 400..403) {
                            "Código incorrecto o caducado. Comprueba el correo o solicita otro código."
                        } else if (path.startsWith("/functions/")) runCatching {
                            JSONObject(text).optString("error").takeIf { it.isNotBlank() && it.length <= 300 }
                        }.getOrNull() else null
                        throw RemoteError(safeMessage ?: when (response.code) {
                            400, 422 -> "Revisa el correo, la contraseña (mínimo 10 caracteres) y la confirmación del correo."
                            401, 403 -> "No autorizado. Revisa la sesión y los permisos de tu cuenta."
                            409 -> "El registro ya existe."
                            429 -> "Demasiados intentos. Espera y vuelve a intentar."
                            else -> "El servidor no pudo completar la operación (${response.code})."
                        }, response.code)
                    }
                    text
                }
            } catch (error: java.io.IOException) { throw RemoteError("No hay conexión con el servidor. Tus datos locales se conservan.") }
        }
    private fun json(value: JSONObject) = value.toString().toRequestBody(jsonType)
    private suspend fun token(force: Boolean = false): String = mutex.withLock {
        val old = session.value ?: throw RemoteError("Inicia sesión para compartir en Ciudad.")
        if (!force && old.expiresAt > System.currentTimeMillis() + 60_000) return@withLock old.accessToken
        try {
            val data = JSONObject(raw("/auth/v1/token?grant_type=refresh_token", "POST",
                json(JSONObject().put("refresh_token", old.refreshToken))))
            val updated = parseSession(data)
            withContext(Dispatchers.IO) { store.write(updated) }
            mutableSession.value = updated
            updated.accessToken
        } catch (error: RemoteError) {
            if (error.status in setOf(400, 401, 403)) clearSession()
            throw error
        }
    }
    private suspend fun authorized(path: String, method: String = "GET", body: RequestBody? = null): String {
        val token = token()
        return try { raw(path, method, body, token) } catch (error: RemoteError) {
            if (error.status != 401) throw error
            raw(path, method, body, token(true))
        }
    }
    private fun parseSession(data: JSONObject): AccountSession {
        val user = data.getJSONObject("user")
        return AccountSession(user.getString("id"), user.optString("email"), data.getString("access_token"),
            data.getString("refresh_token"), System.currentTimeMillis() + data.getLong("expires_in") * 1000)
    }
    suspend fun signIn(email: String, password: String, create: Boolean): Boolean {
        require(email.contains('@') && password.length >= if (create) 10 else 1) { "Introduce correo y contraseña válidos (10 caracteres para crear una cuenta)." }
        val data = JSONObject(raw(if (create) "/auth/v1/signup" else "/auth/v1/token?grant_type=password", "POST",
            json(JSONObject().put("email", email.trim()).put("password", password))))
        if (!data.has("access_token")) return false // Real email verification, not a pretend session.
        val parsed = parseSession(data)
        withContext(Dispatchers.IO) { store.write(parsed) }
        mutableSession.value = parsed
        return true
    }
    suspend fun verifyEmail(email: String, code: String) {
        require(email.trim().contains('@') && code.matches(Regex("(?:[0-9]{6}|[0-9]{8})"))) {
            "Introduce el correo y el código completo recibido (6 u 8 dígitos)."
        }
        val data = JSONObject(raw("/auth/v1/verify", "POST",
            json(JSONObject().put("email", email.trim()).put("token", code).put("type", "email"))))
        val parsed = parseSession(data)
        withContext(Dispatchers.IO) { store.write(parsed) }
        mutableSession.value = parsed
    }
    suspend fun resendConfirmation(email: String) {
        require(email.trim().contains('@')) { "Introduce el correo de registro." }
        raw("/auth/v1/resend", "POST", json(JSONObject().put("email", email.trim()).put("type", "signup")))
    }
    suspend fun acceptProfile(name: String) {
        require(name.trim().length in 2..50) { "El nombre público debe tener de 2 a 50 caracteres." }
        authorized("/rest/v1/rpc/accept_social_profile", "POST", json(JSONObject().put("p_display_name", name.trim())))
    }
    suspend fun logout() {
        // Require the real server logout to succeed; don't falsely claim refresh tokens were revoked offline.
        authorized("/auth/v1/logout?scope=global", "POST", "".toRequestBody())
        clearSession()
    }
    suspend fun clearSession() = withContext(Dispatchers.IO) { store.clear(); mutableSession.value = null }
    suspend fun feed(offset: Int): List<SharedCityEntry> {
        val array = JSONArray(authorized("/rest/v1/city_entries?select=*&order=created_at.desc,id.desc&limit=100&offset=$offset"))
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            SharedCityEntry(row.getString("id"), row.getString("owner_id"), row.getString("kind"), row.getString("label"),
                row.getString("description"), row.getDouble("latitude"), row.getDouble("longitude"), row.getInt("points"),
                row.getBoolean("resolved"), row.getString("photo_path"))
        }
    }
    suspend fun signedPhoto(path: String): String {
        require(path.matches(Regex("[0-9a-f-]{36}/[0-9a-f-]{36}\\.jpg")))
        val result = JSONObject(authorized("/storage/v1/object/sign/city-photos/$path", "POST",
            json(JSONObject().put("expiresIn", 120))))
        return "$base/storage/v1${result.getString("signedURL")}"
    }
    suspend fun classify(file: File, plogging: Boolean): PhotoSuggestion = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("kind", if (plogging) "PLOGGING" else "REPORT")
            .put("jpegBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        val data = JSONObject(authorized("/functions/v1/ai-gateway", "POST",
            json(JSONObject().put("task", "photo").put("payload", payload)))).getJSONObject("data")
        PhotoSuggestion(data.getString("label"), data.getString("reason"), data.getBoolean("unsafe"))
    }
    suspend fun publish(id: String, plogging: Boolean, consent: Boolean) {
        require(consent) { "Autoriza explícitamente publicar foto y ubicación." }
        val uid = session.value?.userId ?: throw RemoteError("Inicia sesión.")
        // The local mutation lock keeps delete/photo cleanup from racing with an upload.
        local.access {
            val dao = database.localDao()
            val p = if (plogging) dao.findPlogging(id) else null
            val r = if (!plogging) dao.findReport(id) else null
            require(p != null || r != null) { "El registro local ya no existe." }
            val old = database.publicationDao().find(id)
            require(old == null || old.ownerId == uid) { "Este registro pertenece a otra cuenta; no se vuelve a publicar." }
            val file = local.photos.file(if (plogging) PhotoDirectory.PLOGGING else PhotoDirectory.REPORTS, p?.photoName ?: r!!.photoName)
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("id", id).addFormDataPart("kind", if (plogging) "PLOGGING" else "REPORT")
                .addFormDataPart("label", p?.quantity ?: r!!.kind)
                .addFormDataPart("description", r?.description ?: "")
                .addFormDataPart("latitude", (p?.latitude ?: r!!.latitude).toString())
                .addFormDataPart("longitude", (p?.longitude ?: r!!.longitude).toString()).addFormDataPart("consent", "true")
                .addFormDataPart("photo", "photo.jpg", file.readBytes().toRequestBody("image/jpeg".toMediaType())).build()
            authorized("/functions/v1/city-submit", "POST", body)
            database.publicationDao().save(PublicationEntity(id, uid, id, if (plogging) "PLOGGING" else "REPORT"))
        }
    }
    suspend fun resolve(id: String, resolved: Boolean) {
        authorized("/rest/v1/rpc/set_report_resolved", "POST", json(JSONObject().put("p_id", id).put("p_resolved", resolved)))
    }
    suspend fun deletePublication(id: String) {
        authorized("/functions/v1/city-delete", "POST", json(JSONObject().put("id", id)))
        local.access { database.publicationDao().delete(id) }
    }
    suspend fun deleteAccount(confirmation: String) {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
            val user = requireNotNull(session.value) { "Inicia sesión." }
            val old = if (deletionRequest.exists()) JSONObject(deletionRequest.readText()) else null
            val receipt = if (old?.optString("user_id") == user.userId) old.getString("request_id") else {
                val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                val nonce = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
                java.io.FileOutputStream(deletionRequest).use {
                    it.write(JSONObject().put("user_id", user.userId).put("request_id", nonce).toString().toByteArray()); it.fd.sync()
                }
                nonce
            }
            val body = json(JSONObject().put("confirmation", confirmation).put("requestId", receipt))
            try { raw("/functions/v1/account-delete", "POST", body, user.accessToken) }
            catch (error: RemoteError) {
                if (error.status != 401) throw error
                authorized("/functions/v1/account-delete", "POST", body)
            }
            java.io.FileOutputStream(deletionAck).use { it.write(1); it.fd.sync() }
            finishLocalDeletion()
        }
    }
    private suspend fun finishLocalDeletion() {
        clearSession()
        local.eraseAll()
        check(!deletionRequest.exists() || deletionRequest.delete())
        check(deletionAck.delete()) { "El borrado local se completará al reiniciar." }
    }
}
