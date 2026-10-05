package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraSssQualityContractTest {
    @Test
    fun `stable knowledge definitions stay optional and non fresh`() {
        listOf(
            "¿Qué es un exoplaneta?",
            "¿Qué es un macroverso?"
        ).forEach { query ->
            val request = UltraGeneralQueryRouter.classify(query)

            assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
            assertFalse(request.requiresFreshData)
        }
    }

    @Test
    fun `complete new subject does not inherit previous conversation topic`() {
        val resolved = UltraConversationContextResolver.resolve(
            message = "¿Qué es un exoplaneta?",
            conversation = listOf("Tú: Háblame de los osos")
        )

        assertEquals("¿Qué es un exoplaneta?", resolved)
    }

    @Test
    fun `ambiguous comparative follow up keeps the previous topic`() {
        val resolved = UltraConversationContextResolver.resolve(
            message = "¿Cuál es el más grande?",
            conversation = listOf("Tú: Háblame de los osos")
        )

        assertTrue(resolved.contains("osos", ignoreCase = true))
        assertTrue(resolved.contains("más grande", ignoreCase = true))
    }

    @Test
    fun `gaming voice commands preserve launch profile and network intents`() {
        val launch = VoiceCommandParser.parse("Ultra abre RE4")
        assertTrue(launch is VoiceCommand.OpenGame)
        assertEquals("re4", (launch as VoiceCommand.OpenGame).query)

        val x4 = VoiceCommandParser.parse("Ultra activa X4")
        assertEquals(VoiceCommand.SelectProfile(PerformanceProfile.X4), x4)

        val network = VoiceCommandParser.parse("Ultra optimiza mi internet")
        assertTrue(network is VoiceCommand.Network)
    }

    @Test
    fun `unsafe shell shaped voice input stays blocked`() {
        val command = VoiceCommandParser.parse("Ultra adb shell settings put global animator 0")

        assertTrue(command is VoiceCommand.Unknown)
    }
}
