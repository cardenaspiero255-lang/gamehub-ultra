package com.cardenaspiero255.gamehubultra

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryTest {
    private val games = listOf(
        GameInfo("com.supercell.brawlstars", "Brawl Stars"),
        GameInfo("com.epicgames.fortnite", "Fortnite"),
        GameInfo("com.valvesoftware.steam", "Steam Link")
    )

    @Test
    fun filterGames_matchesVisibleNameCaseInsensitively() {
        val result = GameLibrary.filterGames(games, "BRAWL")
        assertEquals(listOf(games[0]), result)
    }

    @Test
    fun filterGames_matchesPackageName() {
        val result = GameLibrary.filterGames(games, "epicgames")
        assertEquals(listOf(games[1]), result)
    }

    @Test
    fun filterGames_blankQuery_returnsOriginalOrderAndContents() {
        val result = GameLibrary.filterGames(games, "  ")
        assertEquals(games, result)
    }

    @Test
    fun filterGames_noMatch_returnsEmptyList() {
        val result = GameLibrary.filterGames(games, "does-not-exist")
        assertTrue(result.isEmpty())
    }

    @Test
    fun isGameApplication_acceptsDeclaredGameCategory() {
        assertTrue(
            GameLibrary.isGameApplication(ApplicationInfo.CATEGORY_GAME)
        )
    }

    @Test
    fun isGameApplication_rejectsUndefinedCategory() {
        assertTrue(
            GameLibrary.isGameApplication(ApplicationInfo.CATEGORY_UNDEFINED)
        )
    }

    @Test
    fun isGameApplication_reliesOnCategoryOnly() {
        assertEquals(
            false,
            GameLibrary.isGameApplication(ApplicationInfo.CATEGORY_UNDEFINED)
        )
    }
}
