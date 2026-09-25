package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
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
}
