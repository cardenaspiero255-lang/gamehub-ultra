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


internal fun isUltraAssistantInputReady(
    selection: UltraSessionScopeSelection?,
    controllerGamePackage: String?,
    controllerScopeReady: Boolean
): Boolean =
    selection != null &&
        controllerScopeReady &&
        selection.gamePackage == controllerGamePackage
