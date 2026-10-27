package com.cardenaspiero255.gamehubultra.platform.compat

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceCompatibilityDatabaseTest {
    private val device = CompatibilityDeviceKey(
        socFamily = VendorSocFamily.MEDIATEK,
        gpuFamily = CompatibilityGpuFamily.MALI,
        androidApiLevel = 34
    )

    private fun record(
        packageName: String = "com.example.game",
        key: CompatibilityDeviceKey = device,
        schema: Int = DeviceCompatibilityDatabase.SCHEMA_VERSION,
        ruleId: String = "medIATEK-rule-01",
        evidenceId: String = "verified-sha-001",
        profile: PerformanceProfile = PerformanceProfile.BALANCED
    ) = DeviceCompatibilityRule(
        schemaVersion = schema,
        ruleId = ruleId,
        evidenceId = evidenceId,
        gamePackage = packageName,
        key = key,
        recommendedProfile = profile
    )

    @Test fun noRulesFailClosedWithoutPretendingAValidatedDefault() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game", emptyList(), emptySet()
        )
        assertEquals(CompatibilityStatus.NO_RECORD, decision.status)
        assertEquals(PerformanceProfile.BALANCED, decision.recommendedProfile)
        assertNull(decision.ruleId)
        assertFalse(decision.profileApplied)
    }

    @Test fun independentlyVerifiedEvidenceUnlocksExactKnownGoodRule() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game",
            listOf(record(profile = PerformanceProfile.X4)),
            setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.KNOWN_GOOD, decision.status)
        assertEquals(PerformanceProfile.X4, decision.recommendedProfile)
        assertEquals("medIATEK-rule-01", decision.ruleId)
        assertFalse(decision.profileApplied)
    }

    @Test fun metadataWithoutIndependentlyVerifiedEvidenceIsNeverKnownGood() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game",
            listOf(record(profile = PerformanceProfile.X4)),
            emptySet()
        )
        assertEquals(CompatibilityStatus.UNVERIFIED, decision.status)
        assertEquals(PerformanceProfile.BALANCED, decision.recommendedProfile)
        assertNull(decision.ruleId)
    }

    @Test fun oldOrFutureSchemaIsRejectedRatherThanSilentlyMigrated() {
        for (schema in listOf(0, 2)) {
            val decision = DeviceCompatibilityDatabase.resolve(
                device, "com.example.game",
                listOf(record(schema = schema)),
                setOf("verified-sha-001")
            )
            assertEquals(CompatibilityStatus.UNVERIFIED, decision.status)
        }
    }

    @Test fun sameGameOnDifferentGpuOrApiNeverInheritsProfile() {
        val otherGpu = device.copy(gpuFamily = CompatibilityGpuFamily.ADRENO)
        val otherApi = device.copy(androidApiLevel = 35)
        for (other in listOf(otherGpu, otherApi)) {
            val decision = DeviceCompatibilityDatabase.resolve(
                other, "com.example.game", listOf(record()), setOf("verified-sha-001")
            )
            assertEquals(CompatibilityStatus.NO_RECORD, decision.status)
        }
    }

    @Test fun profilesAreScopedToPackageWithNoWildcardMatching() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.other", listOf(record()), setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.NO_RECORD, decision.status)
    }

    @Test fun contradictoryMatchingRulesFailClosedEvenWhenBothClaimVerification() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game",
            listOf(record(), record(ruleId = "medIATEK-rule-02", profile = PerformanceProfile.X4)),
            setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.AMBIGUOUS, decision.status)
        assertEquals(PerformanceProfile.BALANCED, decision.recommendedProfile)
    }

    @Test fun invalidPackageAndUnavailableDeviceMetadataAreNotTrusted() {
        val badPackage = DeviceCompatibilityDatabase.resolve(
            device, "../wrong", listOf(record()), setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.INVALID_INPUT, badPackage.status)
        val unknown = DeviceCompatibilityDatabase.resolve(
            device.copy(socFamily = VendorSocFamily.GENERIC), "com.example.game",
            listOf(record()), setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.INVALID_INPUT, unknown.status)
        val unknownGpu = DeviceCompatibilityDatabase.resolve(
            device.copy(gpuFamily = CompatibilityGpuFamily.UNKNOWN),
            "com.example.game", listOf(record()), setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.INVALID_INPUT, unknownGpu.status)
    }

    @Test fun invalidOrUnverifiedEvidenceIdFailsClosed() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game",
            listOf(record(evidenceId = "../../bad")),
            setOf("../../bad")
        )
        assertEquals(CompatibilityStatus.UNVERIFIED, decision.status)
    }

    @Test fun sanitizedDiagnosticExportContainsNoHardwareIdentifiersOrGameByDefault() {
        val decision = DeviceCompatibilityDatabase.resolve(
            device, "com.example.game", listOf(record()), setOf("verified-sha-001")
        )
        val report = DeviceCompatibilityDatabase.exportDiagnostic(
            device, "com.example.game", decision
        )
        assertTrue(report.contains("MEDIATEK"))
        assertTrue(report.contains("MALI"))
        assertTrue(report.contains("KNOWN_GOOD"))
        assertTrue(report.contains("redacted"))
        assertFalse(report.contains("com.example.game"))
        assertFalse(report.contains("serial"))
        assertFalse(report.contains("imei"))
        assertTrue(
            DeviceCompatibilityDatabase.exportDiagnostic(
                device, "com.example.game", decision, includePackage = true
            ).contains("com.example.game")
        )
    }

    @Test fun invalidApiLevelDoesNotCrashOrCreateKnownGoodDecision() {
        val result = DeviceCompatibilityDatabase.resolve(
            device.copy(androidApiLevel = -1),
            "com.example.game", listOf(record()), setOf("verified-sha-001")
        )
        assertEquals(CompatibilityStatus.INVALID_INPUT, result.status)
    }
}
