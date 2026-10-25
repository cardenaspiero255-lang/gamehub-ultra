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

    @Test fun reservedCompetitiveCommandBeatsPersistedGameAlias() {
        val result = VoiceCommandParser.parse(
            "Ultra activa modo competitivo",
            knownGameAliases = setOf("activa modo competitivo"),
            optionalResolver = object : NaturalLanguageIntentResolver {
                override fun resolve(transcript: String): VoiceCommand =
                    VoiceCommand.OpenGame("inventado")
            }
        )
        assertEquals(VoiceCommand.Network(NetworkVoiceRequest.COMPETITIVE), result)
    }

    @Test fun hypotheticalAndNegatedCompetitiveNeverExecute() {
        listOf("Ultra no actives modo competitivo", "Ultra que pasaria si activara modo competitivo",
            "Ultra activa modo competitivo para jugar a algo")
            .forEach { phrase ->
                assertFalse(VoiceCommandParser.parse(phrase) is VoiceCommand.Network, phrase)
            }
    }


    @Test fun competitiveExecutesRequestedProfileInsteadOfRecommendedProfile() {
        val snapshot = VoiceNetworkSnapshot(
            metrics = NetworkMetrics(
                averageLatencyMs = 40.0,
                jitterMs = 5.0,
                packetLossPercent = 0.0,
                spikeCount = 0,
                stability = NetworkStability.GOOD
            ),
            recommendedProfile = NetworkGameProfile.BALANCED,
            metered = false
        )
        var applied: NetworkGameProfile? = null
        val result = VoiceCommandEngine.execute(
            command = VoiceCommandParser.parse("Ultra activa modo competitivo"),
            gamesProvider = { error("Network command must not enumerate games") },
            launchGame = { error("Never launch a game for competitive network mode") },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(50, "normal") },
            networkStatusProvider = { snapshot },
            applyNetworkProfile = {
                applied = it
                NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE
            }
        )
        assertEquals(NetworkGameProfile.COMPETITIVE, applied)
        val report = assertIs<VoiceActionResult.NetworkReport>(result)
        assertFalse(report.optimizationApplied)
        assertTrue(NetworkVoiceResponseText.format(report).contains("PREPARADO"))
    }

    @Test fun namedAssistantIsUltraByDefaultAndCanBeRenamed() {
        assertEquals("ultra", UltraWakeNamePolicy.validate("Ultra"))
        assertEquals("roberto", UltraWakeNamePolicy.requestedName("Ultra desde ahora te llamaras Roberto"))
        assertEquals("roberto", UltraWakeNamePolicy.requestedName("Ultra quiero que te llames Roberto"))
        assertEquals("ultra", UltraWakeNamePolicy.requestedName(
            UltraWakeNamePolicy.canonicalizeInvocation("Roberto desde ahora te llamaras Ultra", "roberto")
        ))
        assertNull(UltraWakeNamePolicy.requestedName("Ultra, que pasaria si te llamaras Roberto"))
        assertNull(UltraWakeNamePolicy.requestedName("Ultra, te llamaras Roberto?"))
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
