package com.cardenaspiero255.gamehubultra.platform.gpu

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidGraphicsDriverPreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun gamePreferencesRemainIsolatedAndPersisted() {
        val first = "com.example.gameone"
        val second = "com.example.gametwo"
        AndroidGraphicsDriverPreferences.save(context, first, OpenGlDriverPreference.SYSTEM)
        AndroidGraphicsDriverPreferences.save(context, second, OpenGlDriverPreference.SYSTEM)
        assertTrue(AndroidGraphicsDriverPreferences.save(context, first, OpenGlDriverPreference.ANGLE))
        assertEquals(OpenGlDriverPreference.ANGLE, AndroidGraphicsDriverPreferences.selected(context, first))
        assertEquals(OpenGlDriverPreference.SYSTEM, AndroidGraphicsDriverPreferences.selected(context, second))
        AndroidGraphicsDriverPreferences.save(context, first, OpenGlDriverPreference.SYSTEM)
    }

    @Test fun invalidPackagesAreRejectedWithoutCreatingKeys() {
        assertFalse(AndroidGraphicsDriverPreferences.save(context, "../other", OpenGlDriverPreference.ANGLE))
        assertEquals(OpenGlDriverPreference.SYSTEM,
            AndroidGraphicsDriverPreferences.selected(context, "../other"))
    }

    @Test fun frameworkReadbackMustMatchSelectedGameAndMode() {
        val name = "com.example.verifiedgame"
        AndroidGraphicsDriverPreferences.save(context, name, OpenGlDriverPreference.NATIVE)
        val status = AndroidGraphicsDriverPreferences.inspect(context, name) { key ->
            when (key) {
                "angle_gl_driver_selection_pkgs" -> "com.example.verifiedgame"
                "angle_gl_driver_selection_values" -> "native"
                else -> null
            }
        }
        assertEquals(DriverPreferenceStatus.SYSTEM_SETTING_CONFIRMED, status.status)
        assertFalse(status.driverLoadedVerified)
        AndroidGraphicsDriverPreferences.save(context, name, OpenGlDriverPreference.SYSTEM)
    }

    @Test fun protectedReadFailureCannotBeReportedAsConfirmed() {
        val name = "com.example.deniedgame"
        AndroidGraphicsDriverPreferences.save(context, name, OpenGlDriverPreference.ANGLE)
        val status = AndroidGraphicsDriverPreferences.inspect(context, name) {
            throw SecurityException("no access")
        }
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE, status.status)
        AndroidGraphicsDriverPreferences.save(context, name, OpenGlDriverPreference.SYSTEM)
    }
    @Test fun frameworkReadbackWithoutOverrideReportsSystemFallback() {
        val name = "com.example.systemdriver"
        AndroidGraphicsDriverPreferences.save(context, name, OpenGlDriverPreference.SYSTEM)
        val state = AndroidGraphicsDriverPreferences.inspect(context, name) { null }
        assertEquals(DriverPreferenceStatus.SYSTEM_DEFAULT, state.status)
        assertTrue(state.matchesPreference)
        assertFalse(state.driverLoadedVerified)
    }

    @Test fun openingDeveloperSettingsUsesOnlyOfficialIntent() {
        var started: Intent? = null
        val wrapper = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                started = intent
            }
        }
        assertTrue(AndroidGraphicsDriverPreferences.openSystemDeveloperOptions(wrapper))
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, started?.action)
        assertTrue(started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun developerOptionsNotExportedFailsWithoutCrashing() {
        val unavailable = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw SecurityException("developer options denied")
            }
        }
        assertFalse(AndroidGraphicsDriverPreferences.openSystemDeveloperOptions(unavailable))
    }

    @Test fun invalidPackageReadbackIsNeverTrusted() {
        val state = AndroidGraphicsDriverPreferences.inspect(context, "../../other") {
            error("unexpected read")
        }
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE, state.status)
    }
}
