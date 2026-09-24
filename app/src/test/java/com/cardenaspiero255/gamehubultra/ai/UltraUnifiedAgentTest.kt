package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UltraUnifiedAgentTest {
    @Test
    fun knownVoiceCommandStaysOnSafeActionPath() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, pon modo equilibrado",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.SelectProfile>(commandRoute.command)
        assertEquals(PerformanceProfile.BALANCED, command.profile)
    }

    @Test
    fun freeFormSpeechFallsBackToConversationalAi() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, cuentame algo sobre mi juego",
            optionalResolver = null
        )

        val chatRoute = assertIs<UltraAgentRoute.Chat>(route)
        assertEquals("Ultra, cuentame algo sobre mi juego", chatRoute.message)
    }

    @Test
    fun sharedConversationKeepsRecentTurnsAcrossTextAndVoice() {
        var history = emptyList<String>()

        history = UltraConversationPolicy.append(history, "Tú: hola", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Ultra: hola", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Tú: abre RE4", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Ultra: abriendo RE4", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Tú: ¿y la temperatura?", maxEntries = 4)

        assertEquals(
            listOf(
                "Ultra: hola",
                "Tú: abre RE4",
                "Ultra: abriendo RE4",
                "Tú: ¿y la temperatura?"
            ),
            history
        )
    }

    @Test
    fun blankConversationEntriesAreIgnored() {
        assertEquals(
            listOf("Ultra: listo"),
            UltraConversationPolicy.append(
                history = listOf("Ultra: listo"),
                entry = "   ",
                maxEntries = 8
            )
        )
    }
}
