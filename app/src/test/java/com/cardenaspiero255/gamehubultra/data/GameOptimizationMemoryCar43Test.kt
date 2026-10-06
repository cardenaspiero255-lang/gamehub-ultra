package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationFingerprint
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
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

    @Test
    fun `feedback in other contexts cannot evict measured session history`() = runBlocking {
        val file = File.createTempFile("gamehub-ultra-car43-retention-", ".preferences_pb")
            .also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val store = GameOptimizationMemoryStore(dataStore, maxObservations = 2)
            val measuredKey = OptimizationContextKey("device", "game.a", "1", null, "gpu")
            val noisyKey = OptimizationContextKey("device", "game.b", "1", null, "gpu")

            store.record(
                measuredKey,
                OptimizationObservation(
                    contextKey = "",
                    profile = PerformanceProfile.BALANCED,
                    measuredFps = 60f,
                    stable = true,
                    timestampMillis = 1L
                )
            )
            repeat(3) { index ->
                store.record(
                    noisyKey,
                    OptimizationObservation(
                        contextKey = "",
                        profile = PerformanceProfile.X4,
                        feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                        timestampMillis = 10L + index
                    )
                )
            }

            val measured = store.observationsFlow(measuredKey).first()
            assertEquals(1, measured.size)
            assertEquals(60f, measured.single().measuredFps)
        } finally {
            scope.cancel()
            file.delete()
            File(file.absolutePath + ".corrupt").delete()
            File(file.absolutePath + ".bak").delete()
        }
    }

    @Test
    fun `normalized no-gpu key still reads legacy pipe fingerprint history`() = runBlocking {
        val file = File.createTempFile("gamehub-ultra-car43-legacy-gpu-", ".preferences_pb")
            .also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val device = DeviceInfo(
                manufacturer = "Test",
                model = "NoGpuFields",
                androidVersion = "14",
                sdkInt = 34,
                supportedAbis = listOf("arm64-v8a"),
                cpuModel = "cpu",
                cpuCores = 8,
                totalRamMb = 8192,
                gpuVendor = null,
                gpuRenderer = null
            )
            val currentKey = OptimizationContextKeyFactory.from(
                device = device,
                gamePackage = "game.a",
                gameVersion = "1",
                emulatorBackend = null
            )
            val legacyKey = OptimizationContextKey(
                deviceFingerprint = OptimizationFingerprint.from(
                    device = device,
                    gamePackage = "game.a",
                    gameVersion = "1",
                    emulatorBackend = null,
                    driverFingerprint = "|"
                ),
                gamePackage = "game.a",
                gameVersion = "1",
                emulatorBackend = null,
                driverFingerprint = "|"
            )
            val legacyObservation = OptimizationObservation(
                id = "legacy-no-gpu",
                contextKey = legacyKey.serialized,
                profile = PerformanceProfile.X4,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                timestampMillis = 99L
            )
            val encoded = listOf(
                legacyObservation.id,
                legacyObservation.contextKey,
                legacyObservation.profile.name,
                "",
                "false",
                "false",
                "false",
                "",
                "",
                "",
                legacyObservation.timestampMillis.toString(),
                legacyObservation.feedbackDecision.name
            ).joinToString("|") { value ->
                Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
            }
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("observations_v1")] = encoded
            }

            val loaded = GameOptimizationMemoryStore(dataStore)
                .observationsFlow(currentKey)
                .first()

            assertEquals(1, loaded.size)
            assertEquals("legacy-no-gpu", loaded.single().id)
            assertEquals(OptimizationFeedbackDecision.REJECTED, loaded.single().feedbackDecision)
        } finally {
            scope.cancel()
            file.delete()
            File(file.absolutePath + ".corrupt").delete()
            File(file.absolutePath + ".bak").delete()
        }
    }

}
