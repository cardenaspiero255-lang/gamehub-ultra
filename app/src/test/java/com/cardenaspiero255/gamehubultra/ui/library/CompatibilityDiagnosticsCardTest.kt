package com.cardenaspiero255.gamehubultra.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDecision
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDeviceKey
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDeviceSnapshot
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityGpuFamily
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityStatus
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompatibilityDiagnosticsCardTest {
    @get:Rule val rule = createComposeRule()

    private val snapshot = CompatibilityDeviceSnapshot(
        key = CompatibilityDeviceKey(VendorSocFamily.MEDIATEK, CompatibilityGpuFamily.MALI, 35),
        openGlEs = CapabilityEvidence.VERIFIED,
        vulkan = CapabilityEvidence.VERIFIED,
        deviceClassObserved = true
    )

    @Test fun observedDeviceSignalsAreVisibleButDoNotClaimGameDriverSwitch() {
        rule.setContent {
            CompatibilityDiagnosticsContent(snapshot, CompatibilityDecision(CompatibilityStatus.NO_RECORD))
        }
        rule.onNodeWithText("Compatibilidad del dispositivo (CAR-56)").assertIsDisplayed()
        rule.onNodeWithText("MediaTek · Mali · Android API 35").assertIsDisplayed()
        rule.onNodeWithText("OpenGL ES: verificado · Vulkan: verificado").assertIsDisplayed()
        rule.onNodeWithText("Sin perfil conocido verificado para este juego.").assertIsDisplayed()
        rule.onNodeWithText("El diagnóstico no cambia drivers ni activa perfiles.").assertIsDisplayed()
    }

    @Test fun unverifiedRuleMustBeCalledUnverifiedInTheUserInterface() {
        rule.setContent {
            CompatibilityDiagnosticsContent(snapshot, CompatibilityDecision(CompatibilityStatus.UNVERIFIED))
        }
        rule.onNodeWithText("Hay una regla local sin evidencia autenticada; no se aplica.").assertIsDisplayed()
    }

    @Test fun unknownHardwareShowsInsufficientEvidenceRatherThanManufacturerGuess() {
        rule.setContent {
            CompatibilityDiagnosticsContent(
                snapshot.copy(
                    key = snapshot.key.copy(
                        socFamily = VendorSocFamily.GENERIC,
                        gpuFamily = CompatibilityGpuFamily.UNKNOWN
                    ),
                    deviceClassObserved = false,
                    vulkan = CapabilityEvidence.UNKNOWN
                ),
                CompatibilityDecision(CompatibilityStatus.INVALID_INPUT)
            )
        }
        rule.onNodeWithText("Datos insuficientes para clasificar el dispositivo.").assertIsDisplayed()
        rule.onNodeWithText("OpenGL ES: verificado · Vulkan: no verificado").assertIsDisplayed()
    }
}
