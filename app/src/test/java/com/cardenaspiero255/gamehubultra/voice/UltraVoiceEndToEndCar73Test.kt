package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraGeneralQueryKind
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraVoiceEndToEndCar73Test {

    @Test
    fun wakeCommandInterruptsTtsInsteadOfBeingSuppressed() {
        assertEquals(
            UltraWakeRecognitionDisposition.INTERRUPT_TTS,
            UltraWakeBargeInPolicy.decide(
                playbackActive = true,
                transcript = "Ultra, abre Resident Evil"
            )
        )
    }

    @Test
    fun backgroundSpeechIsSuppressedWhileTtsIsPlaying() {
        assertEquals(
            UltraWakeRecognitionDisposition.SUPPRESS,
            UltraWakeBargeInPolicy.decide(
                playbackActive = true,
                transcript = "ruido del juego"
            )
        )
    }

    @Test
    fun normalWakeCommandIsAcceptedWhenTtsIsIdle() {
        assertEquals(
            UltraWakeRecognitionDisposition.ACCEPT,
            UltraWakeBargeInPolicy.decide(
                playbackActive = false,
                transcript = "Ultra, dime la hora"
            )
        )
    }

    @Test
    fun speechGenerationExposesActiveTokenForSafeInterruption() {
        val generation = UltraWakeSpeechGeneration()
        val token = generation.begin()

        assertEquals(token, generation.activeToken())
    }

    @Test
    fun explicitGameLaunchKeepsPriorityOverGeneralResearch() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, abre Resident Evil"
        )

        assertIs<UltraAgentRoute.Command>(route)
    }

    @Test
    fun voiceFollowUpPreservesComparisonEntitiesForResearch() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, y cuál tiene mejor batería?",
            conversationHistory = listOf(
                "Tú: Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra",
                "Ultra: Estoy comparándolos."
            )
        )

        val chat = assertIs<UltraAgentRoute.Chat>(route)
        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, chat.query?.kind)
        assertTrue(chat.query?.originalText.orEmpty().contains("RedMagic 11S Pro"))
        assertTrue(chat.query?.originalText.orEmpty().contains("Galaxy S26 Ultra"))
    }
}
