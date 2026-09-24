package com.cardenaspiero255.gamehubultra.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveLayoutPolicyTest {
    @Test
    fun compactBelowWideBreakpoint() {
        assertEquals(
            UltraLayoutMode.COMPACT,
            ResponsiveLayoutPolicy.modeForWidthDp(839)
        )
    }

    @Test
    fun wideAtLandscapeBreakpoint() {
        assertEquals(
            UltraLayoutMode.WIDE,
            ResponsiveLayoutPolicy.modeForWidthDp(840)
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
