package com.cardenaspiero255.gamehubultra.platform

import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GamePlatformLinksTest {

    @Test
    fun supportsNumericSteamProfileId() {
        val account = ConnectedGameAccount(
            id = "1",
            platform = GamePlatform.STEAM,
            displayName = "Steam",
            publicId = "76561198012345678"
        )

        assertTrue(GamePlatformLinks.isPublicProfileIdSupported(account))
    }

    @Test
    fun supportsVanitySteamProfileId() {
        val account = ConnectedGameAccount(
            id = "2",
            platform = GamePlatform.STEAM,
            displayName = "Steam",
            publicId = "player_one-2026"
        )

        assertTrue(GamePlatformLinks.isPublicProfileIdSupported(account))
    }

    @Test
    fun rejectsMalformedSteamProfileId() {
        assertFalse(
            GamePlatformLinks.isPublicProfileIdSupported(
                GamePlatform.STEAM,
                "not a valid profile id!"
            )
        )
    }

    @Test
    fun rejectsUnsupportedPlatformAndBlankProfile() {
        val epic = ConnectedGameAccount(
            id = "3",
            platform = GamePlatform.EPIC_GAMES,
            displayName = "Epic",
            publicId = "player"
        )
        val blank = ConnectedGameAccount(
            id = "4",
            platform = GamePlatform.STEAM,
            displayName = "Steam",
            publicId = " "
        )

        assertFalse(GamePlatformLinks.isPublicProfileIdSupported(epic))
        assertFalse(GamePlatformLinks.isPublicProfileIdSupported(blank))
    }
}
