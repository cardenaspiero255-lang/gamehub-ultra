package com.cardenaspiero255.gamehubultra.platform.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuCapabilityMatrixTest {
    @Test fun unknownGpuDoesNotReportAPIsOrDrivers() {
        val result = GpuCapabilityMatrix.from(GpuCapabilityObservation(null, null, null, null))
        assertNull(result.vendor)
        assertNull(result.renderer)
        assertEquals(CapabilityEvidence.UNKNOWN, result.vulkan)
        assertEquals(CapabilityEvidence.UNKNOWN, result.openGlEs)
        assertFalse(result.alternateDriverSelectable)
    }

    @Test fun explicitVulkanFeatureReportsVerifiedNotInferred() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("Qualcomm", "Adreno 650", "OpenGL ES 3.2 V@123", 0)
        )
        assertEquals(CapabilityEvidence.VERIFIED, result.vulkan)
        assertEquals(CapabilityEvidence.VERIFIED, result.openGlEs)
        assertEquals("3.2", result.openGlEsVersion)
        assertFalse(result.alternateDriverSelectable)
    }

    @Test fun adrenoNameAloneDoesNotGuaranteeVulkanOrTurnip() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("Qualcomm", "Adreno 740", "OpenGL ES 3.2", null)
        )
        assertEquals(CapabilityEvidence.UNKNOWN, result.vulkan)
        assertFalse(result.alternateDriverSelectable)
        assertTrue(result.explanations.any { "alternativo" in it.lowercase() })
    }

    @Test fun maliAndMediatekNamesDoNotBecomeDriverSwitchPermissions() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("ARM", "Mali-G77", "OpenGL ES 3.1", 1)
        )
        assertEquals("Mali-G77", result.renderer)
        assertFalse(result.alternateDriverSelectable)
    }

    @Test fun malformedOrContradictoryFeaturesFailClosed() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("GPU", "Renderer", "unknown-v2", -1)
        )
        assertNull(result.openGlEsVersion)
        assertEquals(CapabilityEvidence.UNKNOWN, result.openGlEs)
        assertEquals(CapabilityEvidence.UNKNOWN, result.vulkan)
    }

    @Test fun explicitAbsenceOfVulkanIsDifferentFromUnknown() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("GPU", "Renderer", "OpenGL ES 3.0", null, vulkanFeatureAvailable = false)
        )
        assertEquals(CapabilityEvidence.UNAVAILABLE, result.vulkan)
    }

    @Test fun unverifiedAlternateDriverFlagIsRejected() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("Qualcomm", "Adreno 740", "OpenGL ES 3.2", 1,
                verifiedDriverSelectionAvailable = false)
        )
        assertFalse(result.alternateDriverSelectable)
    }

    @Test fun alternateDriverSelectionRequiresIndependentVerifiedCapability() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("Qualcomm", "Adreno 740", "OpenGL ES 3.2", 1,
                verifiedDriverSelectionAvailable = true)
        )
        assertTrue(result.alternateDriverSelectable)
        assertFalse(result.driverWasChanged)
    }

    @Test fun normalizeWhitespaceWithoutLeakingDeviceUniqueIdentifiers() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("  Qualcomm  ", "  Adreno   650  ", "OpenGL ES 3.2", null)
        )
        assertEquals("Qualcomm", result.vendor)
        assertEquals("Adreno 650", result.renderer)
        assertFalse(result.fingerprint.contains("android_id", true))
    }

    @Test fun badlyFormattedInputCannotCreateUnsupportedClaims() {
        val result = GpuCapabilityMatrix.from(
            GpuCapabilityObservation("", " ", "OpenGL ES beta", 999, vulkanFeatureAvailable = false)
        )
        assertEquals(CapabilityEvidence.UNAVAILABLE, result.vulkan)
        assertNull(result.openGlEsVersion)
        assertFalse(result.alternateDriverSelectable)
    }

    @Test fun documentedCompatibilityReportPreservesUnknownState() {
        val report = GpuCapabilityMatrix.from(GpuCapabilityObservation("GPU", null, null, null))
        assertTrue(report.explanations.any { "no" in it.lowercase() })
        assertFalse(report.driverWasChanged)
    }
}
