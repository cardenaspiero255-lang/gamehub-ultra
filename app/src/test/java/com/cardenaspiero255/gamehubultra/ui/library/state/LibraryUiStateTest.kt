package com.cardenaspiero255.gamehubultra.ui.library.state

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
        assertFalse(LibraryUiState(launchFailed = true).reduce(LibraryUiEvent.GameLaunchResult(true)).launchFailed)
        assertTrue(LibraryUiState().reduce(LibraryUiEvent.GameLaunchResult(false)).launchFailed)
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
    fun stateHolderDispatchesThroughReducer() {
        val holder = LibraryUiStateHolder(LibraryUiState(query = "before"))
        holder.onEvent(LibraryUiEvent.QueryChanged("after"))
        holder.onEvent(LibraryUiEvent.GameLaunchResult(false))

        assertEquals("after", holder.state.query)
        assertTrue(holder.state.launchFailed)
    }
}
