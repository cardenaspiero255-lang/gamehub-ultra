package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class UltraResearchProviderHealthTest {
    @Test
    fun repeatedInfrastructureFailuresOpenCircuitUntilCooldownExpires() {
        val health = UltraResearchProviderHealth(
            policy = UltraResearchProviderHealthPolicy(
                failureThreshold = 2,
                cooldownMillis = 30_000L
            )
        )

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "UPSTREAM_UNAVAILABLE",
                retryable = true
            ),
            nowMillis = 1_000L
        )
        assertTrue(health.isAvailable("primary", 1_001L))

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "UPSTREAM_TIMEOUT",
                retryable = true
            ),
            nowMillis = 2_000L
        )

        assertFalse(health.isAvailable("primary", 31_999L))
        assertTrue(health.isAvailable("primary", 32_000L))
    }

    @Test
    fun successfulEvidenceImmediatelyRestoresProviderHealth() {
        val health = UltraResearchProviderHealth(
            policy = UltraResearchProviderHealthPolicy(
                failureThreshold = 1,
                cooldownMillis = 60_000L
            )
        )

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "PROVIDER_FAILURE",
                retryable = true
            ),
            nowMillis = 1_000L
        )
        assertFalse(health.isAvailable("primary", 1_001L))

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Evidence(
                UltraResearchEvidence(
                    claimKey = "claim",
                    value = "value",
                    displayText = "answer",
                    sourceId = "source"
                )
            ),
            nowMillis = 1_002L
        )

        assertTrue(health.isAvailable("primary", 1_003L))
    }

    @Test
    fun semanticAbstentionDoesNotPoisonProviderHealth() {
        val health = UltraResearchProviderHealth(
            policy = UltraResearchProviderHealthPolicy(
                failureThreshold = 1,
                cooldownMillis = 60_000L
            )
        )

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Abstained(
                reasonCode = "INSUFFICIENT_CORROBORATION",
                retryable = false
            ),
            nowMillis = 1_000L
        )

        assertTrue(health.isAvailable("primary", 1_001L))
    }

    @Test
    fun semanticOutcomeResetsInfrastructureFailureStreak() {
        val health = UltraResearchProviderHealth(
            policy = UltraResearchProviderHealthPolicy(
                failureThreshold = 2,
                cooldownMillis = 60_000L
            )
        )

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "UPSTREAM_UNAVAILABLE",
                retryable = true
            ),
            nowMillis = 1_000L
        )
        health.record(
            providerId = "primary",
            result = UltraProviderResult.Abstained(
                reasonCode = "INSUFFICIENT_CORROBORATION",
                retryable = false
            ),
            nowMillis = 2_000L
        )
        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "UPSTREAM_TIMEOUT",
                retryable = true
            ),
            nowMillis = 3_000L
        )

        assertTrue(health.isAvailable("primary", 3_001L))
    }

    @Test
    fun researchEngineSkipsOpenProviderAndUsesHealthyFallback() {
        var primaryCalls = 0
        var fallbackCalls = 0
        var now = 1_000L
        val health = UltraResearchProviderHealth(
            policy = UltraResearchProviderHealthPolicy(
                failureThreshold = 1,
                cooldownMillis = 60_000L
            )
        )
        val primary = object : UltraResearchProvider {
            override val id: String = "primary"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                primaryCalls += 1
                error("primary should be skipped while its circuit is open")
            }
        }
        val fallback = object : UltraResearchProvider {
            override val id: String = "fallback"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                fallbackCalls += 1
                return UltraResearchEvidence(
                    claimKey = "android",
                    value = "verified",
                    displayText = "Dato verificado.",
                    sourceId = "fallback-source",
                    independentSourceCount = 2,
                    authoritative = true
                )
            }
        }

        health.record(
            providerId = "primary",
            result = UltraProviderResult.Failure(
                reasonCode = "UPSTREAM_UNAVAILABLE",
                retryable = true
            ),
            nowMillis = now
        )

        val engine = UltraVerifiedResearchEngine(
            providers = listOf(primary, fallback),
            providerHealth = health,
            nowMillis = { now }
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        )

        assertEquals(0, primaryCalls)
        assertEquals(1, fallbackCalls)
        assertFalse(answer.abstained)
        assertTrue(answer.sources.contains("fallback-source"))
    }
}
