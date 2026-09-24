package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameHubOriginalUiContractTest {
    @Test
    fun car31KeepsOriginalGameHubNavigationShell() {
        assertEquals(listOf("Inicio", "Biblioteca", "Perfil"), GameHubOriginalUiContract.topNavigation)
        assertTrue(GameHubOriginalUiContract.keepsOriginalShell())
    }

    @Test
    fun car31AppliesCanvaRedNeonBlackIdentity() {
        assertTrue(GameHubOriginalUiContract.appliesCanvaNeonIdentity())
        assertTrue(GameHubOriginalUiContract.canvaIdentity.any { it.contains("Rojo neón") })
        assertTrue(GameHubOriginalUiContract.canvaIdentity.any { it.contains("Negro") })
    }
}
