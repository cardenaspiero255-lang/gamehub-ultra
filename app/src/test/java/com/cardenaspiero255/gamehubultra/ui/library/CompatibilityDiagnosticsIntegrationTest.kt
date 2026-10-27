package com.cardenaspiero255.gamehubultra.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.compat.AndroidDeviceCompatibilityStore
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDeviceKey
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDeviceSnapshot
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityGpuFamily
import com.cardenaspiero255.gamehubultra.platform.compat.DeviceCompatibilityDatabase
import com.cardenaspiero255.gamehubultra.platform.compat.DeviceCompatibilityRule
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompatibilityDiagnosticsIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val game = "com.example.car56diagnostics"
    private val observed = CompatibilityDeviceSnapshot(
        CompatibilityDeviceKey(VendorSocFamily.MEDIATEK, CompatibilityGpuFamily.MALI, 35),
        CapabilityEvidence.VERIFIED,
        CapabilityEvidence.VERIFIED,
        deviceClassObserved = true
    )

    @After fun cleanStore() {
        AndroidDeviceCompatibilityStore.clear(context)
    }

    private fun awaitTitle(text: String) {
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun libraryDiagnosisLoadsObservedDeviceAndAllowsOnlySanitizedClipboardExport() {
        rule.setContent {
            CompatibilityDiagnosticsCard(game, readSnapshot = { observed })
        }
        awaitTitle("MediaTek · Mali · Android API 35")
        rule.onNodeWithText("Sin perfil conocido verificado para este juego.").assertIsDisplayed()
        rule.onNodeWithText("Copiar diagnóstico sin identificadores").performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val exported = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        assertTrue(exported.contains("MEDIATEK"))
        assertTrue(exported.contains("MALI"))
        assertTrue(exported.contains("gamePackage=redacted"))
        assertFalse(exported.contains(game))
        assertFalse(exported.contains("imei"))
    }

    @Test fun persistedGameRuleIsInspectedButNotAutoVerifiedOrApplied() {
        val record = DeviceCompatibilityRule(
            DeviceCompatibilityDatabase.SCHEMA_VERSION, "rule-evidence-001",
            "verified-source-001", game, observed.key, PerformanceProfile.X4
        )
        assertTrue(AndroidDeviceCompatibilityStore.save(context, listOf(record)))
        rule.setContent {
            CompatibilityDiagnosticsCard(game, readSnapshot = { observed })
        }
        awaitTitle("Hay una regla local sin evidencia autenticada; no se aplica.")
        rule.onNodeWithText("El diagnóstico no cambia drivers ni activa perfiles.").assertIsDisplayed()
    }

    @Test fun deniedClipboardAccessDoesNotCrashAndReportsFailure() {
        val blocked = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.CLIPBOARD_SERVICE) throw SecurityException("blocked")
                else super.getSystemService(name)
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides blocked) {
                CompatibilityDiagnosticsCard(game, readSnapshot = { observed })
            }
        }
        awaitTitle("MediaTek · Mali · Android API 35")
        rule.onNodeWithText("Copiar diagnóstico sin identificadores").performClick()
        rule.onNodeWithText("No se pudo copiar el diagnóstico en este dispositivo.").assertIsDisplayed()
    }
}
