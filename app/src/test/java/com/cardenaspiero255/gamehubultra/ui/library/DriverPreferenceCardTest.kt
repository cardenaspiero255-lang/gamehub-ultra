package com.cardenaspiero255.gamehubultra.ui.library

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.platform.gpu.AndroidGraphicsDriverPreferences
import com.cardenaspiero255.gamehubultra.platform.gpu.OpenGlDriverPreference
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriverPreferenceCardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val packageName = "com.example.car54game"

    private fun androidMapping(packages: String?, values: String?) {
        Settings.Global.putString(context.contentResolver,
            "angle_gl_driver_selection_pkgs", packages)
        Settings.Global.putString(context.contentResolver,
            "angle_gl_driver_selection_values", values)
    }

    @Before fun cleanState() {
        AndroidGraphicsDriverPreferences.save(context, packageName, OpenGlDriverPreference.SYSTEM)
        androidMapping(null, null)
    }

    @After fun resetState() {
        AndroidGraphicsDriverPreferences.save(context, packageName, OpenGlDriverPreference.SYSTEM)
        androidMapping(null, null)
    }

    @Test fun defaultGameShowsGenuineSystemFallbackAndTurnipLimit() {
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText("Driver OpenGL por juego").assertIsDisplayed()
        composeRule.onNodeWithText("Sistema").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sin cambio de driver aplicado por GameHub; Android decide."
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Turnip no se instala ni se activa desde Android estándar sin un backend compatible y comprobado."
        ).assertIsDisplayed()
    }

    @Test fun chooseAnglePersistsPerGameAndExplainsManualConfiguration() {
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText("ANGLE").performClick()
        composeRule.onNodeWithText(
            "Preferencia pendiente: debes seleccionarla también para este juego en Opciones de desarrollador de Android. GameHub no la aplica automáticamente."
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(OpenGlDriverPreference.ANGLE,
                AndroidGraphicsDriverPreferences.selected(context, packageName))
        }
        composeRule.onNodeWithText("Sistema").performClick()
        composeRule.onNodeWithText(
            "Sin cambio de driver aplicado por GameHub; Android decide."
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(OpenGlDriverPreference.SYSTEM,
                AndroidGraphicsDriverPreferences.selected(context, packageName))
        }
    }

    @Test fun verifiedAngleSystemSettingStillDoesNotClaimLoadedDriver() {
        androidMapping(packageName, "angle")
        AndroidGraphicsDriverPreferences.save(context, packageName, OpenGlDriverPreference.ANGLE)
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText(
            "Preferencia de Android confirmada para este paquete. El driver cargado dentro del juego aún no está verificado."
        ).assertIsDisplayed()
    }

    @Test fun nativePreferenceMustMatchActualPackageMapping() {
        androidMapping(packageName, "angle")
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText("Nativo").performClick()
        composeRule.onNodeWithText(
            "Preferencia pendiente: debes seleccionarla también para este juego en Opciones de desarrollador de Android. GameHub no la aplica automáticamente."
        ).assertIsDisplayed()
        androidMapping(packageName, "native")
        // A new composition picks up the externally changed readback.
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText(
            "Preferencia de Android confirmada para este paquete. El driver cargado dentro del juego aún no está verificado."
        ).assertIsDisplayed()
    }

    @Test fun malformedAndroidOverrideIsUnverifiableNotDriverSuccess() {
        androidMapping(packageName, null)
        AndroidGraphicsDriverPreferences.save(context, packageName, OpenGlDriverPreference.ANGLE)
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText(
            "No es posible verificar de forma segura la preferencia del sistema."
        ).assertIsDisplayed()
    }

    @Test fun settingsButtonIsAvailableButDoesNotWriteGpuSettings() {
        composeRule.setContent { DriverPreferenceCard(packageName) }
        composeRule.onNodeWithText("Abrir opciones de desarrollador").assertIsDisplayed()
        // Read-only confirmation; no Settings.Global write is performed here.
        composeRule.onNodeWithText("Sistema").assertIsDisplayed()
    }
}
