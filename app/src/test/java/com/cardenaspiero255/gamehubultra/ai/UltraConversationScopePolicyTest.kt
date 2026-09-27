package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraConversationScopePolicyTest {
    @Test
    fun onlyAcceptsUpdatesFromTheCurrentGameScope() {
        assertTrue(UltraConversationScopePolicy.isSameGame("game.a", "game.a"))
        assertTrue(UltraConversationScopePolicy.isSameGame(null, null))
        assertFalse(UltraConversationScopePolicy.isSameGame("game.a", "game.b"))
        assertFalse(UltraConversationScopePolicy.isSameGame("game.a", null))
    }

    @Test
    fun lateTurnResultIsNotPublishedAfterGameScopeChanges() {
        var published = false

        UltraConversationScopePolicy.runIfSameGame(
            originatingGamePackage = "game.a",
            currentGamePackage = "game.b"
        ) {
            published = true
        }

        assertFalse(published)
    }

    @Test
    fun currentTurnResultIsPublishedWhenGameScopeStillMatches() {
        var published = false

        UltraConversationScopePolicy.runIfSameGame(
            originatingGamePackage = "game.a",
            currentGamePackage = "game.a"
        ) {
            published = true
        }

        assertTrue(published)
    }
}
