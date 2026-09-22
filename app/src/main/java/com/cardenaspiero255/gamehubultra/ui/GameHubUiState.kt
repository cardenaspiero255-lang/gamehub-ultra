package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

data class GameHubUiState(
    val globalProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val selectedGamePackage: String? = null,
    val selectedGameProfile: PerformanceProfile? = null
) {
    val effectiveProfile: PerformanceProfile
        get() = selectedGameProfile ?: globalProfile
}
