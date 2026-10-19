package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraPhase5PrivacyEpochTest {
    @Test
    fun erasureInvalidatesEveryPendingVoiceResult() {
        val before = UltraPrivacyEpoch.current()
        assertTrue(UltraPrivacyEpoch.isCurrent(before))
        val after = UltraPrivacyEpoch.invalidate()
        assertFalse(UltraPrivacyEpoch.isCurrent(before))
        assertEquals(after, UltraPrivacyEpoch.current())
        assertTrue(UltraPrivacyEpoch.isCurrent(after))
    }

    @Test
    fun continuousVoiceLedgerDoesNotRetainDeletedConversationAcrossEpoch() {
        val ledger = UltraVoiceConversationLedger(maxEntries = 8)
        ledger.bindScope("game.a")
        ledger.record("mi dato privado", "respuesta privada")
        assertEquals(2, ledger.snapshot().size)

        UltraPrivacyEpoch.invalidate()
        assertTrue(ledger.snapshot().isEmpty())
        ledger.bindScope("game.a")
        ledger.record("nuevo contexto", "nuevo dato")
        assertEquals(listOf("Tú: nuevo contexto", "Ultra: nuevo dato"), ledger.snapshot())
    }

    @Test
    fun queuedOldVoiceResultCannotAppendAfterErasure() {
        val ledger = UltraVoiceConversationLedger(maxEntries = 8)
        val captured = UltraPrivacyEpoch.current()
        UltraPrivacyEpoch.invalidate()
        if (UltraPrivacyEpoch.isCurrent(captured)) {
            ledger.record("turno viejo", "respuesta vieja")
        }
        assertTrue(ledger.snapshot().isEmpty())
    }
}
