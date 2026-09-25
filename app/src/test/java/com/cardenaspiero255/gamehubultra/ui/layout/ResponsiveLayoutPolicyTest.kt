package com.cardenaspiero255.gamehubultra.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveLayoutPolicyTest {
    @Test
    fun compactPhonePortraitStaysCompact() {
        assertEquals(
            UltraLayoutMode.COMPACT,
            ResponsiveLayoutPolicy.modeForWidthDp(599)
        )
    }

    @Test
    fun phoneLandscapeUsesWideNavigationRail() {
        assertEquals(
            UltraLayoutMode.WIDE,
            ResponsiveLayoutPolicy.modeForWidthDp(600)
        )
        assertEquals(
            UltraLayoutMode.WIDE,
            ResponsiveLayoutPolicy.modeForWidthDp(1199)
        )
    }

    @Test
    fun ultraWideAtAssistantPanelBreakpoint() {
        assertEquals(
            UltraLayoutMode.ULTRA_WIDE,
            ResponsiveLayoutPolicy.modeForWidthDp(1200)
        )
    }
}
