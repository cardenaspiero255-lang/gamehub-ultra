package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** F9 user-reported continuous-listening command routing and assistant-name acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9CustomNameAndCompetitiveRegressionTest {
    @Test
    fun continuousModeCompetitiveIntentIsNotInterpretedAsAGame() {
        val actual = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, activa modo competitivo",
            personalizedCommand = null,
            contextualCommand = VoiceCommand.OpenGame("modo competitivo"),
            fallback = { UltraUnifiedAgentRouter.route("Ultra, activa modo competitivo") }
        )
        assertIs<UltraAgentRoute.Utility>(actual)
    }

    @Test
    fun explicitlyLaunchingAGameNamedCompetitiveModeStaysAGame() {
        val command = VoiceCommand.OpenGame("modo competitivo")
        val actual = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, abre modo competitivo",
            personalizedCommand = null,
            contextualCommand = command,
            fallback = { error("Game launch must retain priority") }
        )
        assertEquals(command, assertIs<UltraAgentRoute.Command>(actual).command)
    }

    @Test
    fun negativeCompetitiveUtteranceCannotActivateOptimizer() {
        val actual = UltraWakeRoutePrecedencePolicy.route(
            transcript = "Ultra, no actives modo competitivo",
            personalizedCommand = null,
            contextualCommand = VoiceCommand.OpenGame("modo competitivo"),
            fallback = { UltraUnifiedAgentRouter.route("Ultra, no actives modo competitivo") }
        )
        assertIs<UltraAgentRoute.Chat>(actual)
    }

    @Test
    fun nameDefaultsToUltraAndPersistsAfterRepositoryRecreation() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ultra_assistant_identity", 0).edit().clear().commit()
        assertEquals("ultra", UltraAssistantNamePreferences(context).currentName())
        assertTrue(UltraAssistantNamePreferences(context).setName("Roberto"))
        assertEquals("roberto", UltraAssistantNamePreferences(context).currentName())
        assertTrue(UltraAssistantNamePreferences(context).setName("Ultra"))
        assertEquals("ultra", UltraAssistantNamePreferences(context).currentName())
    }

    @Test
    fun explicitRenameIsRecognizedOnlyAfterCorrectInvocation() {
        assertEquals("roberto", UltraAssistantNamePolicy.parseRequest(
            "Ultra, desde ahora te llamarás Roberto", "ultra"
        ))
        assertEquals("alfred", UltraAssistantNamePolicy.parseRequest(
            "Roberto, desde ahora te llamarás Alfred", "roberto"
        ))
        assertEquals("ultra", UltraAssistantNamePolicy.parseRequest(
            "Roberto, vuelve a llamarte Ultra", "roberto"
        ))
        assertEquals(null, UltraAssistantNamePolicy.parseRequest(
            "Mi amigo dijo que Ultra desde ahora te llamarás Roberto", "ultra"
        ))
    }

    @Test
    fun renamedWakeWordRoutesThroughTheExistingGameParser() {
        val normalized = UltraAssistantNamePolicy.canonicalize(
            "Roberto, abre BS", "roberto"
        )
        assertEquals("ultra abre bs", normalized)
        assertEquals(VoiceCommand.OpenGame("bs"),
            VoiceCommandParser.parse(normalized))
        assertTrue(UltraWakeWordMatcher.contains(
            "Roberto abre BS", assistantName = "roberto"
        ))
        assertFalse(UltraWakeWordMatcher.contains(
            "Estamos hablando de Roberto y un juego", assistantName = "roberto"
        ))
        assertTrue(UltraWakeWordMatcher.contains(
            "Ultra abre BS", assistantName = "roberto"
        ))
    }

    @Test
    fun renamedWakeWordStillSupportsStopDuringTts() {
        assertTrue(UltraWakePartialStopPolicy.shouldStop(
            playbackActive = true,
            alternatives = listOf("Roberto, para"),
            assistantName = "roberto"
        ))
        assertFalse(UltraWakePartialStopPolicy.shouldStop(
            playbackActive = true,
            alternatives = listOf("Roberto para qué sirve X4"),
            assistantName = "roberto"
        ))
    }

    @Test
    fun unsafeNamesAndNegatedChangesAreRejected() {
        for (name in listOf("", "activa", "para", "x4", "123", "roberto abre bs", "robotomuylargosinnombrevalido")) {
            assertFalse(UltraAssistantNamePolicy.isValid(name), name)
        }
        assertEquals(null, UltraAssistantNamePolicy.parseRequest(
            "Ultra, no cambies tu nombre a Roberto", "ultra"
        ))
        assertEquals(null, UltraAssistantNamePolicy.parseRequest(
            "Ultra, ¿qué pasaría si te llamaras Roberto?", "ultra"
        ))
    }
}
