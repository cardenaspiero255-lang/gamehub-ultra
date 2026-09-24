package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameOptimizationMemoryStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: GameOptimizationMemoryStore
    private lateinit var key: OptimizationContextKey

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-opt-", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        store = GameOptimizationMemoryStore(dataStore)
        key = OptimizationContextKey("device-a", "game-a", "1", null, "adreno")
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        deleteDataStoreFiles(file)
    }

    @Test
    fun persistsResultsAcrossReadsAndFiltersByContext() = runBlocking {
        store.record(
            key,
            OptimizationObservation(
                contextKey = "",
                profile = PerformanceProfile.X4,
                measuredFps = 60f,
                stable = true,
                timestampMillis = 10L
            )
        )
        assertEquals(1, store.observationsFlow(key).first().size)
        assertTrue(store.observationsFlow(key.copy(gameVersion = "2")).first().isEmpty())
    }

    @Test
    fun persistsResultsAcrossStoreRestart() = runBlocking {
        store.record(
            key,
            OptimizationObservation(
                contextKey = "",
                profile = PerformanceProfile.X4,
                measuredFps = 72f,
                stable = true,
                timestampMillis = 42L
            )
        )

        scope.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.join()

        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restartedDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
                scope = restartedScope,
                produceFile = { file }
            )
            val restartedStore = GameOptimizationMemoryStore(restartedDataStore)
            val restartedObservations = restartedStore.observationsFlow(key).first()

            assertEquals(1, restartedObservations.size)
            assertEquals(PerformanceProfile.X4, restartedObservations.single().profile)
            assertEquals(72f, restartedObservations.single().measuredFps)
        } finally {
            restartedScope.cancel()
        }
    }

    @Test
    fun clearAllResetsLearningMemoryWithoutCredentials() = runBlocking {
        store.record(key, OptimizationObservation(contextKey = "", profile = PerformanceProfile.X4, timestampMillis = 1L))

        store.clearAll()

        assertTrue(store.observationsFlow(key).first().isEmpty())
    }

    @Test
    fun clearGameDoesNotDeleteOtherContext() = runBlocking {
        val other = key.copy(gamePackage = "game-b")
        store.record(key, OptimizationObservation(contextKey = "", profile = PerformanceProfile.BALANCED, timestampMillis = 1L))
        store.record(other, OptimizationObservation(contextKey = "", profile = PerformanceProfile.X4, timestampMillis = 2L))
        store.clearGame(key)
        assertTrue(store.observationsFlow(key).first().isEmpty())
        assertEquals(1, store.observationsFlow(other).first().size)
    }

    @Test
    fun limitsHistoryToConfiguredSize() = runBlocking {
        repeat(125) { index ->
            store.record(
                key,
                OptimizationObservation(
                    contextKey = "",
                    profile = PerformanceProfile.BALANCED,
                    timestampMillis = index.toLong()
                )
            )
        }
        assertEquals(120, store.observationsFlow(key).first().size)
    }

    private fun deleteDataStoreFiles(base: File) {
        base.delete()
        File(base.absolutePath + ".corrupt").delete()
        File(base.absolutePath + ".bak").delete()
    }
}
