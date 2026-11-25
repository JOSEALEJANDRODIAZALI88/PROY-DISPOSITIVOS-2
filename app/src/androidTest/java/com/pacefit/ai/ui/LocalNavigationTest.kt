package com.pacefit.ai.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pacefit.ai.MainActivity
import com.pacefit.ai.PaceFitApplication
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

/** Runner substitutes a test-only SQLite/root/session namespace; never touches production records. */
@RunWith(AndroidJUnit4::class)
class LocalNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PaceFitApplication
    private var image: File? = null
    @Before fun cleanTestStorage() = runBlocking { app.initialization.join(); app.localRepository.eraseAll() }
    @After fun release() { image?.delete() }
    private fun openHealth() {
        compose.onNodeWithText("Perfil", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Crear mi perfil") or hasText("Editar mi perfil")).performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Peso (kg)") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun back() { compose.onNodeWithContentDescription("Volver").performClick() }
    private fun dismissSavedNotice() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Cerrar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cerrar").performClick()
    }
    private fun choosePhoto() {
        image = File(app.cacheDir, "pacefit-ui-test/ui-${UUID.randomUUID()}.jpg").also { it.parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        image!!.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        val uri = FileProvider.getUriForFile(app, "com.pacefit.ai.debug.files", image!!)
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            compose.onNodeWithText("Seleccionar foto").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Vista previa de la foto seleccionada").assertExists()
        } finally { Intents.release() }
    }
    @Test fun dataScreenShowsTablesColumnsAndPrivateDirectories() {
        compose.onNodeWithText("Perfil", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Datos SQLite").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Vista de hasta 50 filas", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Fotos de plogging:", substring = true).assertExists()
        compose.onNodeWithTag("sqlite-table").performScrollTo()
        compose.onNodeWithText("weightKg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("local_settings").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("detectionWindowSeconds").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun cityWizardPersistsPhotoAndManualCommaCoordinatesWithoutGpsCall() {
        compose.onNodeWithText("Ciudad", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Plogging").performClick()
        choosePhoto()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onNodeWithText("Medio").performScrollTo().performClick()
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onNodeWithText("Latitud (-90 a 90)").performScrollTo().performTextReplacement("-16,5")
        compose.onNodeWithText("Longitud (-180 a 180)").performScrollTo().performTextReplacement("-68,1")
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onNodeWithText("Confirmo la clasificación", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Guardar plogging").performScrollTo().performClick()
        compose.waitUntil(30_000) { runBlocking { app.localRepository.plogging.first().size == 1 } }
        val entry = runBlocking { app.localRepository.plogging.first().single() }
        Assert.assertEquals(-16.5, entry.latitude, 0.0001)
        Assert.assertEquals("MEDIUM", entry.quantity)
        Assert.assertTrue(app.localRepository.photos.file(com.pacefit.ai.data.PhotoDirectory.PLOGGING, entry.photoName).exists())
        compose.onNodeWithText("Mis registros · 1").performScrollTo().performClick()
        compose.onNodeWithText("Recogida · Medio").assertExists()
    }
    @Test fun profileDraftSurvivesNavigationAndRecreation() {
        openHealth()
        compose.onNodeWithText("Peso (kg)").performTextReplacement("72,5")
        back()
        compose.onNodeWithText("Inicio", useUnmergedTree = true).performClick()
        openHealth()
        compose.onNodeWithText("Peso (kg)").assertTextContains("72,5")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Peso (kg)").assertTextContains("72,5")
    }
    @Test fun profileCreateEditAndConfirmedDeleteAreRealUiActions() {
        openHealth()
        compose.onNodeWithText("Peso (kg)").performTextReplacement("72,5")
        compose.onNodeWithText("Estatura (cm)").performTextReplacement("175")
        compose.onNodeWithText("Edad (años)").performTextReplacement("25")
        compose.onNodeWithText("Guardar perfil").performScrollTo().performClick()
        compose.waitUntil(20_000) { runBlocking { app.healthRepository.observe().first()?.weightKg == 72.5 } }
        dismissSavedNotice()
        compose.onNodeWithText("Peso (kg)").performScrollTo().performTextReplacement("73,5")
        compose.onNodeWithText("Peso (kg)").assertTextContains("73,5")
        compose.onNodeWithText("Guardar perfil").performScrollTo().performClick()
        compose.waitUntil(20_000) { runBlocking { app.healthRepository.observe().first()?.weightKg == 73.5 } }
        dismissSavedNotice()
        compose.onNodeWithText("Eliminar mi perfil").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        Assert.assertNotNull(runBlocking { app.healthRepository.observe().first() })
        compose.onNodeWithText("Eliminar mi perfil").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) { runBlocking { app.healthRepository.observe().first() == null } }
        compose.onNodeWithText("Peso (kg)").performScrollTo().assertTextContains("")
    }
    @Test fun accountReflectsActualServerConfiguration() {
        compose.onNodeWithText("Perfil", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Acceder a mi cuenta").performScrollTo().performClick()
        if (com.pacefit.ai.BuildConfig.SUPABASE_URL.startsWith("https://") &&
            com.pacefit.ai.BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            compose.onNodeWithText("Servidor sin conectar").assertDoesNotExist()
            compose.onNodeWithText("Correo electrónico").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Contraseña").performScrollTo().assertIsEnabled()
        } else {
            compose.onNodeWithText("Servidor sin conectar").assertExists()
            compose.onNodeWithText("Correo electrónico").performScrollTo().assertIsNotEnabled()
        }
    }
    @Test fun emailConfirmationScreenRequiresCompleteCodeAndSurvivesRecreationWithoutCode() {
        compose.onNodeWithText("Perfil", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Acceder a mi cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Ya tengo un código de confirmación").performScrollTo().performClick()
        compose.onNodeWithText("Correo electrónico").performScrollTo().performTextReplacement("owned@example.test")
        compose.onNodeWithText("Confirmar mi correo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Código de confirmación").performScrollTo().performTextReplacement("123")
        compose.onNodeWithText("Confirmar mi correo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Código de confirmación").performScrollTo().performTextReplacement("123456")
        if (com.pacefit.ai.BuildConfig.SUPABASE_URL.startsWith("https://") &&
            com.pacefit.ai.BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            compose.onNodeWithText("Confirmar mi correo").performScrollTo().assertIsEnabled()
            compose.onNodeWithText("Código de confirmación").performScrollTo().performTextReplacement("12345678")
            compose.onNodeWithText("Confirmar mi correo").performScrollTo().assertIsEnabled()
        }
        // Deliberately never submit fake codes or send mail. These are UI-only synthetic inputs.
        compose.activityRule.scenario.recreate()
        Assert.assertEquals("", compose.onNodeWithText("Código de confirmación").performScrollTo()
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Confirmar mi correo").performScrollTo().assertIsNotEnabled()
    }
}
