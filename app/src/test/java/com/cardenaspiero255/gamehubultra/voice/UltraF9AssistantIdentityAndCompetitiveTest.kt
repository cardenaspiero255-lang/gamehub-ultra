package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraGeneralAssistant
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingIntentParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Physical reports: competitive mode routed as game; custom wake name must persist safely. */
class UltraF9AssistantIdentityAndCompetitiveTest {
    @Test
    fun competitiveActivationMustReachNetworkUtilityBeforeGameLaunch() {
        val transcript = "Ultra, activa modo competitivo"
        val contextual = UltraContextualVoiceResolver.resolve(
            transcript = transcript,
            context = UltraVoiceContext()
        )
        val route = UltraWakeRoutePrecedencePolicy.route(
            transcript = transcript,
            personalizedCommand = null,
            contextualCommand = contextual,
            fallback = { com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter.route(transcript) }
        )
        val utility = assertIs<UltraAgentRoute.Utility>(route)
        assertTrue(utility.answer.intent is com.cardenaspiero255.gamehubultra.ai.UltraUtilityIntent.NetworkGamingControl)
    }

    @Test
    fun negativeAndHypotheticalCompetitivePhrasesNeverActivate() {
        for (transcript in listOf(
            "Ultra, no actives modo competitivo",
            "Ultra, que pasaria si activara modo competitivo",
            "Ultra, hablame del modo competitivo"
        )) {
            assertFalse(UltraCompetitiveVoiceCommandPolicy.matches(transcript), transcript)
        }
    }

    @Test
    fun explicitGameTitleThatMentionsCompetitiveIsNotNetworkCommand() {
        assertFalse(UltraCompetitiveVoiceCommandPolicy.matches("Ultra, abre Modo Competitivo"))
        assertFalse(UltraCompetitiveVoiceCommandPolicy.matches("Ultra, activa el juego competitivo"))
        assertTrue(UltraCompetitiveVoiceCommandPolicy.matches("Ultra, activa el modo competitivo"))
    }

    @Test
    fun personalNameRecognizesOnlyAtInvocationPrefix() {
        assertEquals("ultra abre bs", UltraAssistantIdentityPolicy.canonicalCommand("Roberto, abre BS", "roberto"))
        assertEquals("ultra activa modo competitivo", UltraAssistantIdentityPolicy.canonicalCommand("ROBERTO, activa modo competitivo", "roberto"))
        assertEquals("ultra abre bs", UltraAssistantIdentityPolicy.canonicalCommand("Ultra, abre BS", "roberto"))
        assertNull(UltraAssistantIdentityPolicy.canonicalCommand("dime si Roberto abre BS", "roberto"))
        assertNull(UltraAssistantIdentityPolicy.canonicalCommand("Al Roberto que abre BS", "roberto"))
        assertNull(UltraAssistantIdentityPolicy.canonicalCommand("Mario, abre BS", "roberto"))
    }

    @Test
    fun explicitRenamingAndResetWorkWithDefaultAndPersonalWake() {
        assertEquals("roberto", UltraAssistantIdentityPolicy.requestedName(
            "Ultra, desde ahora te llamaras Roberto", "ultra"
        ))
        assertEquals("marcos", UltraAssistantIdentityPolicy.requestedName(
            "Roberto, desde ahora te llamaras Marcos", "roberto"
        ))
        assertEquals("ultra", UltraAssistantIdentityPolicy.requestedName(
            "Roberto, vuelve a llamarte Ultra", "roberto"
        ))
        assertEquals("ultra", UltraAssistantIdentityPolicy.requestedName(
            "Ultra, vuelve a llamarte Ultra", "roberto"
        ))
    }

    @Test
    fun mentionsOrUnsafeNamesCannotChangeWakeName() {
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, sabes quien es Roberto", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, como se llama Roberto", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, no te llames Roberto", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, desde ahora te llamaras abre", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, desde ahora te llamaras X4", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, desde ahora te llamaras a", "ultra"))
        assertNull(UltraAssistantIdentityPolicy.requestedName("Ultra, desde ahora te llamaras ${"z".repeat(50)}", "ultra"))
    }

    @Test
    fun renamedWakeAlsoSupportsStopWithoutAnyOtherActions() {
        val stop = UltraAssistantIdentityPolicy.canonicalCommand("Roberto, para", "roberto")
        assertEquals("ultra para", stop)
        assertTrue(UltraWakeStopSpeakingIntent.matches(stop!!))
        val nonStop = UltraAssistantIdentityPolicy.canonicalCommand("Roberto, para que sirve X4", "roberto")
        assertFalse(UltraWakeStopSpeakingIntent.matches(nonStop!!))
    }
}
