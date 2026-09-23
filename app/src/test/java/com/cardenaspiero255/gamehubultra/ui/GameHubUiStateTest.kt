package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.platform.GamePlatformLinks
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    @Test
    fun accountProvidersRemainExplicitAndAccountMetadataIsNonSecret() {
        val steam = ConnectedGameAccount(
            id = "1",
            platform = GamePlatform.STEAM,
            displayName = "Piero",
            publicId = "76561198000000000"
        )
        val epic = ConnectedGameAccount(
            id = "2",
            platform = GamePlatform.EPIC_GAMES,
            displayName = "Player",
            publicId = "public-name"
        )

        assertEquals(GamePlatform.STEAM, steam.platform)
        assertEquals(GamePlatform.EPIC_GAMES, epic.platform)
        assertEquals("Player", epic.displayName)
        assertTrue(GamePlatformLinks.isPublicProfileIdSupported(steam))
        assertTrue(
            GamePlatformLinks.isPublicProfileIdSupported(
                steam.copy(publicId = "piero_player")
            )
        )
        assertFalse(
            GamePlatformLinks.isPublicProfileIdSupported(
                steam.copy(publicId = "not a valid id")
            )
        )
        assertFalse(GamePlatformLinks.isPublicProfileIdSupported(epic))
    }

}
