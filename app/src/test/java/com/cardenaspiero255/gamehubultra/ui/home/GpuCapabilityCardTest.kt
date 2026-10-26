package com.cardenaspiero255.gamehubultra.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.gpu.GpuCapabilityReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GpuCapabilityCardTest {
    @get:Rule val rule = createComposeRule()

    private fun report(
        renderer: String?,
        version: String?,
        vulkan: CapabilityEvidence,
        selectable: Boolean,
        notes: List<String> = emptyList()
    ) = GpuCapabilityReport(
        "GPU vendor", renderer, version,
        if (version == null) CapabilityEvidence.UNKNOWN else CapabilityEvidence.VERIFIED,
        vulkan, selectable, "non-unique-device-capabilities", notes
    )

    @Test fun verifiedApiVersionsDoNotClaimAnInstalledAlternativeDriver() {
        rule.setContent {
            GpuCapabilityCard(report("Adreno 650", "3.2", CapabilityEvidence.VERIFIED, false))
        }
        rule.onNodeWithTag("car53_gpu_card").assertExists()
        rule.onNodeWithText("GPU: Adreno 650").assertExists()
        rule.onNodeWithText("OpenGL ES: 3.2").assertExists()
        rule.onNodeWithText("Vulkan: verificado").assertExists()
        rule.onNodeWithText("Driver alternativo: sin control verificado").assertExists()
    }

    @Test fun unknownCapabilitiesAreExplicitNotFabricated() {
        rule.setContent {
            GpuCapabilityCard(report(null, null, CapabilityEvidence.UNKNOWN, false,
                listOf("El driver del sistema permanece sin cambios.")))
        }
        rule.onNodeWithText("GPU: sin identificar").assertExists()
        rule.onNodeWithText("OpenGL ES: no verificado").assertExists()
        rule.onNodeWithText("Vulkan: sin verificar").assertExists()
        rule.onNodeWithText("El driver del sistema permanece sin cambios.").assertExists()
    }

    @Test fun unavailableVulkanAndExplicitDriverCapabilityRenderSeparately() {
        rule.setContent {
            GpuCapabilityCard(report("Mali", "3.1", CapabilityEvidence.UNAVAILABLE, true))
        }
        rule.onNodeWithText("Vulkan: no disponible").assertExists()
        rule.onNodeWithText("Driver alternativo: selección verificada").assertExists()
    }
}
