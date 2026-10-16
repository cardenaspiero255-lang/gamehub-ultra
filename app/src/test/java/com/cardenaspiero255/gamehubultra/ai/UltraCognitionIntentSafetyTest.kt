package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 3 Cognition Engine acceptance matrix:
 * requests ABOUT a command must not execute the command. Both typed and
 * spoken input pass through the same semantic safety boundary.
 */
class UltraCognitionIntentSafetyTest {
    private val nonExecutable = listOf(
        "Ultra, no actives X4",
        "Ultra, nunca abras Minecraft",
        "Ultra, no me pongas X4",
        "Ultra, qué pasaría si activara X4?",
        "Ultra, ¿cómo activar X4?",
        "Ultra, ¿debería activar X4?",
        "Ultra, ¿qué significa el comando pon X4?",
        "Ultra, what happens if I enable X4?",
        "Ultra, how do I enable X4?",
        "Ultra, do not activate X4",
        "Ultra, no quiero que actives X4",
        "Ultra, prefiero que no actives X4",
        "Ultra, no me gustaria que abras Minecraft",
        "Ultra, evita que se active X4",
        "Ultra, no deberias activar X4",
        "Ultra, por favor no actives X4",
        "Ultra, quiero saber como activar X4",
        "Ultra, puedes explicar como activar X4",
        "Ultra, i don't want you to enable X4",
        "Ultra, please don't open Minecraft"
    )

    @Test
    fun questionsNegationsAndHypotheticalsNeverBecomeActionCommands() {
        nonExecutable.forEach { utterance ->
            val parsed = VoiceCommandParser.parse(utterance)
            assertIs<VoiceCommand.Unknown>(parsed, "Parser executed discussion: $utterance")

            val spoken = UltraUnifiedAgentRouter.route(utterance)
            assertIs<UltraAgentRoute.Chat>(spoken, "Voice executed discussion: $utterance")

            val typed = UltraTypedChatRoutePlanner.route(utterance)
            assertIs<UltraAgentRoute.Chat>(typed, "Typed input executed discussion: $utterance")
        }
    }

    @Test
    fun realExplicitCommandsRemainActionsAndAreNotOverblocked() {
        val launch = UltraUnifiedAgentRouter.route("Ultra, abre Minecraft")
        val profile = UltraUnifiedAgentRouter.route("Ultra, pon X4")
        val typed = UltraTypedChatRoutePlanner.route("pon X4")
        assertIs<UltraAgentRoute.Command>(launch)
        assertIs<UltraAgentRoute.Command>(profile)
        assertIs<UltraAgentRoute.Command>(typed)
    }

    @Test
    fun vsCodeIsAProductNameNotAutomaticallyAComparison() {
        val question = UltraGeneralQueryRouter.classify("Ultra, ¿qué es VS Code?")
        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, question.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, question.verificationMode)
        assertFalse(question.requiresFreshData)

        val chat = assertIs<UltraAgentRoute.Chat>(
            UltraUnifiedAgentRouter.route("Ultra, ¿qué es VS Code?")
        )
        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, chat.query?.kind)
    }

    @Test
    fun actualComparisonsAndCurrentPriceStayVerified() {
        val comparison = UltraGeneralQueryRouter.classify(
            "Ultra, compara VS Code con Android Studio"
        )
        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, comparison.kind)
        assertEquals(UltraVerificationMode.REQUIRED, comparison.verificationMode)

        val versus = UltraGeneralQueryRouter.classify("VS Code vs Android Studio")
        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, versus.kind)

        val current = UltraGeneralQueryRouter.classify("Ultra, precio actual de VS Code")
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, current.kind)
        assertEquals(UltraVerificationMode.REQUIRED, current.verificationMode)
        assertTrue(current.requiresFreshData)
    }
}
