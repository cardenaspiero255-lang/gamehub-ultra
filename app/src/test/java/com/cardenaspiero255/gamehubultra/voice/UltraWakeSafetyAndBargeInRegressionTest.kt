package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Defense in depth for physical voice failures, including personalized intent. */
class UltraWakeSafetyAndBargeInRegressionTest {
    @Test
    fun negativeAndHypotheticalUtterancesOverrideUnsafePersonalization() {
        listOf(
            "Ultra, no desactives X4",
            "Ultra, ¿qué pasaría si activara X4?"
        ).forEach { transcript ->
            var fallbackCalls = 0
            val route = UltraWakeRoutePrecedencePolicy.route(
                transcript = transcript,
                personalizedCommand = VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
                contextualCommand = VoiceCommand.SelectProfile(PerformanceProfile.X4),
                fallback = {
                    fallbackCalls++
                    UltraAgentRoute.Command(VoiceCommand.SelectProfile(PerformanceProfile.X4))
                }
            )
            assertIs<UltraAgentRoute.Chat>(route, transcript)
            assertEquals(0, fallbackCalls, transcript)
        }
    }

    @Test
    fun explicitProfileCommandsRemainExecutable() {
        val expected = VoiceCommand.SelectProfile(PerformanceProfile.BALANCED)
        val route = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, desactiva X4",
            personalizedCommand = null,
            contextualCommand = expected,
            fallback = { error("Direct profile command should win") }
        )
        assertEquals(expected, assertIs<UltraAgentRoute.Command>(route).command)
    }

    @Test
    fun recognizerCanBeReopenedOnlyForUserInitiatedSpeechInterruption() {
        val gate = UltraWakeCommandCoordinator()
        assertTrue(gate.tryBeginCommand())
        assertFalse(gate.tryStartRecognition())
        assertTrue(gate.tryStartSpeechInterruptionRecognition())
        assertFalse(gate.tryStartSpeechInterruptionRecognition())
        gate.onRecognitionFinished()
        assertTrue(gate.finishCommand())
        assertTrue(gate.tryStartRecognition())
    }

    @Test
    fun partialRecognitionMayStopTtsButNeverTriggersOnOrdinaryPhrases() {
        assertTrue(
            UltraWakePartialStopPolicy.shouldStop(
                playbackActive = true,
                alternatives = listOf("Ultra para", "Ultra paras", "Ultra")
            )
        )
        assertFalse(
            UltraWakePartialStopPolicy.shouldStop(
                playbackActive = false,
                alternatives = listOf("Ultra para")
            )
        )
        assertFalse(
            UltraWakePartialStopPolicy.shouldStop(
                playbackActive = true,
                alternatives = listOf("Ultra, para qué sirve X4", "Ultra, compara teléfonos")
            )
        )
    }
}
