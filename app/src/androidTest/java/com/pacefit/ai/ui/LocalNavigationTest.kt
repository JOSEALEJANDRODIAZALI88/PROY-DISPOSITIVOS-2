package com.pacefit.ai.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pacefit.ai.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads production storage but never saves or deletes a user's records. */
@RunWith(AndroidJUnit4::class)
class LocalNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dataTabShowsSqliteTablesColumnsAndPrivateDirectories() {
        compose.onNodeWithText("Datos").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Vista de hasta 50 filas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Fotos de plogging:", substring = true).assertExists()
        // Bring the table into the vertical viewport before scrolling its columns horizontally.
        compose.onNodeWithTag("sqlite-table").performScrollTo()
        compose.onNodeWithText("weightKg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("local_settings").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("detectionWindowSeconds").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("detectionWindowSeconds").assertExists()
    }

    @Test fun cityAcceptsManualCoordinatesWithoutLocationPermission() {
        compose.onNodeWithText("Ciudad").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("Latitud (-90 a 90)") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Latitud (-90 a 90)").performScrollTo().performTextReplacement("-16,5")
        compose.onNodeWithText("Longitud (-180 a 180)").performScrollTo().performTextReplacement("-68,1")
        compose.onNodeWithText("Guardar plogging").performScrollTo().performClick()
        compose.onNodeWithText("Selecciona una foto.").assertIsDisplayed()
    }

    @Test fun profileDraftSurvivesTabChangeAndActivityRecreation() {
        compose.onNodeWithText("Perfil").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("Peso (kg)") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Peso (kg)").performTextReplacement("72,5")
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Perfil").performClick()
        compose.onNodeWithText("Peso (kg)").assertTextContains("72,5")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Peso (kg)").assertTextContains("72,5")
    }
}
