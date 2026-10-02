package com.cardenaspiero255.gamehubultra.ui.layout

data class LibraryGridMetrics(
    val minTileWidthDp: Int,
    val minTileHeightDp: Int
)

internal enum class LibraryHeroLayout { COMPACT, WIDE }

object LibraryLayoutPolicy {
    fun metricsForWidthDp(widthDp: Int): LibraryGridMetrics =
        when {
            widthDp >= 840 -> LibraryGridMetrics(
                minTileWidthDp = 180,
                minTileHeightDp = 126
            )
            widthDp >= 600 -> LibraryGridMetrics(
                minTileWidthDp = 168,
                minTileHeightDp = 132
            )
            else -> LibraryGridMetrics(
                minTileWidthDp = 148,
                minTileHeightDp = 132
            )
        }

    internal fun heroLayoutForWidthDp(widthDp: Int): LibraryHeroLayout =
        if (widthDp >= 700) LibraryHeroLayout.WIDE else LibraryHeroLayout.COMPACT

    internal fun carouselCardWidthForWidthDp(widthDp: Int): Int =
        when {
            widthDp >= 840 -> 208
            widthDp >= 600 -> 190
            else -> 172
        }
}
