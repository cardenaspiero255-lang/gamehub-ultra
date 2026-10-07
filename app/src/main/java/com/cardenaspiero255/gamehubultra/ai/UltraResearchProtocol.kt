package com.cardenaspiero255.gamehubultra.ai

/**
 * Wire-protocol contract between the Android client and Ultra Research.
 *
 * Production requires an exact match so a newer app cannot silently keep
 * talking to an older research engine with weaker routing/fallback behavior.
 */
object UltraResearchProtocol {
    const val ENGINE_VERSION: String = "20"
}
