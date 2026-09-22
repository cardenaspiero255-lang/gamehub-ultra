package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiActionAllowlistTest {
    @Test
    fun acceptsOnlySupportedAdvisorActions() {
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_BALANCED)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_INTERPOLATION)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.ADVICE)))
    }

    @Test
    fun rejectsShellUrlsGenericIntentsAndUnsupportedFlows() {
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate("SHELL")))
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("EXECUTE_SHELL", "rm -rf /")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("OPEN_URL", "https://example.com")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("GENERIC_INTENT", "intent://settings")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("OPEN_INSTALLED_GAME", "Resident Evil 4 Remake")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("DEVICE_STATUS")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate("HELP")
            )
        )
    }

    @Test
    fun profileAndAdviceActionsCannotCarryArguments() {
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4, "extra")
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate(AiActionAllowlist.ADVICE, "open settings")
            )
        )
    }
}
