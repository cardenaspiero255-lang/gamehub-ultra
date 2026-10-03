package com.cardenaspiero255.gamehubultra.ui.home.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeQuickVoiceStartTest {
    @Test
    fun tappingVoiceHeroOpensAssistantAndRequestsImmediateListening() {
        val started = HomeUiState().reduce(HomeUiEvent.QuickVoiceStartRequested)

        assertTrue(started.quickVoiceOpen)
        assertEquals(1, started.quickVoiceRevealRequest)
        assertEquals(1, started.quickVoiceStartRequest)
    }

    @Test
    fun repeatedVoiceHeroTapsCreateDistinctListeningRequests() {
        val first = HomeUiState().reduce(HomeUiEvent.QuickVoiceStartRequested)
        val second = first.reduce(HomeUiEvent.QuickVoiceStartRequested)

        assertTrue(second.quickVoiceOpen)
        assertEquals(2, second.quickVoiceStartRequest)
    }
}
