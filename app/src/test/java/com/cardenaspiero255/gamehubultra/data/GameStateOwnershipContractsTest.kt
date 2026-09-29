package com.cardenaspiero255.gamehubultra.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameStateOwnershipContractsTest {
    @Test
    fun preferencesRepositoryOwnsSelectionAndLibraryStateContracts() {
        val contracts = GameHubPreferencesRepository::class.java.interfaces.toSet()

        assertTrue(GameSelectionStateRepository::class.java in contracts)
        assertTrue(GameLibraryStateRepository::class.java in contracts)
    }

    @Test
    fun selectionContractInventoriesOnlySelectionAndPerGameProfileState() {
        assertEquals(
            setOf(
                "gameProfileConfigFlow",
                "profileForGameFlow",
                "saveGameProfileConfig",
                "saveProfileForGame",
                "saveSelectedGame",
                "saveSelectedGameAndProfile",
                "saveSelectedProfile",
                "selectedGameFlow",
                "selectedProfileFlow",
            ),
            GameSelectionStateRepository::class.java.methods.map { it.name }.toSet(),
        )
    }

    @Test
    fun libraryContractInventoriesOnlyPersistentLibraryCollections() {
        assertEquals(
            setOf(
                "favoriteGamesFlow",
                "manualGamesFlow",
                "recentGamesFlow",
                "recordRecentGame",
                "setFavoriteGame",
                "setManualGame",
            ),
            GameLibraryStateRepository::class.java.methods.map { it.name }.toSet(),
        )
    }

    @Test
    fun aliasContractIsAndroidFreeAndExplicit() {
        assertEquals(
            setOf("aliases", "save"),
            GameAliasStateRepository::class.java.methods.map { it.name }.toSet(),
        )
        val types = GameAliasStateRepository::class.java.methods.flatMap { method ->
            method.parameterTypes.toList() + method.returnType
        }
        assertFalse(types.any { it.name == "android.content.Context" })
    }
}
