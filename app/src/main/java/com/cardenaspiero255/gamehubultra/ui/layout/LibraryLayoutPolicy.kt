package com.cardenaspiero255.gamehubultra.ui.layout

data class LibraryGridMetrics(
    val minTileWidthDp: Int,
    val minTileHeightDp: Int
)

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
}
