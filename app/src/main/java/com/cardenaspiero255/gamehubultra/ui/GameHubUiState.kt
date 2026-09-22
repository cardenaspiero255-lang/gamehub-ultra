package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

data class GameHubUiState(
    val globalProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val selectedGamePackage: String? = null,
    val selectedGameConfig: GameProfileConfig? = null,
    val favoriteGames: Set<String> = emptySet(),
    val recentGamePackages: List<String> = emptyList(),
    val manualGamePackages: Set<String> = emptySet()
) {
    val effectiveProfile: PerformanceProfile
        get() = selectedGameConfig?.performanceProfile ?: globalProfile

    val effectiveThermalPreference =
        selectedGameConfig?.thermalPreference
            ?: com.cardenaspiero255.gamehubultra.domain.ThermalPreference.ADAPTIVE

    val effectiveRefreshRateTargetHz: Int?
        get() = selectedGameConfig?.refreshRateTargetHz
}
