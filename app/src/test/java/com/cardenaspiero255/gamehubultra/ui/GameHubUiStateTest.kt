package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
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
        val state = GameHubUiState(globalProfile = PerformanceProfile.FRAME_INTERPOLATION)
        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, state.effectiveProfile)
        assertEquals(ThermalPreference.ADAPTIVE, state.effectiveThermalPreference)
        assertNull(state.effectiveRefreshRateTargetHz)
    }

    @Test
    fun accountIdentifiersValidateByPlatform() {
        assertEquals(true, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "76561198000000000"))
        assertEquals(true, GameAccountValidation.isSteamId64("76561198000000000"))
        assertEquals(true, GameAccountValidation.isSteamId64("76561197960265728"))
        assertEquals(true, GameAccountValidation.isSteamId64("76561202255233023"))
        assertEquals(true, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "player_one"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "1234567890"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "76561197960265727"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "76561202255233024"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "99999999999999999999"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.STEAM, "not valid"))
        assertEquals(true, GameAccountValidation.isValidPublicId(GamePlatform.EPIC_GAMES, "player.name"))
        assertEquals(false, GameAccountValidation.isValidPublicId(GamePlatform.EPIC_GAMES, "x"))
    }
}
