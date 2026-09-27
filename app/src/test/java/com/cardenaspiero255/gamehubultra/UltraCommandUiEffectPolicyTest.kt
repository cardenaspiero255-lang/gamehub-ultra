package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraCommandUiEffectPolicyTest {

    @Test
    fun profileSelectionAppliesThroughCurrentGameCallback() {
        val result = VoiceActionResult.ProfileSelected(
            profile = PerformanceProfile.X4,
            deferred = false
        )

        assertEquals(
            PerformanceProfile.X4,
            UltraCommandUiEffectPolicy.profileForCurrentGameCallback(result)
        )
    }

    @Test
    fun openedGameDoesNotReapplyItsProfileThroughCurrentGameCallback() {
        val result = VoiceActionResult.GameOpened(
            game = GameInfo("minecraft", "Minecraft"),
            profile = PerformanceProfile.X4,
            profileUnavailable = false,
            profileDeferred = false
        )

        assertNull(UltraCommandUiEffectPolicy.profileForCurrentGameCallback(result))
    }
}
