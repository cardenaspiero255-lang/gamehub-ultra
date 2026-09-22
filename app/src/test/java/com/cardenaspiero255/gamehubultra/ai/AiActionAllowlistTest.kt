package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiActionAllowlistTest {
    @Test
    fun acceptsOnlyKnownProfileActions() {
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_BALANCED)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_INTERPOLATION)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.DEVICE_STATUS)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.HELP)))
        assertTrue(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.ADVICE)))
    }

    @Test
    fun rejectsShellUrlsAndGenericIntents() {
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate("SHELL")))
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate("EXECUTE_SHELL", "rm -rf /")))
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate("OPEN_URL", "https://example.com")))
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate("GENERIC_INTENT", "intent://settings")))
        assertFalse(AiActionAllowlist.validate(LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4, "extra")))
    }

    @Test
    fun openGameArgumentIsRestrictedToSafeText() {
        assertTrue(
            AiActionAllowlist.validate(
                LocalAiActionCandidate(
                    AiActionAllowlist.OPEN_INSTALLED_GAME,
                    "Resident Evil 4 Remake"
                )
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate(
                    AiActionAllowlist.OPEN_INSTALLED_GAME,
                    "https://example.com"
                )
            )
        )
        assertFalse(
            AiActionAllowlist.validate(
                LocalAiActionCandidate(
                    AiActionAllowlist.OPEN_INSTALLED_GAME,
                    "com.example.app;drop table"
                )
            )
        )
    }
}
