package com.cardenaspiero255.gamehubultra.ui.layout

data class LibraryGridMetrics(
    val minTileWidthDp: Int,
    val tileHeightDp: Int
)

object LibraryLayoutPolicy {
    fun metricsForWidthDp(widthDp: Int): LibraryGridMetrics =
        when {
            widthDp >= 840 -> LibraryGridMetrics(
                minTileWidthDp = 180,
                tileHeightDp = 126
            )
            widthDp >= 600 -> LibraryGridMetrics(
                minTileWidthDp = 168,
                tileHeightDp = 132
            )
            else -> LibraryGridMetrics(
                minTileWidthDp = 148,
                tileHeightDp = 132
            )
        }
}
