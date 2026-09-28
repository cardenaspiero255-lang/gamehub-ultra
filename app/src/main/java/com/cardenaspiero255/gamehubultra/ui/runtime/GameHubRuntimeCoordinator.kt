package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.SessionFinishHandle
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class RuntimeSessionMetrics(
    val batteryPercent: Int? = null,
    val thermalStatus: Int? = null,
    val ramUsedPercent: Int? = null,
    val diagnosticsAvailable: Boolean = false
)

internal data class GameHubRuntimeSnapshot(
    val selectedGamePackage: String?,
    val effectiveProfile: PerformanceProfile,
    val activeSessionId: String?,
    val metrics: RuntimeSessionMetrics,
    val optimizationContextKey: OptimizationContextKey
)

internal interface GameHubRuntimeActions {
    fun selectGlobalProfile(profile: PerformanceProfile)
    fun selectGameProfile(packageName: String, profile: PerformanceProfile)
    fun applySmartGameAssistantSuggestion(
        packageName: String,
        suggestion: SmartGameAssistantSuggestion
    )

    fun finishRuntimeGameSession(metrics: SessionEndMetrics): SessionFinishHandle?
    fun beginRuntimeGameSession(record: GameSessionRecord)
    fun recordPerformanceEvent(event: PerformanceEvent)
    fun recordRecentGame(packageName: String)
    fun selectGame(packageName: String)
}

internal class GameHubRuntimeCoordinator(
    private val scope: CoroutineScope,
    private val actions: GameHubRuntimeActions,
    private val recordOptimization: suspend (
        OptimizationContextKey,
        OptimizationObservation
    ) -> Unit,
    private val optimizationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun selectProfile(
        snapshot: GameHubRuntimeSnapshot,
        profile: PerformanceProfile
    ) {
        snapshot.selectedGamePackage?.let { packageName ->
            actions.selectGameProfile(packageName, profile)
        } ?: actions.selectGlobalProfile(profile)

        if (snapshot.effectiveProfile != profile) {
            actions.recordPerformanceEvent(
                PerformanceEvent(
                    timestampMillis = nowMillis(),
                    type = PerformanceEventType.POLICY_CHANGED,
                    sessionId = snapshot.activeSessionId ?: "ui",
                    profile = profile,
                    detail = "manual_profile_selection"
                )
            )
        }
    }

    fun applySmartGameAssistantSuggestion(
        snapshot: GameHubRuntimeSnapshot,
        suggestion: SmartGameAssistantSuggestion
    ) {
        snapshot.selectedGamePackage?.let { packageName ->
            actions.applySmartGameAssistantSuggestion(packageName, suggestion)
        } ?: actions.selectGlobalProfile(suggestion.profile)
    }

    fun endGameSession(
        snapshot: GameHubRuntimeSnapshot
    ): SessionFinishHandle? {
        val endedAt = nowMillis()
        val finishHandle = actions.finishRuntimeGameSession(
            SessionEndMetrics(
                endedAtMillis = endedAt,
                endBatteryPercent = snapshot.metrics.batteryPercent,
                endThermalStatus = snapshot.metrics.thermalStatus,
                endRamUsedPercent = snapshot.metrics.ramUsedPercent
            )
        ) ?: return null

        actions.recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = endedAt,
                type = PerformanceEventType.SESSION_ENDED,
                sessionId = finishHandle.session.id,
                detail = finishHandle.session.packageName
            )
        )

        scope.launch {
            finishHandle.job.join()
            if (!finishHandle.job.isCancelled) {
                withContext(optimizationDispatcher) {
                    val thermalStatus = snapshot.metrics.thermalStatus
                    val highTemperature =
                        thermalStatus != null && thermalStatus >= 4
                    recordOptimization(
                        snapshot.optimizationContextKey,
                        OptimizationObservation(
                            contextKey = snapshot.optimizationContextKey.serialized,
                            profile = snapshot.effectiveProfile,
                            measuredFps = null,
                            stable = snapshot.metrics.diagnosticsAvailable &&
                                !highTemperature &&
                                (thermalStatus == null || thermalStatus <= 2),
                            failed = highTemperature,
                            highTemperature = highTemperature,
                            thermalStatus = thermalStatus,
                            batteryPercent = snapshot.metrics.batteryPercent,
                            errorReason =
                                if (highTemperature) "thermal_pressure" else null,
                            timestampMillis = endedAt
                        )
                    )
                }
            }
        }

        return finishHandle
    }

    fun selectGame(
        snapshot: GameHubRuntimeSnapshot,
        packageName: String
    ) {
        endGameSession(snapshot)
        actions.selectGame(packageName)
    }

    fun recordGameOpened(
        snapshot: GameHubRuntimeSnapshot,
        packageName: String
    ) {
        endGameSession(snapshot)

        val sessionId = sessionIdFactory()
        val startedAt = nowMillis()
        actions.beginRuntimeGameSession(
            GameSessionRecord(
                id = sessionId,
                packageName = packageName,
                profileName = snapshot.effectiveProfile.name,
                startedAtMillis = startedAt,
                startBatteryPercent = snapshot.metrics.batteryPercent
            )
        )
        actions.recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = startedAt,
                type = PerformanceEventType.SESSION_STARTED,
                sessionId = sessionId,
                detail = packageName
            )
        )
        actions.recordRecentGame(packageName)
    }
}
