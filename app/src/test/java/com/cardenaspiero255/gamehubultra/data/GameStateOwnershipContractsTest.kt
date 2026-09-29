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
            listOf(
                "gameProfileConfigFlow",
                "profileForGameFlow",
                "saveGameProfileConfig",
                "saveProfileForGame",
                "saveSelectedGame",
                "saveSelectedGameAndProfile",
                "saveSelectedProfile",
                "selectedGameFlow",
                "selectedProfileFlow",
            ).sorted(),
            GameSelectionStateRepository::class.java.methods.map { it.name }.sorted(),
        )
    }

    @Test
    fun libraryContractInventoriesOnlyPersistentLibraryCollections() {
        assertEquals(
            listOf(
                "favoriteGamesFlow",
                "manualGamesFlow",
                "recentGamesFlow",
                "recordRecentGame",
                "setFavoriteGame",
                "setManualGame",
            ).sorted(),
            GameLibraryStateRepository::class.java.methods.map { it.name }.sorted(),
        )
    }



    @Test
    fun connectedAccountsContractIsAndroidFreeAndExplicit() {
        val contractMethods = ConnectedGameAccountsStateRepository::class.java.methods
            .map { it.name }
            .toSet()
        assertTrue(
            contractMethods.containsAll(
                setOf(
                    "accountsFlow",
                    "activeAccountIdFlow",
                    "remove",
                    "setActiveAccount",
                    "updatePublicMetadata",
                    "upsert",
                )
            )
        )
        val types = ConnectedGameAccountsStateRepository::class.java.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
        assertFalse(types.any { type -> type.typeName.contains("android.") })
    }


    @Test
    fun optimizationMemoryContractIsAndroidFreeAndExplicit() {
        assertEquals(
            listOf(
                "clearAll",
                "clearGame",
                "observationsFlow",
                "pruneTo",
                "record",
            ).sorted(),
            GameOptimizationMemoryStateRepository::class.java.methods.map { it.name }.sorted(),
        )
        val types = GameOptimizationMemoryStateRepository::class.java.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
        assertFalse(types.any { type -> type.typeName.contains("android.") })
    }

    @Test
    fun storeLibraryContractIsAndroidFreeAndExplicit() {
        assertEquals(
            listOf(
                "getAll",
                "removeForAccount",
                "replaceForAccount",
            ).sorted(),
            StoreLibraryStateRepository::class.java.methods.map { it.name }.sorted(),
        )
        val types = StoreLibraryStateRepository::class.java.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
        assertFalse(types.any { type -> type.typeName.contains("android.") })
    }

    @Test
    fun sessionContractIsAndroidFreeAndExplicit() {
        assertEquals(
            listOf(
                "clearSessions",
                "finishActiveSessions",
                "finishSession",
                "sessionsFlow",
                "startSession",
            ).sorted(),
            GameSessionStateRepository::class.java.methods.map { it.name }.sorted(),
        )
        val types = GameSessionStateRepository::class.java.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
        assertFalse(types.any { type -> type.typeName.contains("android.") })
    }

    @Test
    fun aliasContractIsAndroidFreeAndExplicit() {
        assertEquals(
            listOf("aliases", "save"),
            GameAliasStateRepository::class.java.methods.map { it.name }.sorted(),
        )
        val types = GameAliasStateRepository::class.java.methods.flatMap { method ->
            method.genericParameterTypes.toList() + method.genericReturnType
        }
        assertFalse(types.any { type -> type.typeName.contains("android.") })
    }
}
