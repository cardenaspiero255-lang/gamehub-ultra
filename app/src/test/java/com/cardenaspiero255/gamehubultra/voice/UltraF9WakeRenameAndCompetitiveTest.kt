package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraF9WakeRenameAndCompetitiveTest {
    @Test fun competitiveIsNetworkCommandNotGameLaunch() {
        listOf("Ultra activa modo competitivo", "activa modo competitivo", "Ultra pon el modo competitivo")
            .forEach { phrase ->
                assertEquals(VoiceCommand.Network(NetworkVoiceRequest.COMPETITIVE),
                    VoiceCommandParser.parse(phrase), phrase)
            }
    }

    @Test fun hypotheticalAndNegatedCompetitiveNeverExecute() {
        listOf("Ultra no actives modo competitivo", "Ultra que pasaria si activara modo competitivo",
            "Ultra activa modo competitivo para jugar a algo")
            .forEach { phrase ->
                assertFalse(VoiceCommandParser.parse(phrase) is VoiceCommand.Network, phrase)
            }
    }

    @Test fun namedAssistantIsUltraByDefaultAndCanBeRenamed() {
        assertEquals("ultra", UltraWakeNamePolicy.validate("Ultra"))
        assertEquals("roberto", UltraWakeNamePolicy.requestedName("Ultra desde ahora te llamaras Roberto"))
        assertEquals("roberto", UltraWakeNamePolicy.requestedName("Ultra quiero que te llames Roberto"))
        assertEquals("ultra", UltraWakeNamePolicy.requestedName("Roberto desde ahora te llamaras ultra"))
        assertNull(UltraWakeNamePolicy.requestedName("Ultra, que pasaria si te llamaras Roberto"))
        assertNull(UltraWakeNamePolicy.requestedName("No quiero que te llames Roberto"))
    }

    @Test fun renamingRejectsUnsafeNamesWithoutChangingState() {
        listOf("para", "stop", "activa", "abre", "x4", "modo", "no", "a", "roberto y abre bs", "abcdefghijklmnopqrstu")
            .forEach { invalid -> assertNull(UltraWakeNamePolicy.validate(invalid), invalid) }
    }

    @Test fun customWakeNameIsAnchoredAndCanonicalizesExistingCommands() {
        assertEquals("ultra abre bs",
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto, abre BS", "roberto"))
        assertEquals("ultra para",
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto, para", "roberto"))
        assertEquals("ultra activa modo competitivo",
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto activa modo competitivo", "roberto"))
        assertTrue(UltraWakeNamePolicy.isInvoked("roberto abre bs", "roberto"))
        assertTrue(UltraWakeNamePolicy.isInvoked("Ultra abre bs", "roberto"))
        assertFalse(UltraWakeNamePolicy.isInvoked("mi amigo roberto abre bs", "roberto"))
        assertFalse(UltraWakeNamePolicy.isInvoked("robert abre bs", "roberto"))
        assertFalse(UltraWakeNamePolicy.isInvoked("roberto", "roberto"))
        assertEquals("mi amigo roberto abre bs",
            UltraWakeNamePolicy.canonicalizeInvocation("mi amigo roberto abre bs", "roberto"))
    }

    @Test fun renamedInvocationStillUsesExistingBrawlStarsAliasAndStopPolicy() {
        val gameCommand = VoiceCommandParser.parse(
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto abre BS", "roberto"))
        assertEquals(VoiceCommand.OpenGame("bs"), gameCommand)
        assertTrue(UltraWakeStopSpeakingIntent.matches(
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto para", "roberto")))
        assertFalse(UltraWakeStopSpeakingIntent.matches(
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto para que sirve X4", "roberto")))
    }
}
