package com.cardenaspiero255.gamehubultra.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraNavigationTest {
    @Test
    fun exposesAllUnifiedDestinationsInStableOrder() {
        assertEquals(
            listOf(
                UltraDestination.HOME,
                UltraDestination.LIBRARY,
                UltraDestination.BOOSTER,
                UltraDestination.ASSISTANT,
                UltraDestination.SETTINGS
            ),
            UltraNavigation.destinations
        )
    }

    @Test
    fun routesSupportedDeepLinks() {
        assertEquals(UltraRoute(UltraDestination.LIBRARY), UltraNavigation.parseDeepLink("gamehub://library"))
        assertEquals(
            UltraRoute(UltraDestination.LIBRARY, gamePackage = "com.example.game"),
            UltraNavigation.parseDeepLink("gamehub://game/com.example.game")
        )
        assertEquals(
            UltraRoute(UltraDestination.BOOSTER, performancePage = true),
            UltraNavigation.parseDeepLink("gamehub://performance")
        )
    }

    @Test
    fun rejectsUnknownOrUnsafeDeepLinks() {
        assertNull(UltraNavigation.parseDeepLink("https://library"))
        assertNull(UltraNavigation.parseDeepLink("gamehub://game/"))
        assertNull(UltraNavigation.parseDeepLink("gamehub://unknown"))
    }
}
