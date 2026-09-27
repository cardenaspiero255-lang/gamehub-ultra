package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ui.GameHubUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        assertNull(assertNotNull(selection).gamePackage)
    }

    @Test
    fun hydratedGameSelectionProducesGameUltraScope() {
        val selection = GameHubUiState(
            selectedGamePackage = "game.a",
            selectedGameHydrated = true
        ).ultraSessionScopeSelection()

        assertEquals("game.a", selection?.gamePackage)
    }

    @Test
    fun assistantInputStaysBlockedUntilHydratedScopeIsApplied() {
        val selection = GameHubUiState(
            selectedGamePackage = "game.a",
            selectedGameHydrated = true
        ).ultraSessionScopeSelection()

        assertFalse(
            isUltraAssistantInputReady(
                selection = selection,
                controllerGamePackage = null,
                controllerScopeReady = false
            )
        )
        assertFalse(
            isUltraAssistantInputReady(
                selection = selection,
                controllerGamePackage = "game.old",
                controllerScopeReady = true
            )
        )
        assertTrue(
            isUltraAssistantInputReady(
                selection = selection,
                controllerGamePackage = "game.a",
                controllerScopeReady = true
            )
        )
    }

    @Test
    fun hydratedGlobalScopeRequiresControllerReadiness() {
        val selection = GameHubUiState(
            selectedGamePackage = null,
            selectedGameHydrated = true
        ).ultraSessionScopeSelection()

        assertFalse(
            isUltraAssistantInputReady(
                selection = selection,
                controllerGamePackage = null,
                controllerScopeReady = false
            )
        )
        assertTrue(
            isUltraAssistantInputReady(
                selection = selection,
                controllerGamePackage = null,
                controllerScopeReady = true
            )
        )
    }


    @Test
    fun voiceTurnScopeCapturedBeforeDispatchRejectsGameSwitch() {
        val captured = captureUltraVoiceTurnScope(
            assistantInputReady = true,
            gamePackage = "game.a"
        )

        assertNotNull(captured)
        assertTrue(
            isUltraVoiceTurnScopeCurrent(
                captured = captured,
                assistantInputReady = true,
                currentGamePackage = "game.a"
            )
        )
        assertFalse(
            isUltraVoiceTurnScopeCurrent(
                captured = captured,
                assistantInputReady = true,
                currentGamePackage = "game.b"
            )
        )
    }

    @Test
    fun voiceTurnScopeCannotBeCapturedWhileAssistantInputIsBlocked() {
        assertNull(
            captureUltraVoiceTurnScope(
                assistantInputReady = false,
                gamePackage = "game.a"
            )
        )
    }

}
