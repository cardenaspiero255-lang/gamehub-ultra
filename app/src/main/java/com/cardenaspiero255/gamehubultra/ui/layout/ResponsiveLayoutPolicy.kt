package com.cardenaspiero255.gamehubultra.ui.layout

enum class UltraLayoutMode {
    COMPACT,
    WIDE,
    ULTRA_WIDE
}

object ResponsiveLayoutPolicy {
    fun modeForWidthDp(widthDp: Int): UltraLayoutMode =
        when {
            widthDp >= 1200 -> UltraLayoutMode.ULTRA_WIDE
            widthDp >= 600 -> UltraLayoutMode.WIDE
            else -> UltraLayoutMode.COMPACT
        }
}
