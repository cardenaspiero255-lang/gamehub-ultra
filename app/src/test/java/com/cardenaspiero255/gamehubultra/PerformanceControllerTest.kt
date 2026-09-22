package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceControllerTest {
    @Test
    fun balancedNeverEnablesSustainedMode() {
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                profile = PerformanceProfile.BALANCED,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
    }

    @Test
    fun interpolationNeverEnablesSustainedMode() {
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                profile = PerformanceProfile.FRAME_INTERPOLATION,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
    }

    @Test
    fun x4RequiresPlatformSupport() {
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                profile = PerformanceProfile.X4,
                platformSupportsSustainedMode = false,
                deviceSupportsSustainedMode = true
            )
        )
    }

    @Test
    fun x4RequiresDeviceSupport() {
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                profile = PerformanceProfile.X4,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = false
            )
        )
    }

    @Test
    fun x4EnablesSustainedModeOnlyWhenBothSupportIt() {
        assertTrue(
            PerformanceController.shouldEnableSustainedMode(
                profile = PerformanceProfile.X4,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
    }
}
