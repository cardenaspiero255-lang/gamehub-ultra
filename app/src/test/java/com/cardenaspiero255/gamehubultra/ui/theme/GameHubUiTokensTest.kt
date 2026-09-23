package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubUiTokensTest {
    @Test
    fun compactSpacingRemainsTightForLandscapeDashboard() {
        assertEquals(12.dp, GameHubUiTokens.compactHorizontalPadding)
        assertEquals(10.dp, GameHubUiTokens.compactSectionSpacing)
        assertEquals(14.dp, GameHubUiTokens.compactCardPadding)
        assertEquals(8.dp, GameHubUiTokens.compactControlSpacing)
    }

    @Test
    fun compactTypographyKeepsReadableReducedScale() {
        assertEquals(18.sp, GameHubUiTokens.titleLarge)
        assertEquals(15.sp, GameHubUiTokens.titleMedium)
        assertEquals(14.sp, GameHubUiTokens.bodyLarge)
        assertEquals(13.sp, GameHubUiTokens.bodyMedium)
        assertEquals(12.sp, GameHubUiTokens.bodySmall)
        assertEquals(10.sp, GameHubUiTokens.labelSmall)
    }
}
