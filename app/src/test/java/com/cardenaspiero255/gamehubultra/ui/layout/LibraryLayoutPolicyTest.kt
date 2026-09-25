package com.cardenaspiero255.gamehubultra.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryLayoutPolicyTest {
    @Test
    fun portraitPhonesUseCompactStableGameCards() {
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 148, tileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(360)
        )
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 148, tileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(412)
        )
    }

    @Test
    fun widerLayoutsKeepCardsBoundedWithoutStretching() {
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 168, tileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(700)
        )
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 180, tileHeightDp = 126),
            LibraryLayoutPolicy.metricsForWidthDp(840)
        )
    }
}
