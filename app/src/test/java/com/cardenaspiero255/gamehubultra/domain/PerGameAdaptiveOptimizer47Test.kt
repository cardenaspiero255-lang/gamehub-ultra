package com.cardenaspiero255.gamehubultra.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerGameAdaptiveOptimizer47Test {
    @Test
    fun isolatesStatePerGameVersion() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 1000)
        val hot = listOf(AdaptiveTrendSample(4, 50, 60f, 80, 100))
        val v1 = AdaptiveGameKey("game.a", "1")
        val v2 = AdaptiveGameKey("game.a", "2")

        optimizer.evaluate(v1, PerformanceProfile.X4, hot, 0)
        assertTrue(optimizer.evaluate(v1, PerformanceProfile.X4, hot, 100).changed)

        val firstV2 = optimizer.evaluate(v2, PerformanceProfile.X4, hot, 100)
        assertFalse(firstV2.changed)
        assertEquals(PerformanceProfile.X4, firstV2.profile)
    }

    @Test
    fun combinesRequiredTrendsAndRecordsReason() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        val samples = listOf(
            AdaptiveTrendSample(1, 70, 120f, 55, 30),
            AdaptiveTrendSample(2, 58, 90f, 75, 70),
            AdaptiveTrendSample(3, 44, 60f, 91, 140)
        )

        val result = optimizer.evaluate(
            AdaptiveGameKey("game.a", "1"),
            PerformanceProfile.X4,
            samples,
            10_000
        )

        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertTrue(result.changed)
        assertTrue(result.reason.contains("tendencia"))
    }

    @Test
    fun emptySamplesNeverChangeActiveProfile() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)

        val result = optimizer.evaluate(
            AdaptiveGameKey("game.a", "1"),
            PerformanceProfile.X4,
            emptyList(),
            0
        )

        assertFalse(result.changed)
        assertEquals(PerformanceProfile.X4, result.profile)
        assertTrue(result.reason.contains("Sin muestras"))
    }

    @Test
    fun externalProfileChangeResetsPendingAutomaticDecision() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 1000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        assertFalse(optimizer.evaluate(key, PerformanceProfile.X4, hot, 0).changed)

        val reconciled = optimizer.evaluate(
            key = key,
            activeProfile = PerformanceProfile.BALANCED,
            samples = hot,
            nowMillis = 100
        )

        assertFalse(reconciled.changed)
        assertEquals(PerformanceProfile.BALANCED, reconciled.profile)
        assertTrue(reconciled.reason.contains("externo", ignoreCase = true))
    }

    @Test
    fun manualBalancedProfileIsNotAutomaticallyEscalated() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 0)
        val key = AdaptiveGameKey("game.a", "1")
        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 35, 25),
            AdaptiveTrendSample(0, 88, 120f, 36, 24),
            AdaptiveTrendSample(0, 86, 120f, 37, 23)
        )

        assertFalse(optimizer.evaluate(key, PerformanceProfile.BALANCED, stable, 0).changed)
        val second = optimizer.evaluate(key, PerformanceProfile.BALANCED, stable, 100)

        assertFalse(second.changed)
        assertEquals(PerformanceProfile.BALANCED, second.profile)
    }

    @Test
    fun cooldownAndHysteresisPreventOscillationButAllowOwnedRecovery() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 5000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val downshift = optimizer.evaluate(key, PerformanceProfile.X4, hot, 0)
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        val good = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val held = optimizer.evaluate(key, PerformanceProfile.BALANCED, good, 1000)
        assertFalse(held.changed)
        assertTrue(held.reason.contains("enfriamiento"))

        val recovered = optimizer.evaluate(key, PerformanceProfile.BALANCED, good, 6000)
        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.X4, recovered.profile)
    }
    @Test
    fun recoversTheExactProfileOwnedBeforeAutomaticDownshift() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 1000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val downshift = optimizer.evaluate(
            key,
            PerformanceProfile.FRAME_INTERPOLATION,
            hot,
            0
        )
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val recovered = optimizer.evaluate(
            key,
            PerformanceProfile.BALANCED,
            stable,
            1500
        )

        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, recovered.profile)
    }

    @Test
    fun pendingConfirmationSurvivesOptimizerRecreation() {
        val persisted = mutableMapOf<AdaptiveGameKey, PerGameAdaptivePersistedState>()
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey) = persisted[key]
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                persisted[key] = state
            }
        }
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val first = PerGameAdaptiveOptimizer(
            confirmationsRequired = 2,
            cooldownMillis = 1000,
            stateStore = store
        )
        assertFalse(first.evaluate(key, PerformanceProfile.X4, hot, 0).changed)

        val recreated = PerGameAdaptiveOptimizer(
            confirmationsRequired = 2,
            cooldownMillis = 1000,
            stateStore = store
        )
        val second = recreated.evaluate(key, PerformanceProfile.X4, hot, 100)

        assertTrue(second.changed)
        assertEquals(PerformanceProfile.BALANCED, second.profile)
    }

    @Test
    fun ownedRecoveryProfileSurvivesOptimizerRecreation() {
        val persisted = mutableMapOf<AdaptiveGameKey, PerGameAdaptivePersistedState>()
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey) = persisted[key]
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                persisted[key] = state
            }
        }
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))
        val first = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 1000,
            stateStore = store
        )
        assertEquals(
            PerformanceProfile.BALANCED,
            first.evaluate(key, PerformanceProfile.FRAME_INTERPOLATION, hot, 0).profile
        )

        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val recreated = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 1000,
            stateStore = store
        )
        val recovered = recreated.evaluate(
            key,
            PerformanceProfile.BALANCED,
            stable,
            1500
        )

        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, recovered.profile)
    }

    @Test
    fun explicitSameProfileSelectionCancelsOwnedRecovery() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 1_000
        )
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val downshift = optimizer.evaluate(
            key,
            PerformanceProfile.X4,
            hot,
            0
        )
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        optimizer.recordExplicitProfileSelection(
            key = key,
            profile = PerformanceProfile.BALANCED
        )

        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val afterManualSelection = optimizer.evaluate(
            key,
            PerformanceProfile.BALANCED,
            stable,
            2_000
        )

        assertFalse(afterManualSelection.changed)
        assertEquals(PerformanceProfile.BALANCED, afterManualSelection.profile)
    }

    @Test
    fun restoringSnapshotRehydratesRuntimeAndPersistentState() {
        val persisted = mutableMapOf<AdaptiveGameKey, PerGameAdaptivePersistedState>()
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey): PerGameAdaptivePersistedState? = persisted[key]
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                persisted[key] = state
            }
        }
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0,
            stateStore = store
        )
        val key = AdaptiveGameKey("game.restore", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        optimizer.evaluate(key, PerformanceProfile.X4, hot, 10L)
        val snapshot = requireNotNull(optimizer.snapshotState(key))
        optimizer.recordExplicitProfileSelection(key, PerformanceProfile.X4)

        optimizer.restoreState(key, snapshot)

        assertEquals(snapshot, persisted[key])
        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val recovered = optimizer.evaluate(
            key,
            PerformanceProfile.BALANCED,
            stable,
            20L
        )
        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.X4, recovered.profile)
    }


    @Test
    fun ownedRecoveryMigratesToNewGameVersionWithoutSharingHysteresis() {
        val persisted = mutableMapOf(
            AdaptiveGameKey("game.a", "1#10") to PerGameAdaptivePersistedState(
                profile = PerformanceProfile.BALANCED,
                candidate = PerformanceProfile.BALANCED,
                confirmations = 9,
                lastChangeMillis = 123L,
                recoveryProfile = PerformanceProfile.X4
            )
        )
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey) = persisted[key]
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                persisted[key] = state
            }
            override fun ownedRecoveryProfileForPackage(
                packageName: String,
                excludingVersion: String,
                activeProfile: PerformanceProfile
            ): PerformanceProfile? =
                persisted.entries
                    .firstOrNull { (key, state) ->
                        key.packageName == packageName &&
                            key.version != excludingVersion &&
                            state.profile == activeProfile &&
                            state.recoveryProfile != null
                    }
                    ?.value
                    ?.recoveryProfile
        }
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 30_000L,
            stateStore = store
        )
        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )

        val recovered = optimizer.evaluate(
            AdaptiveGameKey("game.a", "2#20"),
            PerformanceProfile.BALANCED,
            stable,
            1_000L
        )

        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.X4, recovered.profile)
    }


    @Test
    fun thermalPredictionSignalStillRequiresCar47Confirmations() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 0)
        val key = AdaptiveGameKey("game.thermal", "1#1")
        val samples = listOf(
            AdaptiveTrendSample(
                thermalStatus = 1,
                batteryPercent = 80,
                refreshRateHz = 120f,
                memoryUsedPercent = 45,
                latencyMs = 30,
                thermalPrediction = preventiveThermalPrediction()
            )
        )

        val first = optimizer.evaluate(key, PerformanceProfile.X4, samples, 1_000L)
        assertFalse(first.changed)
        assertEquals(PerformanceProfile.X4, first.profile)

        val second = optimizer.evaluate(key, PerformanceProfile.X4, samples, 2_000L)
        assertTrue(second.changed)
        assertEquals(PerformanceProfile.BALANCED, second.profile)
        assertTrue(second.reason.contains("predicción térmica", ignoreCase = true))
    }

    @Test
    fun nonActionableThermalPredictionNeverBypassesCar47Policy() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        val key = AdaptiveGameKey("game.thermal", "1#1")
        val samples = listOf(
            AdaptiveTrendSample(
                thermalStatus = 1,
                batteryPercent = 80,
                refreshRateHz = 120f,
                memoryUsedPercent = 45,
                latencyMs = 30,
                thermalPrediction = preventiveThermalPrediction(
                    confidence = 0.60f,
                    allowPreventiveSignal = false
                )
            )
        )

        val result = optimizer.evaluate(key, PerformanceProfile.X4, samples, 1_000L)

        assertFalse(result.changed)
        assertEquals(PerformanceProfile.X4, result.profile)
    }

    private fun preventiveThermalPrediction(
        confidence: Float = 0.90f,
        allowPreventiveSignal: Boolean = true
    ) = ThermalPrediction(
        trend = ThermalTrend.RISING,
        risk = ThermalRisk.HIGH,
        confidence = confidence,
        signalMode = ThermalSignalMode.HEADROOM_AND_STATUS,
        slopePerMinute = 0.20f,
        accelerationPerMinuteSquared = 0f,
        latestMeasuredHeadroom = 0.70f,
        projectedHeadroom = 0.82f,
        allowPreventiveSignal = allowPreventiveSignal,
        recovering = false,
        evidence = emptyList(),
        reason = "Predicción térmica preventiva de prueba."
    )


    @Test
    fun chargingCanSatisfyBatteryRecoveryGateBelowFiftyFivePercent() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val key = AdaptiveGameKey("game.charging-recovery", "1")
        val pressure = listOf(
            AdaptiveTrendSample(
                thermalStatus = 4,
                batteryPercent = 40,
                refreshRateHz = 120f,
                memoryUsedPercent = 40,
                latencyMs = 30,
                batteryCharging = false
            )
        )

        val downshift = optimizer.evaluate(
            key = key,
            activeProfile = PerformanceProfile.X4,
            samples = pressure,
            nowMillis = 1_000L
        )
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        val stableCharging = listOf(
            AdaptiveTrendSample(0, 30, 120f, 30, 20, batteryCharging = true),
            AdaptiveTrendSample(0, 32, 120f, 31, 22, batteryCharging = true),
            AdaptiveTrendSample(0, 34, 120f, 32, 24, batteryCharging = true)
        )
        val recovered = optimizer.evaluate(
            key = key,
            activeProfile = PerformanceProfile.BALANCED,
            samples = stableCharging,
            nowMillis = 2_000L
        )

        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.X4, recovered.profile)
    }

}
