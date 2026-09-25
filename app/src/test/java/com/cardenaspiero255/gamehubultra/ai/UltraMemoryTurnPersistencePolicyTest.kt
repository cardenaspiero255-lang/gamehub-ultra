package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraMemoryTurnPersistencePolicyTest {
    @Test
    fun destructiveMemoryCommandAndItsReplyAreNotPersisted() {
        val userTurn = listOf("Tú: Ultra olvida todo")
        val answered = userTurn + "Ultra: Borré la memoria guardada."

        assertFalse(UltraMemoryTurnPersistencePolicy.shouldPersist(emptyList(), userTurn))
        assertFalse(UltraMemoryTurnPersistencePolicy.shouldPersist(userTurn, answered))
    }

    @Test
    fun ordinaryConversationIsPersisted() {
        assertTrue(
            UltraMemoryTurnPersistencePolicy.shouldPersist(
                emptyList(),
                listOf("Tú: hola", "Ultra: hola")
            )
        )
    }
}
