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
    fun assistantOnlyResetNoticeIsNotPersisted() {
        assertFalse(
            UltraMemoryTurnPersistencePolicy.shouldPersist(
                listOf("Tú: Ultra olvida todo"),
                listOf("Ultra: Borré la memoria guardada.")
            )
        )
    }

    @Test
    fun destructiveAndArchiveCommandsResetActiveConversationContext() {
        assertTrue(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra olvida prefiero X4"))
        assertTrue(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra elimina de tu memoria prefiero X4"))
        assertTrue(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra archiva prefiero X4"))
        assertTrue(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra borra mi historial"))
        assertTrue(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra olvida todo"))
        assertFalse(UltraMemoryTurnPersistencePolicy.resetsConversationContext("Ultra recuerda que prefiero X4"))
        assertFalse(UltraMemoryTurnPersistencePolicy.resetsConversationContext("hola Ultra"))
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
