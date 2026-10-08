package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraFrontierWorldState
import com.cardenaspiero255.gamehubultra.ai.UltraFrontierWorldStateRegistry
import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.AdaptiveTrendSample
import com.cardenaspiero255.gamehubultra.domain.BatteryAwareGamingEngine
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePendingDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngine
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
    val pending = optimizer.readPendingDecision(session.sessionId)
    if (pending != null) {
        if (wasSessionHandled()) {
            optimizer.restoreState(pending.key, pending.targetState)
            optimizer.clearPendingDecision(session.sessionId)
            return PerGameAdaptiveDecision(
                profile = pending.targetProfile,
                changed = true,
                reason = pending.reason
            )
        }
        return completePendingAdaptiveDecision(
            pending = pending,
            optimizer = optimizer,
            persistProfile = persistProfile,
            markSessionHandled = markSessionHandled,
            recordPerformanceEvent = recordPerformanceEvent
        )
    }
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
        nowMillis = nowMillis,
        persistState = false
    )

    if (evaluated == null) {
        markSessionHandled()
        return null
    }

    if (!evaluated.decision.changed) {
        optimizer.commitState(key)
        markSessionHandled()
        return evaluated.decision
    }

    val targetState = checkNotNull(optimizer.snapshotState(key))
    val pendingDecision = PerGameAdaptivePendingDecision(
        sessionId = session.sessionId,
        key = key,
        previousProfile = activeProfile,
        targetProfile = evaluated.decision.profile,
        targetState = targetState,
        eventTimestampMillis = nowMillis,
        reason = evaluated.decision.reason
    )
    optimizer.writePendingDecision(pendingDecision)

    return try {
        completePendingAdaptiveDecision(
            pending = pendingDecision,
            optimizer = optimizer,
            persistProfile = persistProfile,
            markSessionHandled = markSessionHandled,
            recordPerformanceEvent = recordPerformanceEvent
        )
    } catch (error: Throwable) {
        optimizer.restoreState(key, snapshot)
        throw error
    }
}

private suspend fun completePendingAdaptiveDecision(
    pending: PerGameAdaptivePendingDecision,
    optimizer: PerGameAdaptiveOptimizer,
    persistProfile: suspend (String, PerformanceProfile) -> Unit,
    markSessionHandled: () -> Unit,
    recordPerformanceEvent: suspend (PerformanceEvent) -> Unit
): PerGameAdaptiveDecision {
    var profilePersisted = false
    try {
        persistProfile(pending.key.packageName, pending.targetProfile)
        profilePersisted = true
        recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = pending.eventTimestampMillis,
                type = PerformanceEventType.POLICY_CHANGED,
                sessionId = pending.sessionId,
                profile = pending.targetProfile,
                detail = pending.reason
            )
        )
        optimizer.restoreState(pending.key, pending.targetState)
        markSessionHandled()
        optimizer.clearPendingDecision(pending.sessionId)
        return PerGameAdaptiveDecision(
            profile = pending.targetProfile,
            changed = true,
            reason = pending.reason
        )
    } catch (error: Throwable) {
        if (profilePersisted) {
            try {
                withContext(NonCancellable) {
                    persistProfile(pending.key.packageName, pending.previousProfile)
                }
            } catch (rollbackError: Throwable) {
                error.addSuppressed(rollbackError)
            }
        }
        throw error
    }
}

private data class EvaluatedAdaptiveSession(
    val packageName: String,
    val decision: PerGameAdaptiveDecision
)

private fun evaluateCompletedAdaptiveDecision(
    session: SessionCoachStoredSession,
    activeProfile: PerformanceProfile,
    optimizer: PerGameAdaptiveOptimizer,
    nowMillis: Long,
    persistState: Boolean = true
): EvaluatedAdaptiveSession? {
    val key = adaptiveGameKey(session) ?: return null
    if (session.samples.isEmpty()) return null

    val orderedSamples = session.samples.sortedBy { it.timestampMillis }
    val thermalPrediction = ThermalPredictionEngine().predict(orderedSamples)
    val batteryAssessment = BatteryAwareGamingEngine().assess(orderedSamples)
    val mappedSamples = orderedSamples.mapIndexed { index, sample ->
        val isLatest = index == orderedSamples.lastIndex
        AdaptiveTrendSample(
            thermalStatus = sample.thermalStatus,
            batteryPercent = sample.batteryPercent,
            refreshRateHz = sample.refreshRateHz,
            memoryUsedPercent = sample.memoryUsedPercent,
            latencyMs = sample.latencyMs
                ?.coerceIn(0L, Int.MAX_VALUE.toLong())
                ?.toInt(),
            thermalPrediction = thermalPrediction.takeIf { isLatest },
            batteryConstrained = isLatest &&
                batteryAssessment.preventAggressiveProfiles,
            batteryConstraintReason = batteryAssessment.reason
                .takeIf { isLatest && batteryAssessment.preventAggressiveProfiles },
            batteryCharging = sample.batteryCharging
        )
    }
    val decision = if (persistState) {
        optimizer.evaluate(
            key = key,
            activeProfile = activeProfile,
            samples = mappedSamples,
            nowMillis = nowMillis
        )
    } else {
        optimizer.evaluateUncommitted(
            key = key,
            activeProfile = activeProfile,
            samples = mappedSamples,
            nowMillis = nowMillis
        )
    }
    val latest = orderedSamples.last()
    UltraFrontierWorldStateRegistry.update(
        UltraFrontierWorldState(
            selectedGamePackage = key.packageName,
            sessionActive = false,
            selectedProfile = activeProfile,
            networkValidated = latest.latencyMs != null,
            networkLatencyMs = latest.latencyMs,
            batteryPercent = latest.batteryPercent,
            charging = latest.batteryCharging == true,
            thermalStatus = latest.thermalStatus,
            thermalHeadroom = latest.thermalHeadroom,
            thermalTrend = thermalPrediction.trend,
            thermalRisk = thermalPrediction.risk,
            thermalConfidence = thermalPrediction.confidence,
            batteryRecommendation = batteryAssessment.recommendation,
            preventAggressiveProfiles =
                batteryAssessment.preventAggressiveProfiles,
            adaptiveScore = null,
            timestampMillis = nowMillis
        )
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
