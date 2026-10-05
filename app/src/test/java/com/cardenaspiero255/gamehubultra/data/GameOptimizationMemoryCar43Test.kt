package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
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

    @Test
    fun `legacy CAR42 observations remain readable with neutral feedback`() = runBlocking {
        val file = File.createTempFile("gamehub-ultra-car42-legacy-", ".preferences_pb")
            .also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val key = OptimizationContextKey("device", "game", "1", null, "gpu")
            val legacyFields = listOf(
                "legacy-id",
                key.serialized,
                PerformanceProfile.X4.name,
                "60.0",
                "true",
                "false",
                "false",
                "",
                "80",
                "",
                "42"
            )
            val encoded = legacyFields.joinToString("|") { value ->
                Base64.getEncoder().encodeToString(
                    value.toByteArray(StandardCharsets.UTF_8)
                )
            }
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("observations_v1")] = encoded
            }

            val observation = GameOptimizationMemoryStore(dataStore)
                .observationsFlow(key)
                .first()
                .single()

            assertEquals(PerformanceProfile.X4, observation.profile)
            assertEquals(60f, observation.measuredFps)
            assertEquals(OptimizationFeedbackDecision.NONE, observation.feedbackDecision)
        } finally {
            scope.cancel()
            file.delete()
            File(file.absolutePath + ".corrupt").delete()
            File(file.absolutePath + ".bak").delete()
        }
    }

}
