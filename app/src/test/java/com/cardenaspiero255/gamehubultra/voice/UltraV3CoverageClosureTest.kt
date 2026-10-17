package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Extra JVM regression coverage for EVO V3 physical feedback.
 * This cannot replace actual Vivo V25 Pro ASR/TTS testing.
 */
class UltraV3CoverageClosureTest {
    @Test
    fun personalizedCommandWinsOverContextWithoutCallingFallback() {
        val personalized = VoiceCommand.SelectProfile(PerformanceProfile.BALANCED)
        val contextual = VoiceCommand.SelectProfile(PerformanceProfile.X4)
        var calls = 0
        val route = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, pon balanceado",
            personalizedCommand = personalized,
            contextualCommand = contextual,
            fallback = {
                calls++
                UltraAgentRoute.Command(contextual)
            }
        )
        assertEquals(personalized, assertIs<UltraAgentRoute.Command>(route).command)
        assertEquals(0, calls)
    }

    @Test
    fun unknownContextDelegatesToFallbackExactlyOnce() {
        var calls = 0
        val route = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, cuéntame sobre la penicilina",
            personalizedCommand = null,
            contextualCommand = VoiceCommand.Unknown("sin comando"),
            fallback = {
                calls++
                UltraAgentRoute.Chat(message = "respuesta conversacional")
            }
        )
        assertIs<UltraAgentRoute.Chat>(route)
        assertEquals(1, calls)
    }

    @Test
    fun negationOverridesPersonalizationAndContext() {
        var calls = 0
        val route = UltraWakeRoutePrecedencePolicy.route(
            transcript = "  Ultra, no desactives X4  ",
            personalizedCommand = VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            contextualCommand = VoiceCommand.SelectProfile(PerformanceProfile.X4),
            conversationHistory = listOf("Usuario: estaba jugando"),
            fallback = {
                calls++
                UltraAgentRoute.Command(VoiceCommand.SelectProfile(PerformanceProfile.X4))
            }
        )
        assertIs<UltraAgentRoute.Chat>(route)
        assertEquals(0, calls)
    }

    @Test
    fun interruptionRequiresActiveCommandAndCanRestartAfterRelease() {
        val coordinator = UltraWakeCommandCoordinator()
        assertFalse(coordinator.tryStartSpeechInterruptionRecognition())
        assertTrue(coordinator.tryBeginCommand())
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        assertFalse(coordinator.tryStartSpeechInterruptionRecognition())
        coordinator.onRecognitionFinished()
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        coordinator.onRecognitionFinished()
        assertTrue(coordinator.finishCommand())
        assertFalse(coordinator.tryStartSpeechInterruptionRecognition())
    }

    @Test
    fun partialAsrOnlyStopsOnCompleteStopAlternativeWhilePlaying() {
        assertFalse(UltraWakePartialStopPolicy.shouldStop(true, emptyList()))
        assertFalse(UltraWakePartialStopPolicy.shouldStop(false, listOf("Ultra, para")))
        assertFalse(UltraWakePartialStopPolicy.shouldStop(true, listOf("Ultra, para qué sirve X4")))
        assertTrue(
            UltraWakePartialStopPolicy.shouldStop(
                true,
                listOf("Ultra, para qué sirve X4", "Ultra, detente")
            )
        )
    }
}
