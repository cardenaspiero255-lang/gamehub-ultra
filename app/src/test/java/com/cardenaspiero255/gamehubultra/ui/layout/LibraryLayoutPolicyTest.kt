package com.cardenaspiero255.gamehubultra.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryLayoutPolicyTest {
    @Test
    fun portraitPhonesUseCompactStableGameCards() {
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 148, minTileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(360)
        )
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 148, minTileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(412)
        )
    }

    @Test
    fun widerLayoutsKeepCardsBoundedWithoutStretching() {
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 168, minTileHeightDp = 132),
            LibraryLayoutPolicy.metricsForWidthDp(700)
        )
        assertEquals(
            LibraryGridMetrics(minTileWidthDp = 180, minTileHeightDp = 126),
            LibraryLayoutPolicy.metricsForWidthDp(840)
        )
    }
}
