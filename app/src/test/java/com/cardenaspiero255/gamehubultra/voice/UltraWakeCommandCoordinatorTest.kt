package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakeCommandCoordinatorTest {
    @Test
    fun duplicateRecognitionStartIsRejected() {
        val coordinator = UltraWakeCommandCoordinator()

        assertTrue(coordinator.tryStartRecognition())
        assertFalse(coordinator.tryStartRecognition())

        coordinator.onRecognitionFinished()
        assertTrue(coordinator.tryStartRecognition())
    }

    @Test
    fun commandBlocksRecognitionUntilItFinishes() {
        val coordinator = UltraWakeCommandCoordinator()

        assertTrue(coordinator.tryStartRecognition())
        assertTrue(coordinator.tryBeginCommand())

        assertFalse(coordinator.tryStartRecognition())
        assertFalse(coordinator.tryBeginCommand())

        coordinator.finishCommand()
        assertTrue(coordinator.tryStartRecognition())
    }

    @Test
    fun failedRecognitionCanBeRestartedWhenNoCommandIsRunning() {
        val coordinator = UltraWakeCommandCoordinator()

        assertTrue(coordinator.tryStartRecognition())
        coordinator.onRecognitionFinished()

        assertTrue(coordinator.tryStartRecognition())
    }

    @Test
    fun unexpectedCommandFailureIsConvertedToSafeResponse() {
        val response = UltraWakeFailureGuard.run {
            error("simulated runtime failure")
        }

        assertEquals(
            "No pude completar el comando de Ultra. Inténtalo de nuevo.",
            response
        )
    }

}
