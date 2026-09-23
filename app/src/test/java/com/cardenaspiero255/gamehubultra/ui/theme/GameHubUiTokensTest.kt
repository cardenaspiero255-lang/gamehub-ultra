package com.cardenaspiero255.gamehubultra.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubUiTokensTest {
    @Test
    fun compactSpacingRemainsTightForLandscapeDashboard() {
        assertEquals(12, GameHubUiTokens.compactHorizontalPadding.value.toInt())
        assertEquals(10, GameHubUiTokens.compactSectionSpacing.value.toInt())
        assertEquals(14, GameHubUiTokens.compactCardPadding.value.toInt())
        assertEquals(8, GameHubUiTokens.compactControlSpacing.value.toInt())
    }

    @Test
    fun compactTypographyKeepsReadableReducedScale() {
        assertEquals(18, GameHubUiTokens.titleLarge.value.toInt())
        assertEquals(15, GameHubUiTokens.titleMedium.value.toInt())
        assertEquals(14, GameHubUiTokens.bodyLarge.value.toInt())
        assertEquals(13, GameHubUiTokens.bodyMedium.value.toInt())
        assertEquals(12, GameHubUiTokens.bodySmall.value.toInt())
        assertEquals(10, GameHubUiTokens.labelSmall.value.toInt())
    }
}
