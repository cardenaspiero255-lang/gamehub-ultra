package com.cardenaspiero255.gamehubultra.platform.vendor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VendorAdapterFrameworkTest {
    private fun observed(
        socMaker: String? = null,
        socModel: String? = null,
        deviceMaker: String? = null,
        renderer: String? = null
    ) = VendorHardwareObservation(socMaker, socModel, deviceMaker, renderer)

    private fun adapter(
        requestedFamily: VendorSocFamily,
        id: String = "verified-vendor-adapter",
        snapshot: VendorReadOnlySnapshot? = VendorReadOnlySnapshot(
            publicAndroidEvidence = true,
            capabilities = setOf(VendorReadOnlyCapability.THERMAL_STATUS)
        )
    ): VendorOptionalAdapter = object : VendorOptionalAdapter {
        override val family = requestedFamily
        override val adapterId = id
        override fun readOnlyCapabilities() = snapshot
    }

    @Test fun mediatekSocIsIdentifiedEvenWhenGpuOnlySaysArmMali() {
        val decision = VendorAdapterFramework.evaluate(
            observed("MediaTek", "Dimensity 1300", "vivo", "Mali-G77"), emptyList()
        )
        assertEquals(VendorSocFamily.MEDIATEK, decision.family)
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, decision.route)
        assertNull(decision.adapterId)
        assertFalse(decision.canSwitchExternalGameDrivers)
    }

    @Test fun qualcommSoCDoesNotAutomaticallyGrantTurnipOrBoostAccess() {
        val decision = VendorAdapterFramework.evaluate(
            observed("Qualcomm", "SM8650", "Samsung", "Adreno 750"), emptyList()
        )
        assertEquals(VendorSocFamily.QUALCOMM, decision.family)
        assertEquals(VendorAdapterReason.NO_VERIFIED_ADAPTER, decision.reason)
        assertFalse(decision.canControlCpuFrequency)
        assertFalse(decision.canSwitchExternalGameDrivers)
    }

    @Test fun gpuVendorOrPhoneBrandCannotProveChipsetFamily() {
        val decision = VendorAdapterFramework.evaluate(
            observed(deviceMaker = "Samsung", renderer = "Adreno 740"),
            listOf(adapter(VendorSocFamily.QUALCOMM))
        )
        assertEquals(VendorSocFamily.GENERIC, decision.family)
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, decision.route)
    }

    @Test fun samsungChipsetsRequireExplicitExynosModelEvidence() {
        assertEquals(VendorSocFamily.EXYNOS,
            VendorAdapterFramework.detectFamily(observed("Samsung", "Exynos 2400")))
        assertEquals(VendorSocFamily.GENERIC,
            VendorAdapterFramework.detectFamily(observed("Samsung", "Snapdragon 8 Gen 3")))
        assertEquals(VendorSocFamily.GENERIC,
            VendorAdapterFramework.detectFamily(observed("Samsung", null)))
    }

    @Test fun verifiedOptionalReadoutNeverEnablesPrivilegedMutations() {
        val selected = VendorAdapterFramework.evaluate(
            observed("qti", "SM8650"),
            listOf(adapter(VendorSocFamily.QUALCOMM))
        )
        assertEquals(VendorSocFamily.QUALCOMM, selected.family)
        assertEquals(VendorAdapterRoute.VERIFIED_READ_ONLY, selected.route)
        assertEquals(VendorAdapterReason.READ_ONLY_VERIFIED, selected.reason)
        assertEquals("verified-vendor-adapter", selected.adapterId)
        assertTrue(VendorReadOnlyCapability.THERMAL_STATUS in selected.capabilities)
        assertFalse(selected.canControlCpuFrequency)
        assertFalse(selected.canSwitchExternalGameDrivers)
    }

    @Test fun wrongFamilyAdapterIsNeverUsed() {
        val result = VendorAdapterFramework.evaluate(
            observed("MediaTek", "Dimensity 1300"),
            listOf(adapter(VendorSocFamily.QUALCOMM))
        )
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, result.route)
        assertEquals(VendorAdapterReason.NO_VERIFIED_ADAPTER, result.reason)
    }

    @Test fun ambiguousAdaptersFailClosedRatherThanPickingArbitrarily() {
        val result = VendorAdapterFramework.evaluate(
            observed("MediaTek", "Dimensity 1300"),
            listOf(adapter(VendorSocFamily.MEDIATEK), adapter(VendorSocFamily.MEDIATEK))
        )
        assertEquals(VendorAdapterReason.AMBIGUOUS_ADAPTER, result.reason)
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, result.route)
    }

    @Test fun maliciousOrInvalidAdapterIdsFailClosed() {
        val result = VendorAdapterFramework.evaluate(
            observed("Qualcomm", "SM8650"),
            listOf(adapter(VendorSocFamily.QUALCOMM, "../../system"))
        )
        assertEquals(VendorAdapterReason.INVALID_ADAPTER, result.reason)
        assertNull(result.adapterId)
    }

    @Test fun failedOrAbsentVendorProbeUsesGenericAndroid() {
        val family = VendorSocFamily.EXYNOS
        val missing = VendorAdapterFramework.evaluate(
            observed("Samsung", "Exynos 2400"), listOf(adapter(family, snapshot = null))
        )
        assertEquals(VendorAdapterReason.NO_VERIFIED_READOUT, missing.reason)
        val throwing = object : VendorOptionalAdapter {
            override val family = VendorSocFamily.EXYNOS
            override val adapterId = "unstable-adapter"
            override fun readOnlyCapabilities(): VendorReadOnlySnapshot =
                throw SecurityException("permission denied")
        }
        val result = VendorAdapterFramework.evaluate(
            observed("Samsung", "Exynos 2400"), listOf(throwing)
        )
        assertEquals(VendorAdapterReason.NO_VERIFIED_READOUT, result.reason)
        assertTrue(result.capabilities.isEmpty())
    }

    @Test fun untrustedEvidenceOrEmptyCapabilitySetCannotClaimSupport() {
        for (snapshot in listOf(
            VendorReadOnlySnapshot(publicAndroidEvidence = false,
                capabilities = setOf(VendorReadOnlyCapability.GPU_RENDERER)),
            VendorReadOnlySnapshot(publicAndroidEvidence = true, capabilities = emptySet())
        )) {
            val result = VendorAdapterFramework.evaluate(
                observed("MediaTek", "Dimensity 1300"),
                listOf(adapter(VendorSocFamily.MEDIATEK, snapshot = snapshot))
            )
            assertEquals(VendorAdapterRoute.GENERIC_ANDROID, result.route)
            assertEquals(VendorAdapterReason.NO_VERIFIED_READOUT, result.reason)
        }
    }

    @Test fun genericModeNeverInvokesAnUnrelatedAdapter() {
        var inspected = false
        val unexpected = object : VendorOptionalAdapter {
            override val family = VendorSocFamily.MEDIATEK
            override val adapterId = "vendor-adapter-01"
            override fun readOnlyCapabilities(): VendorReadOnlySnapshot {
                inspected = true
                error("must not call")
            }
        }
        val result = VendorAdapterFramework.evaluate(observed(), listOf(unexpected))
        assertEquals(VendorAdapterReason.SOC_UNVERIFIED, result.reason)
        assertFalse(inspected)
    }
}
