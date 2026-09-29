package com.cardenaspiero255.gamehubultra.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubWideNavigationRailTest {
    @Test
    fun tabSelectionMapsToHomeAndLibrary() {
        assertEquals(
            GameHubWideNavDestination.HOME,
            selectedWideNavDestination(selectedTab = 0, settingsOpen = false, profileOpen = false)
        )
        assertEquals(
            GameHubWideNavDestination.LIBRARY,
            selectedWideNavDestination(selectedTab = 1, settingsOpen = false, profileOpen = false)
        )
    }

    @Test
    fun profileOverridesTabSelection() {
        assertEquals(
            GameHubWideNavDestination.PROFILE,
            selectedWideNavDestination(selectedTab = 1, settingsOpen = false, profileOpen = true)
        )
    }

    @Test
    fun settingsHasHighestSelectionPriority() {
        assertEquals(
            GameHubWideNavDestination.SETTINGS,
            selectedWideNavDestination(selectedTab = 1, settingsOpen = true, profileOpen = true)
        )
    }
}
