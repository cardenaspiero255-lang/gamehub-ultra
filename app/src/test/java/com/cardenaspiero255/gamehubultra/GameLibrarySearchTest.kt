package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLibrarySearchTest {

    private val games = listOf(
        GameInfo("com.example.alpha", "Alpha Quest"),
        GameInfo("com.example.beta", "Beta Racer"),
        GameInfo("com.example.gamma", "Gamma Arena")
    )

    @Test
    fun blankQueryKeepsAllGames() {
        assertEquals(games, GameLibrary.filterGames(games, " "))
    }

    @Test
    fun queryMatchesVisibleName() {
        assertEquals(
            listOf(games[1]),
            GameLibrary.filterGames(games, "racer")
        )
    }

    @Test
    fun queryMatchesPackageName() {
        assertEquals(
            listOf(games[2]),
            GameLibrary.filterGames(games, "example.gamma")
        )
    }

    @Test
    fun queryDoesNotReturnExcludedSelectedGame() {
        val visible = GameLibrary.filterGames(games, "alpha")
        assertTrue(visible.none { it.packageName == games[1].packageName })
    }
}
