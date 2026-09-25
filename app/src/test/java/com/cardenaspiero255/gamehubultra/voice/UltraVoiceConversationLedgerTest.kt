package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraVoiceConversationLedgerTest {
    @Test
    fun recordsVoiceTurnsAndReturnsIncrementalDelta() {
        val ledger = UltraVoiceConversationLedger(maxEntries = 4)

        val first = ledger.record("hola", "hola")
        val second = ledger.record("como estas", "bien")

        assertEquals(emptyList(), first.previous)
        assertEquals(listOf("Tú: hola", "Ultra: hola"), first.next)
        assertEquals(first.next, second.previous)
        assertEquals(
            listOf("Tú: hola", "Ultra: hola", "Tú: como estas", "Ultra: bien"),
            second.next
        )
    }
    @Test
    fun changingGameScopeClearsConversationLedger() {
        val ledger = UltraVoiceConversationLedger(maxEntries = 4)

        ledger.bindScope("game.a")
        ledger.record("hola desde A", "respuesta A")
        assertEquals(
            listOf("Tú: hola desde A", "Ultra: respuesta A"),
            ledger.snapshot()
        )

        ledger.bindScope("game.b")

        assertEquals(emptyList(), ledger.snapshot())
    }
}
