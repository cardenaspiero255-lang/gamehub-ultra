package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.AdaptiveTrendSample
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun <T> runAdaptiveSessionProcessing(
    process: suspend () -> T,
    onError: (Throwable) -> Unit
): T? =
    try {
        process()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        onError(error)
        null
    }

internal fun applyCompletedAdaptiveDecision(
    completed: SessionCoachStoredSession?,
    activeProfile: PerformanceProfile,
    optimizer: PerGameAdaptiveOptimizer,
    nowMillis: Long,
    applyProfile: (String, PerformanceProfile) -> Unit,
    recordPerformanceEvent: (PerformanceEvent) -> Unit
): PerGameAdaptiveDecision? {
    val session = completed ?: return null
    val evaluated = evaluateCompletedAdaptiveDecision(
        session = session,
        activeProfile = activeProfile,
        optimizer = optimizer,
        nowMillis = nowMillis
    ) ?: return null

    if (evaluated.decision.changed) {
        applyProfile(evaluated.packageName, evaluated.decision.profile)
        recordPerformanceEvent(
            adaptivePerformanceEvent(
                session = session,
                decision = evaluated.decision,
                nowMillis = nowMillis
            )
        )
    }
    return evaluated.decision
}

internal suspend fun processCompletedAdaptiveSession(
    completed: SessionCoachStoredSession?,
    optimizer: PerGameAdaptiveOptimizer,
    nowMillis: Long,
    wasSessionHandled: () -> Boolean,
    resolveActiveProfile: suspend () -> PerformanceProfile,
    persistProfile: suspend (String, PerformanceProfile) -> Unit,
    markSessionHandled: () -> Unit,
    recordPerformanceEvent: suspend (PerformanceEvent) -> Unit
): PerGameAdaptiveDecision? {
    val session = completed ?: return null
    if (wasSessionHandled()) return null

    val key = adaptiveGameKey(session) ?: run {
        markSessionHandled()
        return null
    }
    val snapshot = optimizer.snapshotState(key)
    val activeProfile = resolveActiveProfile()
    val evaluated = evaluateCompletedAdaptiveDecision(
        session = session,
        activeProfile = activeProfile,
        optimizer = optimizer,
        nowMillis = nowMillis
    )

    if (evaluated == null) {
        markSessionHandled()
        return null
    }

    if (evaluated.decision.changed) {
        var profilePersisted = false
        try {
            persistProfile(evaluated.packageName, evaluated.decision.profile)
            profilePersisted = true
            recordPerformanceEvent(
                adaptivePerformanceEvent(
                    session = session,
                    decision = evaluated.decision,
                    nowMillis = nowMillis
                )
            )
        } catch (error: Throwable) {
            if (profilePersisted) {
                try {
                    withContext(NonCancellable) {
                        persistProfile(evaluated.packageName, activeProfile)
                    }
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
            }
            optimizer.restoreState(key, snapshot)
            throw error
        }
    }

    markSessionHandled()
    return evaluated.decision
}

private data class EvaluatedAdaptiveSession(
    val packageName: String,
    val decision: PerGameAdaptiveDecision
)

private fun evaluateCompletedAdaptiveDecision(
    session: SessionCoachStoredSession,
    activeProfile: PerformanceProfile,
    optimizer: PerGameAdaptiveOptimizer,
    nowMillis: Long
): EvaluatedAdaptiveSession? {
    val key = adaptiveGameKey(session) ?: return null
    if (session.samples.isEmpty()) return null

    val decision = optimizer.evaluate(
        key = key,
        activeProfile = activeProfile,
        samples = session.samples.map { sample ->
            AdaptiveTrendSample(
                thermalStatus = sample.thermalStatus,
                batteryPercent = sample.batteryPercent,
                refreshRateHz = sample.refreshRateHz,
                memoryUsedPercent = sample.memoryUsedPercent,
                latencyMs = sample.latencyMs
                    ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                    ?.toInt()
            )
        },
        nowMillis = nowMillis
    )
    return EvaluatedAdaptiveSession(
        packageName = key.packageName,
        decision = decision
    )
}

private fun adaptiveGameKey(session: SessionCoachStoredSession): AdaptiveGameKey? {
    val packageName = session.packageName.trim().takeIf(String::isNotEmpty) ?: return null
    val version = session.gameVersion
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: "unknown"
    return AdaptiveGameKey(packageName, version)
}

private fun adaptivePerformanceEvent(
    session: SessionCoachStoredSession,
    decision: PerGameAdaptiveDecision,
    nowMillis: Long
): PerformanceEvent =
    PerformanceEvent(
        timestampMillis = nowMillis,
        type = PerformanceEventType.POLICY_CHANGED,
        sessionId = session.sessionId,
        profile = decision.profile,
        detail = decision.reason
    )
