package com.cardenaspiero255.gamehubultra.ui.library.state

import com.cardenaspiero255.gamehubultra.GameDiscoveryResult
import com.cardenaspiero255.gamehubultra.GameInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryUiStateTest {
    @Test
    fun queryChangePreservesUnrelatedState() {
        val initial = LibraryUiState(refreshToken = 3, launchFailed = true)
        val updated = initial.reduce(LibraryUiEvent.QueryChanged("resident evil"))
        assertEquals("resident evil", updated.query)
        assertEquals(3, updated.refreshToken)
        assertTrue(updated.launchFailed)
    }

    @Test
    fun resumedRequestsRefreshWithoutMutatingOtherFlags() {
        val initial = LibraryUiState(refreshToken = 4, addGameDialogVisible = true)
        val updated = initial.reduce(LibraryUiEvent.Resumed)
        assertEquals(5, updated.refreshToken)
        assertTrue(updated.addGameDialogVisible)
    }

    @Test
    fun selectingGameClearsPreviousLaunchFailure() {
        val updated = LibraryUiState(launchFailed = true)
            .reduce(LibraryUiEvent.GameSelected)
        assertFalse(updated.launchFailed)
    }

    @Test
    fun launchResultReflectsFailureOnly() {
        assertFalse(
            LibraryUiState(launchFailed = true)
                .reduce(LibraryUiEvent.GameLaunchResult(true))
                .launchFailed
        )
        assertTrue(
            LibraryUiState()
                .reduce(LibraryUiEvent.GameLaunchResult(false))
                .launchFailed
        )
    }

    @Test
    fun dialogAndDetailsEventsAreDeterministic() {
        val opened = LibraryUiState()
            .reduce(LibraryUiEvent.AddGameDialogVisibilityChanged(true))
            .reduce(LibraryUiEvent.SelectedGameDetailsVisibilityChanged(true))
        assertTrue(opened.addGameDialogVisible)
        assertTrue(opened.selectedGameDetailsVisible)
    }

    @Test
    fun discoveryLoadedStoresRuntimeSnapshotWithoutChangingPresentationState() {
        val result = GameDiscoveryResult(
            games = listOf(GameInfo(packageName = "com.example.game", label = "Example Game"))
        )
        val updated = LibraryUiState(query = "example", launchFailed = true)
            .reduce(LibraryUiEvent.DiscoveryLoaded(result))

        assertEquals(result, updated.discovery)
        assertEquals("example", updated.query)
        assertTrue(updated.launchFailed)
    }

    @Test
    fun launchableAppsLoadedReplacesCandidatesWithoutClosingDialog() {
        val apps = listOf(
            GameInfo(packageName = "com.example.one", label = "One"),
            GameInfo(packageName = "com.example.two", label = "Two")
        )
        val updated = LibraryUiState(addGameDialogVisible = true)
            .reduce(LibraryUiEvent.LaunchableAppsLoaded(apps))

        assertEquals(apps, updated.launchableApps)
        assertTrue(updated.addGameDialogVisible)
    }

    @Test
    fun localFilterSelectionIsStoredWithoutMutatingOtherLibraryState() {
        val initial = LibraryUiState(query = "resident", launchFailed = true)

        val updated = initial.reduce(
            LibraryUiEvent.LocalFilterChanged(LibraryLocalFilter.FAVORITES)
        )

        assertEquals(LibraryLocalFilter.FAVORITES, updated.localFilter)
        assertEquals("resident", updated.query)
        assertTrue(updated.launchFailed)
    }

    @Test
    fun localFilterCanReturnToAllGames() {
        val updated = LibraryUiState(localFilter = LibraryLocalFilter.RECENT)
            .reduce(LibraryUiEvent.LocalFilterChanged(LibraryLocalFilter.ALL))

        assertEquals(LibraryLocalFilter.ALL, updated.localFilter)
    }


    @Test
    fun localFilterAppliesFavoritesRecentsAndQueryDeterministically() {
        val games = listOf(
            GameInfo(packageName = "com.game.resident", label = "Resident Evil 4"),
            GameInfo(packageName = "com.game.brawl", label = "Brawl Stars"),
            GameInfo(packageName = "com.game.cod", label = "Call of Duty")
        )
        val favorites = setOf("com.game.resident", "com.game.cod")
        val recents = listOf("com.game.brawl", "com.game.resident")

        assertEquals(
            listOf("com.game.resident", "com.game.cod"),
            filterLibraryGames(games, "", LibraryLocalFilter.FAVORITES, favorites, recents)
                .map { it.packageName }
        )
        assertEquals(
            listOf("com.game.brawl", "com.game.resident"),
            filterLibraryGames(games, "", LibraryLocalFilter.RECENT, favorites, recents)
                .map { it.packageName }
        )
        assertEquals(
            listOf("com.game.resident"),
            filterLibraryGames(games, "resident", LibraryLocalFilter.FAVORITES, favorites, recents)
                .map { it.packageName }
        )
        assertEquals(
            games,
            filterLibraryGames(games, "", LibraryLocalFilter.ALL, favorites, recents)
        )
    }

    @Test
    fun installedAndSourceCategoryFiltersComposeWithoutInventingMetadata() {
        val games = listOf(
            GameInfo(packageName = "com.game.detected", label = "Detected Game"),
            GameInfo(packageName = "com.game.manual", label = "Manual Game")
        )
        val manualPackages = setOf("com.game.manual")

        assertEquals(
            games,
            filterLibraryGames(
                games = games,
                query = "",
                localFilter = LibraryLocalFilter.INSTALLED,
                favoriteGames = emptySet(),
                recentGamePackages = emptyList(),
                category = LibraryCategory.ALL,
                manualGamePackages = manualPackages
            )
        )
        assertEquals(
            listOf("com.game.manual"),
            filterLibraryGames(
                games = games,
                query = "",
                localFilter = LibraryLocalFilter.ALL,
                favoriteGames = emptySet(),
                recentGamePackages = emptyList(),
                category = LibraryCategory.MANUAL,
                manualGamePackages = manualPackages
            ).map { it.packageName }
        )
        assertEquals(
            listOf("com.game.detected"),
            filterLibraryGames(
                games = games,
                query = "",
                localFilter = LibraryLocalFilter.ALL,
                favoriteGames = emptySet(),
                recentGamePackages = emptyList(),
                category = LibraryCategory.DETECTED,
                manualGamePackages = manualPackages
            ).map { it.packageName }
        )
    }

    @Test
    fun categorySelectionIsStoredIndependentlyFromLocalFilter() {
        val updated = LibraryUiState(localFilter = LibraryLocalFilter.FAVORITES)
            .reduce(LibraryUiEvent.CategoryChanged(LibraryCategory.MANUAL))

        assertEquals(LibraryCategory.MANUAL, updated.category)
        assertEquals(LibraryLocalFilter.FAVORITES, updated.localFilter)
    }

    @Test
    fun stateHolderDispatchesThroughReducer() {
        val holder = LibraryUiStateHolder(LibraryUiState(query = "before"))
        holder.onEvent(LibraryUiEvent.QueryChanged("after"))
        holder.onEvent(LibraryUiEvent.GameLaunchResult(false))

        assertEquals("after", holder.state.query)
        assertTrue(holder.state.launchFailed)
    }
}
