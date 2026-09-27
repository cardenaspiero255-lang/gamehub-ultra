package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ui.GameHubUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraSessionScopeSelectionTest {

    @Test
    fun unloadedSelectionDoesNotHydrateUltraSession() {
        val selection = GameHubUiState(
            selectedGamePackage = null,
            selectedGameHydrated = false
        ).ultraSessionScopeSelection()

        assertNull(selection)
    }

    @Test
    fun hydratedGlobalSelectionProducesGlobalUltraScope() {
        val selection = GameHubUiState(
            selectedGamePackage = null,
            selectedGameHydrated = true
        ).ultraSessionScopeSelection()

        assertEquals(null, selection?.gamePackage)
    }

    @Test
    fun hydratedGameSelectionProducesGameUltraScope() {
        val selection = GameHubUiState(
            selectedGamePackage = "game.a",
            selectedGameHydrated = true
        ).ultraSessionScopeSelection()

        assertEquals("game.a", selection?.gamePackage)
    }
}
