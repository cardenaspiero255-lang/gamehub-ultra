package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubUiTokensTest {
    @Test
    fun compactSpacingRemainsTightForLandscapeDashboard() {
        assertEquals(9.dp, GameHubUiTokens.compactHorizontalPadding)
        assertEquals(7.dp, GameHubUiTokens.compactSectionSpacing)
        assertEquals(9.dp, GameHubUiTokens.compactCardPadding)
        assertEquals(6.dp, GameHubUiTokens.compactControlSpacing)
    }

    @Test
    fun compactTypographyKeepsReadableReducedScale() {
        assertEquals(16.sp, GameHubUiTokens.titleLarge)
        assertEquals(14.sp, GameHubUiTokens.titleMedium)
        assertEquals(13.sp, GameHubUiTokens.bodyLarge)
        assertEquals(12.sp, GameHubUiTokens.bodyMedium)
        assertEquals(11.sp, GameHubUiTokens.bodySmall)
        assertEquals(9.sp, GameHubUiTokens.labelSmall)
    }
}
