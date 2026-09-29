package com.cardenaspiero255.gamehubultra.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubPresentationTest {

    @Test
    fun libraryDeepLinkSelectsLibraryTab() {
        assertEquals(1, GameHubPresentation.initialTabFor("library"))
    }

    @Test
    fun missingDeepLinkSelectsDefaultTab() {
        assertEquals(0, GameHubPresentation.initialTabFor(null))
    }

    @Test
    fun unknownDeepLinkSelectsDefaultTab() {
        assertEquals(0, GameHubPresentation.initialTabFor("profile"))
    }

    @Test
    fun deepLinkMatchingIsExact() {
        assertEquals(0, GameHubPresentation.initialTabFor("Library"))
        assertEquals(0, GameHubPresentation.initialTabFor("library/"))
    }
}
