package com.cardenaspiero255.gamehubultra.platform.vendor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidVendorHardwareProbeTest {
    @Test fun publicSocReadoutCanIdentifyMediatekWithoutVendorPrivileges() {
        val observed = AndroidVendorHardwareProbe.observe(sdkInt = 35) {
            "MediaTek" to "Dimensity 1300"
        }
        val decision = VendorAdapterFramework.evaluate(observed, emptyList())
        assertEquals(VendorSocFamily.MEDIATEK, decision.family)
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, decision.route)
        assertFalse(decision.canSwitchExternalGameDrivers)
    }

    @Test fun oldAndroidDoesNotQueryUnsupportedSocApi() {
        var queried = false
        val observed = AndroidVendorHardwareProbe.observe(sdkInt = 29) {
            queried = true
            "Qualcomm" to "SM8650"
        }
        assertFalse(queried)
        assertEquals(VendorSocFamily.GENERIC,
            VendorAdapterFramework.detectFamily(observed))
    }

    @Test fun inaccessibleSocPropertiesFailClosedAndDoNotCrash() {
        val observed = AndroidVendorHardwareProbe.observe(sdkInt = 35) {
            throw SecurityException("No SoC properties")
        }
        val result = VendorAdapterFramework.evaluate(observed, emptyList())
        assertEquals(VendorSocFamily.GENERIC, result.family)
        assertEquals(VendorAdapterReason.SOC_UNVERIFIED, result.reason)
    }

    @Test fun productionProbeNeedsNoProprietaryVendorSdk() {
        val report = AndroidVendorHardwareProbe.evaluate()
        assertEquals(VendorAdapterRoute.GENERIC_ANDROID, report.route)
        assertFalse(report.canControlCpuFrequency)
    }
}
