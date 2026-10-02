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
    fun selectedGameHeroSwitchesToWideCompositionOnlyWhenSpaceAllows() {
        assertEquals(LibraryHeroLayout.COMPACT, LibraryLayoutPolicy.heroLayoutForWidthDp(412))
        assertEquals(LibraryHeroLayout.COMPACT, LibraryLayoutPolicy.heroLayoutForWidthDp(699))
        assertEquals(LibraryHeroLayout.WIDE, LibraryLayoutPolicy.heroLayoutForWidthDp(700))
        assertEquals(172, LibraryLayoutPolicy.carouselCardWidthForWidthDp(412))
        assertEquals(208, LibraryLayoutPolicy.carouselCardWidthForWidthDp(840))
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
