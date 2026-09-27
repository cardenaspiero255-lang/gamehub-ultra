package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ui.GameHubUiState

internal data class UltraSessionScopeSelection(
    val gamePackage: String?
)

internal fun GameHubUiState.ultraSessionScopeSelection(): UltraSessionScopeSelection? =
    if (selectedGameHydrated) {
        UltraSessionScopeSelection(gamePackage = selectedGamePackage)
    } else {
        null
    }
