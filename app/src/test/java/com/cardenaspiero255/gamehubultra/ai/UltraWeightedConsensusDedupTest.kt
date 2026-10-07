package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWeightedConsensusDedupTest {
    @Test
    fun overlappingSourceIdsDoNotInflateIndependentEvidence() {
        val decision = UltraWeightedConsensusEngine(
            minimumIndependentSources = 2
        ).decide(
            listOf(
                UltraWeightedEvidenceCandidate(
                    providerId = "provider-a",
                    claimKey = "claim",
                    value = "same",
                    displayText = "same",
                    sourceIds = setOf("shared-source"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.9
                ),
                UltraWeightedEvidenceCandidate(
                    providerId = "provider-b",
                    claimKey = "claim",
                    value = "same",
                    displayText = "same",
                    sourceIds = setOf("shared-source"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.9
                )
            )
        )

        assertEquals(1, decision.independentSourceCount)
        assertFalse(decision.accepted)
    }

    @Test
    fun multipleUrlsFromOneUnderlyingSourceStillCountAsOne() {
        val decision = UltraWeightedConsensusEngine(
            minimumIndependentSources = 2
        ).decide(
            listOf(
                UltraWeightedEvidenceCandidate(
                    providerId = "provider-a",
                    claimKey = "claim",
                    value = "same",
                    displayText = "same",
                    sourceIds = setOf("page-a", "page-b"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.9
                )
            )
        )

        assertEquals(1, decision.independentSourceCount)
        assertFalse(decision.accepted)
    }

    @Test
    fun distinctUnderlyingSourcesStillSatisfyConsensusQuorum() {
        val decision = UltraWeightedConsensusEngine(
            minimumIndependentSources = 2
        ).decide(
            listOf(
                UltraWeightedEvidenceCandidate(
                    providerId = "provider-a",
                    claimKey = "claim",
                    value = "same",
                    displayText = "same",
                    sourceIds = setOf("source-a"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.9
                ),
                UltraWeightedEvidenceCandidate(
                    providerId = "provider-b",
                    claimKey = "claim",
                    value = "same",
                    displayText = "same",
                    sourceIds = setOf("source-b"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.9
                )
            )
        )

        assertEquals(2, decision.independentSourceCount)
        assertTrue(decision.accepted)
    }
}
