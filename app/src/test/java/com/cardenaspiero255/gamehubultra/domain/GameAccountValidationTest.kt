package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameAccountValidationTest {

    @Test
    fun acceptsInRangeSteamId64() {
        assertTrue(
            GameAccountValidation.isValidPublicId(
                GamePlatform.STEAM,
                "76561198012345678"
            )
        )
    }

    @Test
    fun acceptsNumbersOnlySteamVanity() {
        assertTrue(
            GameAccountValidation.isValidPublicId(
                GamePlatform.STEAM,
                "1234"
            )
        )
    }

    @Test
    fun rejectsOutOfRangeSteamNumericId() {
        assertFalse(
            GameAccountValidation.isValidPublicId(
                GamePlatform.STEAM,
                "99999999999999999999"
            )
        )
    }

    @Test
    fun acceptsValidEpicPublicId() {
        assertTrue(
            GameAccountValidation.isValidPublicId(
                GamePlatform.EPIC_GAMES,
                "player_123"
            )
        )
    }

    @Test
    fun rejectsMalformedEpicPublicId() {
        assertFalse(
            GameAccountValidation.isValidPublicId(
                GamePlatform.EPIC_GAMES,
                "!"
            )
        )
    }
}
