package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameHubUiStateTest {
    @Test
    fun gameOverrideWinsOverGlobalProfile() {
        val state = GameHubUiState(
            globalProfile = PerformanceProfile.BALANCED,
            selectedGameConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                thermalPreference = ThermalPreference.COOLER,
                refreshRateTargetHz = 144
            )
        )

        assertEquals(PerformanceProfile.X4, state.effectiveProfile)
        assertEquals(ThermalPreference.COOLER, state.effectiveThermalPreference)
        assertEquals(144, state.effectiveRefreshRateTargetHz)
    }

    @Test
    fun missingGameOverrideUsesGlobalAndSafeDefaults() {
        val state = GameHubUiState(
            globalProfile = PerformanceProfile.FRAME_INTERPOLATION
        )

        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, state.effectiveProfile)
        assertEquals(ThermalPreference.ADAPTIVE, state.effectiveThermalPreference)
        assertNull(state.effectiveRefreshRateTargetHz)
    }
}
