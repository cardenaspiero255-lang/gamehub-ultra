package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraVoiceResultPublisherTest {
    @Test
    fun oldGameVoiceResultDoesNotPublishAfterGameSwitch() {
        var published = false

        val accepted = UltraVoiceResultPublisher.publishIfCurrentGame(
            originatingGamePackage = "game.old",
            currentGamePackage = "game.new"
        ) {
            published = true
        }

        assertFalse(accepted)
        assertFalse(published)
    }

    @Test
    fun currentGameVoiceResultPublishesNormally() {
        var published = false

        val accepted = UltraVoiceResultPublisher.publishIfCurrentGame(
            originatingGamePackage = "game.current",
            currentGamePackage = "game.current"
        ) {
            published = true
        }

        assertTrue(accepted)
        assertTrue(published)
    }
}
