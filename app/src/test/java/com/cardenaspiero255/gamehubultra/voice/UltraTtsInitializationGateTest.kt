package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraTtsInitializationGateTest {
    @Test
    fun firstSpokenAnswerWaitsUntilTheEngineFinishesInitializing() {
        val gate = UltraTtsInitializationGate()
        assertNull(gate.offer("Comparación de teléfonos"))
        assertEquals("Comparación de teléfonos", gate.initialized(true))
        assertEquals("Seguimos conversando", gate.offer("Seguimos conversando"))
    }

    @Test
    fun latestResponseWinsWhileTheEngineIsStillStarting() {
        val gate = UltraTtsInitializationGate()
        gate.offer("Respuesta vieja")
        gate.offer("Respuesta actual")
        assertEquals("Respuesta actual", gate.initialized(true))
        assertNull(gate.initialized(true))
    }

    @Test
    fun unsuccessfulInitializationDropsPendingSpeech() {
        val gate = UltraTtsInitializationGate()
        gate.offer("Una respuesta que no debe reproducirse")
        assertNull(gate.initialized(false))
        assertNull(gate.offer("No hablar si Android no tiene motor TTS"))
        assertNull(gate.initialized(true))
    }

    @Test
    fun closingBeforeEngineCallbackNeverReplaysSpeech() {
        val gate = UltraTtsInitializationGate()
        gate.offer("Interrumpida")
        gate.close()
        assertNull(gate.initialized(true))
        assertNull(gate.offer("No reactivar"))
    }

    @Test
    fun cancellingSpeechBeforeInitializationDropsOnlyPendingAnswer() {
        val gate = UltraTtsInitializationGate()
        gate.offer("Respuesta que el usuario acaba de cancelar")
        kotlin.test.assertTrue(gate.cancelPending())
        kotlin.test.assertNull(gate.initialized(true))
        assertEquals("La siguiente consulta se puede pronunciar", gate.offer("La siguiente consulta se puede pronunciar"))
        kotlin.test.assertFalse(gate.cancelPending())
    }

    @Test
    fun blankInputIsNeverEnqueued() {
        val gate = UltraTtsInitializationGate()
        assertNull(gate.offer("    "))
        assertNull(gate.initialized(true))
    }
}
