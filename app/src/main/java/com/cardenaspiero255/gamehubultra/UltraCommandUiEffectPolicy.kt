package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult

/**
 * Keeps command-result UI callbacks from duplicating persistence already
 * performed by VoiceCommandEngine.
 */
internal object UltraCommandUiEffectPolicy {
    fun profileForCurrentGameCallback(
        result: VoiceActionResult
    ): PerformanceProfile? =
        when (result) {
            is VoiceActionResult.ProfileSelected -> result.profile
            else -> null
        }
}
