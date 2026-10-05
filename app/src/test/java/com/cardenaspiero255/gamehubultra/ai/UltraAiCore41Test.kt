package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraAiCore41Test {
    @Test
    fun `core separates observation recommendation explanation feedback and memory layers`() {
        val core = UltraAiCore2(
            recommender = UltraAiRecommender { observation, _, _ ->
                UltraAiRecommendation(
                    profileId = "BALANCED",
                    confidence = 0.80,
                    evidence = listOf("battery=" + observation.batteryPercent)
                )
            }
        )

        val result = core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 18,
                thermalLabel = "moderado",
                refreshRateHz = 120f,
                latencyMs = 42
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = listOf(
                UltraAiMemorySignal(
                    text = "Prefiero estabilidad",
                    provenance = UltraMemoryProvenance.REMEMBERED_FACT
                )
            )
        )

        assertEquals("BALANCED", result.recommendation.profileId)
        assertTrue(result.explanation.contains("battery=18"))
        assertEquals(1, result.memorySignals.size)
    }

    @Test
    fun `core uses deterministic local fallback without model or cloud`() {
        val result = UltraAiCore2(recommender = null).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 12,
                thermalLabel = "alto",
                refreshRateHz = 120f,
                latencyMs = 50
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )

        assertEquals(UltraAiRecommendationSource.DETERMINISTIC_LOCAL, result.recommendation.source)
        assertFalse(result.requiresCloud)
        assertTrue(result.recommendation.evidence.isNotEmpty())
    }

    @Test
    fun `fallback never invents unavailable diagnostics`() {
        val result = UltraAiCore2(recommender = null).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED"
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )

        assertFalse(result.explanation.contains("fps", ignoreCase = true))
        assertFalse(result.explanation.contains("temperatura:", ignoreCase = true))
        assertFalse(result.explanation.contains("latencia:", ignoreCase = true))
    }
    @Test
    fun `structured context maps only real diagnostics and profile state`() {
        val context = GameHubAiContext(
            selectedGamePackage = "com.example.game",
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
            gpuAvailable = true,
            thermalStatus = 3,
            thermalHeadroom = 0.4f,
            batteryPercent = 64,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 37,
            downstreamBandwidthKbps = 100_000,
            storageFreePercent = 42,
            inputDeviceCount = 1,
            selectedProfile = com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.BALANCED,
            sessionActive = true
        )

        val observation = UltraAiObservation.from(context)

        assertEquals("com.example.game", observation.gamePackage)
        assertEquals("BALANCED", observation.activeProfileId)
        assertEquals(64, observation.batteryPercent)
        assertEquals(120f, observation.refreshRateHz)
        assertEquals(37, observation.latencyMs)
        assertEquals(3, observation.thermalStatus)
        assertTrue(observation.sessionActive)
    }

    @Test
    fun `memory recall converts to provenance-aware signals without losing source`() {
        val recall = UltraMemoryRecall(
            record = UltraStoredMemory(
                id = "m1",
                kind = UltraMemoryKind.FACT,
                role = UltraMemoryRole.USER,
                text = "Prefiero estabilidad",
                timestampMillis = 1L,
                scope = UltraMemoryScope()
            ),
            score = 0.9,
            provenance = UltraMemoryProvenance.REMEMBERED_FACT
        )

        val signal = UltraAiMemorySignal.from(recall)

        assertEquals("Prefiero estabilidad", signal.text)
        assertEquals(UltraMemoryProvenance.REMEMBERED_FACT, signal.provenance)
    }

    @Test
    fun `invalid diagnostic values are discarded instead of becoming evidence`() {
        val observation = UltraAiObservation(
            gamePackage = "com.example.game",
            activeProfileId = "BALANCED",
            batteryPercent = 150,
            refreshRateHz = -1f,
            latencyMs = -20
        ).sanitized()

        assertEquals(null, observation.batteryPercent)
        assertEquals(null, observation.refreshRateHz)
        assertEquals(null, observation.latencyMs)
    }

    @Test
    fun `deterministic fallback covers thermal feedback active profile and safe default branches`() {
        val core = UltraAiCore2()

        val thermal = core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 80,
                thermalLabel = " HOT "
            ),
            feedback = UltraAiFeedbackSnapshot(
                rejectedProfileIds = setOf("BALANCED")
            ),
            memories = emptyList()
        )
        assertEquals("BALANCED", thermal.recommendation.profileId)
        assertTrue(thermal.recommendation.evidence.any { it.startsWith("thermal=") })
        assertTrue(thermal.recommendation.evidence.contains("feedback=rejected"))

        val active = core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "FRAME_INTERPOLATION",
                batteryPercent = 70
            ),
            feedback = UltraAiFeedbackSnapshot(
                acceptedProfileIds = setOf("FRAME_INTERPOLATION")
            ),
            memories = emptyList()
        )
        assertEquals("FRAME_INTERPOLATION", active.recommendation.profileId)
        assertTrue(active.recommendation.evidence.contains("activeProfile=FRAME_INTERPOLATION"))
        assertTrue(active.recommendation.evidence.contains("feedback=accepted"))

        val safeDefault = core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = ""
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )
        assertEquals("BALANCED", safeDefault.recommendation.profileId)
        assertTrue(safeDefault.recommendation.evidence.contains("safe-default"))
    }

    @Test
    fun `explanation exposes only sanitized available metrics and handles empty model evidence`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation(
                    profileId = "X4",
                    confidence = 0.91,
                    evidence = listOf("", " ")
                )
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED",
                batteryPercent = 60,
                thermalLabel = "moderado",
                refreshRateHz = 144f,
                latencyMs = 33,
                thermalStatus = 2,
                thermalHeadroom = 0.45f
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )

        assertTrue(result.explanation.contains("sin métricas adicionales disponibles"))
        assertTrue(result.explanation.contains("batería=60%"))
        assertTrue(result.explanation.contains("estado térmico=moderado"))
        assertTrue(result.explanation.contains("refresco=144 Hz"))
        assertTrue(result.explanation.contains("latencia=33 ms"))

        val invalid = UltraAiObservation(
            gamePackage = "com.example.game",
            activeProfileId = "BALANCED",
            batteryPercent = -1,
            refreshRateHz = Float.NaN,
            latencyMs = -1,
            thermalStatus = -1,
            thermalHeadroom = Float.NaN
        ).sanitized()

        assertEquals(null, invalid.batteryPercent)
        assertEquals(null, invalid.refreshRateHz)
        assertEquals(null, invalid.latencyMs)
        assertEquals(null, invalid.thermalStatus)
        assertEquals(null, invalid.thermalHeadroom)
    }

    @Test
    fun `model recommender receives provenance aware memory signals`() {
        var receivedMemories: List<UltraAiMemorySignal> = emptyList()
        val core = UltraAiCore2(
            recommender = UltraAiRecommender { observation, _, memories ->
                receivedMemories = memories
                UltraAiRecommendation(
                    profileId = observation.activeProfileId,
                    confidence = 0.88,
                    evidence = listOf("memory-aware")
                )
            }
        )
        val memory = UltraAiMemorySignal(
            text = "Prefiero estabilidad",
            provenance = UltraMemoryProvenance.REMEMBERED_FACT
        )

        core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED"
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = listOf(memory)
        )

        assertEquals(listOf(memory), receivedMemories)
    }

}
