package com.cardenaspiero255.gamehubultra.ui.home.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeUiStateTest {
    @Test
    fun defaultsKeepTransientHomeDataEmptyAndVoiceClosed() {
        val state = HomeUiState()

        assertEquals(0, state.localGameCount)
        assertFalse(state.quickVoiceOpen)
        assertEquals(0, state.quickVoiceRevealRequest)
        assertEquals(0, state.quickVoiceListenRequest)
    }

    @Test
    fun gameCountLoadedUpdatesOnlyDiscoveredGameCount() {
        val initial = HomeUiState(
            quickVoiceOpen = true,
            quickVoiceRevealRequest = 2,
            quickVoiceListenRequest = 3,
        )

        val updated = initial.reduce(HomeUiEvent.GameCountLoaded(7))

        assertEquals(7, updated.localGameCount)
        assertTrue(updated.quickVoiceOpen)
        assertEquals(2, updated.quickVoiceRevealRequest)
        assertEquals(3, updated.quickVoiceListenRequest)
    }

    @Test
    fun togglingQuickVoiceRequestsRevealOnlyWhenOpening() {
        val opened = HomeUiState().reduce(HomeUiEvent.QuickVoiceToggled)
        assertTrue(opened.quickVoiceOpen)
        assertEquals(1, opened.quickVoiceRevealRequest)

        val closed = opened.reduce(HomeUiEvent.QuickVoiceToggled)
        assertFalse(closed.quickVoiceOpen)
        assertEquals(1, closed.quickVoiceRevealRequest)

        val reopened = closed.reduce(HomeUiEvent.QuickVoiceToggled)
        assertTrue(reopened.quickVoiceOpen)
        assertEquals(2, reopened.quickVoiceRevealRequest)
    }

    @Test
    fun explicitQuickVoiceRevealAlwaysOpensAndRequestsScroll() {
        val alreadyOpen = HomeUiState(
            quickVoiceOpen = true,
            quickVoiceRevealRequest = 4,
        )

        val revealed = alreadyOpen.reduce(HomeUiEvent.QuickVoiceRevealed)

        assertTrue(revealed.quickVoiceOpen)
        assertEquals(5, revealed.quickVoiceRevealRequest)
    }

    @Test
    fun talkRequestOpensVoiceAndRequestsImmediateListening() {
        val requested = HomeUiState().reduce(HomeUiEvent.QuickVoiceListenRequested)

        assertTrue(requested.quickVoiceOpen)
        assertEquals(1, requested.quickVoiceRevealRequest)
        assertEquals(1, requested.quickVoiceListenRequest)
    }

    @Test
    fun repeatedTalkRequestCreatesNewListeningRequestWithoutClosingVoice() {
        val first = HomeUiState().reduce(HomeUiEvent.QuickVoiceListenRequested)
        val second = first.reduce(HomeUiEvent.QuickVoiceListenRequested)

        assertTrue(second.quickVoiceOpen)
        assertEquals(2, second.quickVoiceRevealRequest)
        assertEquals(2, second.quickVoiceListenRequest)
    }

    @Test
    fun holderDispatchesHomeEventsThroughReducer() {
        val holder = HomeUiStateHolder()

        holder.onEvent(HomeUiEvent.GameCountLoaded(3))
        holder.onEvent(HomeUiEvent.QuickVoiceToggled)

        assertEquals(3, holder.state.localGameCount)
        assertTrue(holder.state.quickVoiceOpen)
        assertEquals(1, holder.state.quickVoiceRevealRequest)
    }
}
