package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraFrontierV2Test {
    @Test
    fun hierarchicalPlannerBuildsExplicitDeepResearchPhases() {
        val query = UltraGeneralQueryRouter.classify(
            "Compara profundamente dos teléfonos actuales y verifica cada afirmación"
        )
        val request = UltraFrontierRequest(
            message = query.originalText,
            query = query,
            networkAvailable = true,
            memoryAvailable = true
        )
        val tasks = UltraFrontierTaskPlanner().plan(
            request = request,
            lane = UltraFrontierLane.DEEP_RESEARCH
        )

        val hierarchy = UltraFrontierV2HierarchicalPlanner().plan(
            request = request,
            lane = UltraFrontierLane.DEEP_RESEARCH,
            tasks = tasks
        )

        assertEquals(UltraFrontierV2Phase.ROUTING, hierarchy.phases.first().phase)
        assertTrue(
            hierarchy.phases.any {
                it.phase == UltraFrontierV2Phase.EVIDENCE &&
                    it.taskIds.containsAll(setOf("research-a", "research-b"))
            }
        )
        assertTrue(
            hierarchy.phases.any {
                it.phase == UltraFrontierV2Phase.SYNTHESIS &&
                    "synthesize" in it.taskIds
            }
        )
        assertEquals(UltraFrontierV2Phase.CRITIQUE, hierarchy.phases.last().phase)
        assertTrue(hierarchy.objective.requiresVerification)
    }

    @Test
    fun replannerReplacesFailedResearchBranchAndRetargetsSynthesis() {
        val query = UltraGeneralQueryRouter.classify(
            "Compara profundamente dos teléfonos actuales"
        )
        val request = UltraFrontierRequest(
            message = query.originalText,
            query = query,
            networkAvailable = true
        )
        val tasks = UltraFrontierTaskPlanner().plan(
            request = request,
            lane = UltraFrontierLane.DEEP_RESEARCH
        )

        val replanned = UltraFrontierV2Replanner().replan(
            tasks = tasks,
            failedTaskIds = setOf("research-a"),
            remainingTimeMillis = 15_000L,
            recoveryOrdinal = 3
        )

        val recovery = replanned.tasks.single {
            it.id == "research-recovery-3"
        }
        val synthesis = replanned.tasks.single {
            it.id == "synthesize"
        }

        assertFalse(replanned.tasks.any { it.id == "research-a" })
        assertEquals(UltraFrontierSpecialist.RESEARCH, recovery.specialist)
        assertTrue("research-recovery-3" in synthesis.dependsOn)
        assertTrue("research-b" in synthesis.dependsOn)
        assertTrue(replanned.changed)
    }

    @Test
    fun adaptiveBranchAllocatorPreservesBudgetAndRewardsStrongNovelBranch() {
        val allocator = UltraFrontierV2BranchAllocator()
        val allocation = allocator.allocate(
            totalSourceBudget = 7,
            maxParallelism = 3,
            signals = listOf(
                UltraFrontierV2BranchSignal(
                    taskId = "a",
                    reliability = 0.95,
                    novelty = 0.90,
                    urgency = 0.80
                ),
                UltraFrontierV2BranchSignal(
                    taskId = "b",
                    reliability = 0.55,
                    novelty = 0.40,
                    urgency = 0.50
                ),
                UltraFrontierV2BranchSignal(
                    taskId = "c",
                    reliability = 0.25,
                    novelty = 0.20,
                    urgency = 0.20
                )
            )
        )

        assertEquals(7, allocation.values.sum())
        assertTrue(allocation.getValue("a") >= allocation.getValue("b"))
        assertTrue(allocation.getValue("b") >= allocation.getValue("c"))
        assertTrue(allocation.values.all { it >= 1 })
    }

    @Test
    fun claimProvenanceDoesNotOverwriteDistinctEvidenceWhenIdsContainDelimiters() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "price",
                normalizedValue = "100",
                sourceId = "official|mirror",
                providerId = "provider-a",
                authoritative = true
            )
        )
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "price",
                normalizedValue = "100",
                sourceId = "official",
                providerId = "mirror|provider-a",
                authoritative = false
            )
        )
        val snapshot = assertNotNull(graph.snapshot("price", "100"))
        assertEquals(2, snapshot.supportingSources)
        assertEquals(2, snapshot.independentSources)
        assertEquals(2, snapshot.providers)
        assertTrue(snapshot.hasAuthoritativeSupport)
        assertFalse(snapshot.hasConflict)
    }

    @Test
    fun caseSensitiveUrlPathsRemainIndependentEvidence() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "scientific finding",
                normalizedValue = "confirmed",
                sourceId = "HTTPS://EXAMPLE.ORG/Evidence/A",
                providerId = "primary",
                authoritative = true
            )
        )
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "scientific finding",
                normalizedValue = "confirmed",
                sourceId = "https://example.org/Evidence/a",
                providerId = "primary",
                authoritative = false
            )
        )
        val snapshot = assertNotNull(
            graph.snapshot("scientific finding", "confirmed")
        )
        assertEquals(2, snapshot.supportingSources)
        assertEquals(2, snapshot.independentSources)
        assertTrue(snapshot.hasAuthoritativeSupport)
    }

    @Test
    fun identicalUrlPathsDeduplicateAcrossSchemeAndHostCase() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        val evidence = UltraFrontierV2ClaimEvidence(
            claimId = "report",
            normalizedValue = "accepted",
            sourceId = "HTTPS://EXAMPLE.COM/Report?Version=1",
            providerId = "provider",
            authoritative = true
        )
        graph.record(evidence)
        graph.record(
            evidence.copy(
                sourceId = "https://example.com/Report?Version=1",
                authoritative = false
            )
        )
        val snapshot = assertNotNull(graph.snapshot("report", "accepted"))
        assertEquals(1, snapshot.supportingSources)
        assertTrue(snapshot.hasAuthoritativeSupport)
    }

    @Test
    fun claimProvenanceRetainsAuthorityWhenRepeatedObservationIsLessTrustworthy() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        val trusted = UltraFrontierV2ClaimEvidence(
            claimId = " Price ",
            normalizedValue = " 100 ",
            sourceId = " Official ",
            providerId = " Provider ",
            authoritative = true
        )
        graph.record(trusted)
        graph.record(
            trusted.copy(
                claimId = "price",
                normalizedValue = "100",
                sourceId = "official",
                providerId = "provider",
                authoritative = false
            )
        )
        val snapshot = assertNotNull(graph.snapshot("PRICE", "100"))
        assertEquals(1, snapshot.supportingSources)
        assertEquals(1, snapshot.independentSources)
        assertEquals(1, snapshot.providers)
        assertTrue(snapshot.hasAuthoritativeSupport)
        assertFalse(snapshot.hasConflict)
    }

    @Test
    fun claimProvenancePromotesAuthorityWhenStrongEvidenceArrivesLater() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        val evidence = UltraFrontierV2ClaimEvidence(
            claimId = "battery",
            normalizedValue = "5000mah",
            sourceId = "official",
            providerId = "maker",
            authoritative = false
        )
        graph.record(evidence)
        graph.record(evidence.copy(authoritative = true))
        val snapshot = assertNotNull(graph.snapshot("battery", "5000mah"))
        assertEquals(1, snapshot.supportingSources)
        assertTrue(snapshot.hasAuthoritativeSupport)
    }

    @Test
    fun claimProvenanceTracksSupportConflictAndIndependentSources() {
        val graph = UltraFrontierV2ClaimProvenanceGraph()
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "price",
                normalizedValue = "100",
                sourceId = "official-a",
                providerId = "provider-a",
                authoritative = true
            )
        )
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "price",
                normalizedValue = "100",
                sourceId = "official-b",
                providerId = "provider-b",
                authoritative = false
            )
        )
        graph.record(
            UltraFrontierV2ClaimEvidence(
                claimId = "price",
                normalizedValue = "999",
                sourceId = "conflict-c",
                providerId = "provider-c",
                authoritative = false
            )
        )

        val snapshot = graph.snapshot("price", preferredValue = "100")

        assertNotNull(snapshot)
        assertEquals(2, snapshot.supportingSources)
        assertEquals(1, snapshot.conflictingSources)
        assertEquals(3, snapshot.independentSources)
        assertEquals(3, snapshot.providers)
        assertTrue(snapshot.hasConflict)
        assertTrue(snapshot.hasAuthoritativeSupport)
    }
}
