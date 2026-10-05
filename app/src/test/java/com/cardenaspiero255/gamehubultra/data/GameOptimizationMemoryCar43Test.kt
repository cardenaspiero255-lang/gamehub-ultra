package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GameOptimizationMemoryCar43Test {
    @Test
    fun `rejected and reverted recommendation decisions survive persistence`() = runBlocking {
        val file = File.createTempFile("gamehub-ultra-car43-", ".preferences_pb").also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val store = GameOptimizationMemoryStore(dataStore)
            val key = OptimizationContextKey("device", "game", "1", null, "gpu")
            store.record(
                key,
                OptimizationObservation(
                    contextKey = "",
                    profile = PerformanceProfile.X4,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                    timestampMillis = 1L
                )
            )
            store.record(
                key,
                OptimizationObservation(
                    contextKey = "",
                    profile = PerformanceProfile.FRAME_INTERPOLATION,
                    feedbackDecision = OptimizationFeedbackDecision.REVERTED,
                    timestampMillis = 2L
                )
            )

            val observations = store.observationsFlow(key).first()
            assertEquals(
                listOf(
                    OptimizationFeedbackDecision.REVERTED,
                    OptimizationFeedbackDecision.REJECTED
                ),
                observations.map { it.feedbackDecision }
            )
        } finally {
            scope.cancel()
            file.delete()
            File(file.absolutePath + ".corrupt").delete()
            File(file.absolutePath + ".bak").delete()
        }
    }
}
