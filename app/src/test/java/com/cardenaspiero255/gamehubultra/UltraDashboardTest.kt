package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraDashboardTest {
    @Test
    fun selectedGameLeadsRecentGameStripWithoutDuplicates() {
        assertEquals(
            listOf("War Robots", "Asphalt 9", "C.A.T.S."),
            dashboardRecentGames(
                selectedGame = "War Robots",
                recentGames = listOf("Asphalt 9", "War Robots", "C.A.T.S.", "Call of Duty: Mobile")
            )
        )
    }

    @Test
    fun placeholderSelectionIsNotShownAsARecentGame() {
        assertEquals(
            listOf("Asphalt 9", "C.A.T.S."),
            dashboardRecentGames(
                selectedGame = "Selecciona un juego",
                recentGames = listOf("Asphalt 9", "C.A.T.S.")
            )
        )
    }
}
