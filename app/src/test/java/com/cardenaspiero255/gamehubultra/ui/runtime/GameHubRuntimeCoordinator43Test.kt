package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.SessionFinishHandle
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals

class GameHubRuntimeCoordinator43Test {
    @Test
    fun `runtime records rejected and reverted recommendations in scoped memory`() {
        val recorded = mutableListOf<Pair<OptimizationContextKey, OptimizationObservation>>()
        val coordinator = GameHubRuntimeCoordinator(
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            actions = NoOpRuntimeActions,
            recordOptimization = { key, observation -> recorded += key to observation },
            optimizationDispatcher = Dispatchers.Unconfined,
            nowMillis = { 9_000L },
            sessionIdFactory = { "unused" }
        )
        val key = OptimizationContextKey("device", "game.a", "1", null, "gpu")
        val snapshot = GameHubRuntimeSnapshot(
            selectedGamePackage = "game.a",
            effectiveProfile = PerformanceProfile.BALANCED,
            activeSessionId = null,
            metrics = RuntimeSessionMetrics(diagnosticsAvailable = true),
            optimizationContextKey = key
        )

        coordinator.recordRecommendationFeedback(
            snapshot,
            PerformanceProfile.X4,
            OptimizationFeedbackDecision.REJECTED
        )
        coordinator.recordRecommendationFeedback(
            snapshot,
            PerformanceProfile.FRAME_INTERPOLATION,
            OptimizationFeedbackDecision.REVERTED
        )

        assertEquals(2, recorded.size)
        assertEquals(key, recorded[0].first)
        assertEquals(PerformanceProfile.X4, recorded[0].second.profile)
        assertEquals(
            OptimizationFeedbackDecision.REJECTED,
            recorded[0].second.feedbackDecision
        )
        assertEquals(
            OptimizationFeedbackDecision.REVERTED,
            recorded[1].second.feedbackDecision
        )
        assertEquals(listOf(9_000L, 9_000L), recorded.map { it.second.timestampMillis })
    }

    private object NoOpRuntimeActions : GameHubRuntimeActions {
        override fun selectGlobalProfile(profile: PerformanceProfile) = Unit
        override fun selectGameProfile(packageName: String, profile: PerformanceProfile) = Unit
        override fun applySmartGameAssistantSuggestion(
            packageName: String,
            suggestion: SmartGameAssistantSuggestion
        ) = Unit
        override fun finishRuntimeGameSession(metrics: SessionEndMetrics): SessionFinishHandle? = null
        override fun beginRuntimeGameSession(record: GameSessionRecord) = Unit
        override fun recordPerformanceEvent(event: PerformanceEvent) = Unit
        override fun recordRecentGame(packageName: String) = Unit
        override fun selectGame(packageName: String) = Unit
    }
}
