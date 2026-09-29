package com.cardenaspiero255.gamehubultra.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreLibraryComponentsTest {
    @Test
    fun emptyStoreLibraryExplainsHowToConnectAccounts() {
        assertEquals(
            "Conecta Steam o Epic para sincronizar tus juegos dentro de Ultra.",
            storeLibrarySummaryText(0)
        )
    }

    @Test
    fun populatedStoreLibraryReportsConnectedGameCount() {
        assertEquals(
            "Steam + Epic sincronizados · 7 juego(s)",
            storeLibrarySummaryText(7)
        )
    }

    @Test
    fun invalidNegativeCountDoesNotClaimConnectedGames() {
        assertEquals(
            "Conecta Steam o Epic para sincronizar tus juegos dentro de Ultra.",
            storeLibrarySummaryText(-1)
        )
    }
}
