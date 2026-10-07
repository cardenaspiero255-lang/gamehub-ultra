package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.AdaptiveTrendSample
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

internal fun applyCompletedAdaptiveDecision(
    completed: SessionCoachStoredSession?,
    activeProfile: PerformanceProfile,
    optimizer: PerGameAdaptiveOptimizer,
    nowMillis: Long,
    applyProfile: (String, PerformanceProfile) -> Unit,
    recordPerformanceEvent: (PerformanceEvent) -> Unit
): PerGameAdaptiveDecision? {
    val session = completed ?: return null
    val sessionPackage = session.packageName
        .trim()
        .takeIf(String::isNotEmpty)
        ?: return null
    if (session.samples.isEmpty()) return null

    val key = AdaptiveGameKey(
        packageName = sessionPackage,
        version = session.gameVersion
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "unknown"
    )
    val samples = session.samples.map { sample ->
        AdaptiveTrendSample(
            thermalStatus = sample.thermalStatus,
            batteryPercent = sample.batteryPercent,
            refreshRateHz = sample.refreshRateHz,
            memoryUsedPercent = sample.memoryUsedPercent,
            latencyMs = sample.latencyMs
                ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                ?.toInt()
        )
    }
    val decision = optimizer.evaluate(
        key = key,
        activeProfile = activeProfile,
        samples = samples,
        nowMillis = nowMillis
    )

    if (decision.changed) {
        applyProfile(sessionPackage, decision.profile)
        recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = nowMillis,
                type = PerformanceEventType.POLICY_CHANGED,
                sessionId = session.sessionId,
                profile = decision.profile,
                detail = decision.reason
            )
        )
    }
    return decision
}
