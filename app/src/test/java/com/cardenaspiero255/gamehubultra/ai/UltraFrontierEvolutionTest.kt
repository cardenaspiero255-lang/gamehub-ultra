package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingRecommendation
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import com.cardenaspiero255.gamehubultra.domain.ThermalSignalMode
import com.cardenaspiero255.gamehubultra.domain.ThermalTrend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraFrontierEvolutionTest {
    @Test
    fun synthesisDoesNotMistakeDifferentComparisonCandidatesForConflictingClaims() {
        val candidates = listOf(
            UltraQueryExecutionAnswer(
                message = "Candidato A",
                verified = true,
                confidence = UltraAnswerConfidence.HIGH,
                sources = listOf("a", "b"),
                independentSourceCount = 2,
                abstained = false
            ) to 12L,
            UltraQueryExecutionAnswer(
                message = "Candidato B",
                verified = true,
                confidence = UltraAnswerConfidence.MEDIUM,
                sources = listOf("c"),
                independentSourceCount = 1,
                abstained = false
            ) to 16L
        )

        val answer = assertNotNull(
            UltraFrontierEvolutionController().synthesizeResearch(candidates)
        )
        assertFalse(answer.abstained)
        assertTrue(answer.verified)
        assertEquals("Candidato A", answer.message)
        assertEquals(listOf("a", "b"), answer.sources)
    }

    @Test
    fun learningStorePrefersLaneWithBetterObservedOutcomes() {
        val learning = UltraFrontierLearningStore(minSamplesForPreference = 2)
        repeat(3) {
            learning.record(
                UltraFrontierExecutionOutcome(
                    domain = UltraFrontierDomain.COMPARISON,
                    lane = UltraFrontierLane.DEEP_RESEARCH,
                    accepted = true,
                    verified = true,
                    abstained = false,
                    latencyMillis = 900L,
                    reasonCode = null
                )
            )
        }
        repeat(2) {
            learning.record(
                UltraFrontierExecutionOutcome(
                    domain = UltraFrontierDomain.COMPARISON,
                    lane = UltraFrontierLane.VERIFIED_RESEARCH,
                    accepted = false,
                    verified = false,
                    abstained = true,
                    latencyMillis = 2_500L,
                    reasonCode = "INSUFFICIENT_CORROBORATION"
                )
            )
        }

        assertEquals(
            UltraFrontierLane.DEEP_RESEARCH,
            learning.preferredLane(
                UltraFrontierDomain.COMPARISON,
                setOf(
                    UltraFrontierLane.DEEP_RESEARCH,
                    UltraFrontierLane.VERIFIED_RESEARCH
                )
            )
        )
        assertTrue(
            learning.failurePressure(UltraFrontierDomain.COMPARISON) >= 1
        )
    }

    @Test
    fun providerRankerLearnsReliabilityLatencyAndDomain() {
        val ranker = UltraAdaptiveProviderRanker()
        repeat(3) {
            ranker.record(
                providerId = "fast-good",
                domain = UltraFrontierDomain.CURRENT_DATA,
                result = UltraProviderResult.Evidence(
                    UltraResearchEvidence(
                        claimKey = "weather",
                        value = "sunny",
                        displayText = "Soleado",
                        sourceId = "official",
                        independentSourceCount = 2,
                        authoritative = true
                    )
                ),
                latencyMillis = 120L
            )
        }
        repeat(3) {
            ranker.record(
                providerId = "slow-bad",
                domain = UltraFrontierDomain.CURRENT_DATA,
                result = UltraProviderResult.Failure(
                    reasonCode = "UPSTREAM_TIMEOUT",
                    retryable = true
                ),
                latencyMillis = 3_000L
            )
        }

        val providers = listOf(
            fakeProvider("slow-bad"),
            fakeProvider("fast-good")
        )

        assertEquals(
            listOf("fast-good", "slow-bad"),
            ranker.rank(providers, UltraFrontierDomain.CURRENT_DATA).map { it.id }
        )
    }

    @Test
    fun weightedConsensusPrefersIndependentAuthoritativeEvidence() {
        val decision = UltraWeightedConsensusEngine().decide(
            listOf(
                UltraWeightedEvidenceCandidate(
                    providerId = "official-a",
                    claimKey = "price",
                    value = "100",
                    displayText = "100",
                    sourceIds = setOf("a", "b"),
                    independentSourceCount = 2,
                    authoritative = true,
                    providerScore = 0.90
                ),
                UltraWeightedEvidenceCandidate(
                    providerId = "official-b",
                    claimKey = "price",
                    value = "100",
                    displayText = "100",
                    sourceIds = setOf("c"),
                    independentSourceCount = 1,
                    authoritative = true,
                    providerScore = 0.85
                ),
                UltraWeightedEvidenceCandidate(
                    providerId = "weak",
                    claimKey = "price",
                    value = "999",
                    displayText = "999",
                    sourceIds = setOf("x"),
                    independentSourceCount = 1,
                    authoritative = false,
                    providerScore = 0.15
                )
            )
        )

        assertTrue(decision.accepted)
        assertEquals("100", decision.value)
        assertTrue(decision.independentSourceCount >= 3)
        assertEquals(UltraAnswerConfidence.HIGH, decision.confidence)
    }

    @Test
    fun atomicClaimGateFailsClosedWhenAnswerExceedsVerificationCapacity() {
        val evolution = UltraFrontierEvolutionController(
            claimExtractor = UltraAtomicClaimExtractor(maximumClaims = 3)
        )
        val query = UltraGeneralQueryRouter.classify("noticias de Android hoy")
        val plan = UltraFrontierOrchestrator(evolution = evolution).plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )
        val answer = UltraQueryExecutionAnswer(
            message = "Uno. Dos. Tres. Cuatro.",
            verified = true,
            confidence = UltraAnswerConfidence.HIGH,
            sources = listOf("a", "b"),
            independentSourceCount = 2,
            abstained = false
        )

        val gated = evolution.finalGate(
            request = query,
            plan = plan,
            answer = answer,
            nowMillis = 1_000L
        )

        assertTrue(gated.abstained)
        assertFalse(gated.verified)
        assertEquals("FRONTIER_CLAIM_CAPACITY_EXCEEDED", gated.reasonCode)
    }

    @Test
    fun claimVerifierSeparatesVerifiedWeakStaleAndInference() {
        val verifier = UltraClaimVerifier()
        val now = 10_000L

        assertEquals(
            UltraClaimStatus.VERIFIED,
            verifier.verify(
                UltraFrontierClaim(
                    id = "verified",
                    text = "dato",
                    confidence = UltraAnswerConfidence.HIGH,
                    independentSourceCount = 2,
                    validUntilMillis = now + 1_000L
                ),
                minimumIndependentSources = 2,
                nowMillis = now
            )
        )
        assertEquals(
            UltraClaimStatus.WEAK,
            verifier.verify(
                UltraFrontierClaim(
                    id = "weak",
                    text = "dato",
                    confidence = UltraAnswerConfidence.HIGH,
                    independentSourceCount = 1
                ),
                minimumIndependentSources = 2,
                nowMillis = now
            )
        )
        assertEquals(
            UltraClaimStatus.STALE,
            verifier.verify(
                UltraFrontierClaim(
                    id = "stale",
                    text = "dato",
                    confidence = UltraAnswerConfidence.HIGH,
                    independentSourceCount = 3,
                    validUntilMillis = now - 1L
                ),
                minimumIndependentSources = 2,
                nowMillis = now
            )
        )
        assertEquals(
            UltraClaimStatus.INFERRED,
            verifier.verify(
                UltraFrontierClaim(
                    id = "inferred",
                    text = "estimación",
                    confidence = UltraAnswerConfidence.HIGH,
                    independentSourceCount = 3,
                    inferred = true
                ),
                minimumIndependentSources = 2,
                nowMillis = now
            )
        )
    }

    @Test
    fun temporalKnowledgeGraphResolvesFactValidAtRequestedTime() {
        val graph = UltraTemporalKnowledgeGraph()
        graph.upsert(
            UltraTemporalFact(
                subject = "device",
                predicate = "price",
                value = "100",
                validFromMillis = 1_000L,
                validUntilMillis = 1_999L,
                confidence = UltraAnswerConfidence.HIGH,
                sourceIds = setOf("a", "b")
            )
        )
        graph.upsert(
            UltraTemporalFact(
                subject = "device",
                predicate = "price",
                value = "120",
                validFromMillis = 2_000L,
                validUntilMillis = 3_000L,
                confidence = UltraAnswerConfidence.HIGH,
                sourceIds = setOf("c", "d")
            )
        )

        assertEquals("100", graph.resolve("device", "price", 1_500L)?.value)
        assertEquals("120", graph.resolve("device", "price", 2_500L)?.value)
        assertEquals(null, graph.resolve("device", "price", 4_000L))
    }

    @Test
    fun multiStagePlannerBuildsParallelResearchThenSynthesisAndVerification() {
        val planner = UltraFrontierTaskPlanner()
        val query = UltraGeneralQueryRouter.classify(
            "Compara dos teléfonos actuales y dime cuál es mejor"
        )
        val tasks = planner.plan(
            request = UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            ),
            lane = UltraFrontierLane.DEEP_RESEARCH
        )

        val researchTasks = tasks.filter {
            it.specialist == UltraFrontierSpecialist.RESEARCH
        }
        assertTrue(researchTasks.size >= 2)
        assertTrue(researchTasks.mapNotNull { it.parallelGroup }.distinct().size == 1)

        val synthesis = tasks.single {
            it.specialist == UltraFrontierSpecialist.SYNTHESIZER
        }
        assertTrue(researchTasks.all { it.id in synthesis.dependsOn })

        val verifier = tasks.single {
            it.specialist == UltraFrontierSpecialist.VERIFIER
        }
        assertTrue(synthesis.id in verifier.dependsOn)
    }

    @Test
    fun adaptiveComputeBudgetSpendsMoreOnHardQueriesButRespectsDevicePressure() {
        val budgeter = UltraAdaptiveComputeBudgeter()
        val policy = UltraFrontierPolicy()
        val hard = UltraGeneralQueryRouter.classify(
            "Compara profundamente dos teléfonos actuales y analiza diferencias"
        )
        val easy = UltraGeneralQueryRouter.classify("¿Qué es un libro?")

        val hardBudget = budgeter.budget(
            request = hard,
            policy = policy,
            failurePressure = 2,
            worldState = null
        )
        val easyBudget = budgeter.budget(
            request = easy,
            policy = policy,
            failurePressure = 0,
            worldState = null
        )

        assertTrue(hardBudget.sourceBudget >= easyBudget.sourceBudget)
        assertTrue(hardBudget.passBudget >= easyBudget.passBudget)
        assertTrue(hardBudget.timeBudgetMillis >= easyBudget.timeBudgetMillis)

        val constrained = budgeter.budget(
            request = hard,
            policy = policy,
            failurePressure = 2,
            worldState = UltraFrontierWorldState(
                selectedGamePackage = "game",
                sessionActive = true,
                selectedProfile = PerformanceProfile.X4,
                networkValidated = true,
                networkLatencyMs = 30L,
                batteryPercent = 12,
                charging = false,
                thermalStatus = 4,
                thermalHeadroom = 0.9f,
                thermalTrend = ThermalTrend.RISING_FAST,
                thermalRisk = ThermalRisk.CRITICAL,
                thermalConfidence = 0.95f,
                batteryRecommendation = BatteryGamingRecommendation.CONSERVE,
                preventAggressiveProfiles = true,
                adaptiveScore = 30,
                timestampMillis = 1_000L
            )
        )

        assertEquals(1, constrained.maxParallelism)
        assertTrue(constrained.sourceBudget <= hardBudget.sourceBudget)
    }

    @Test
    fun worldStateCombinesAiThermalBatteryAndAdaptiveSignals() {
        val context = GameHubAiContext(
            selectedGamePackage = "game.pkg",
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
            gpuAvailable = true,
            thermalStatus = 3,
            thermalHeadroom = 0.75f,
            batteryPercent = 32,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 28L,
            downstreamBandwidthKbps = 500_000L,
            storageFreePercent = 50,
            inputDeviceCount = 1,
            selectedProfile = PerformanceProfile.X4,
            sessionActive = true
        )
        val thermal = ThermalPrediction(
            trend = ThermalTrend.RISING,
            risk = ThermalRisk.HIGH,
            confidence = 0.88f,
            signalMode = ThermalSignalMode.HEADROOM_AND_STATUS,
            slopePerMinute = 0.1f,
            accelerationPerMinuteSquared = 0.02f,
            latestMeasuredHeadroom = 0.75f,
            projectedHeadroom = 0.82f,
            allowPreventiveSignal = true,
            recovering = false,
            evidence = emptyList(),
            reason = "test"
        )
        val battery = BatteryGamingAssessment(
            currentPercent = 32,
            charging = false,
            powerSaveMode = false,
            observedDropPercent = 5,
            observedDurationMillis = 600_000L,
            drainPercentPerHour = 30f,
            recommendation = BatteryGamingRecommendation.BALANCED,
            preventAggressiveProfiles = true,
            reason = "test"
        )
        val adaptive = AdaptiveDecision(
            profile = PerformanceProfile.BALANCED,
            enableSustainedPerformance = false,
            score = 55,
            reason = "test",
            changed = true
        )

        val world = UltraFrontierWorldState.from(
            context = context,
            thermalPrediction = thermal,
            batteryAssessment = battery,
            adaptiveDecision = adaptive,
            nowMillis = 5_000L
        )

        assertEquals("game.pkg", world.selectedGamePackage)
        assertEquals(ThermalRisk.HIGH, world.thermalRisk)
        assertEquals(BatteryGamingRecommendation.BALANCED, world.batteryRecommendation)
        assertEquals(55, world.adaptiveScore)
        assertTrue(world.preventAggressiveProfiles)
    }

    @Test
    fun continuousEvaluationTracksVerificationAbstentionLatencyAndFailures() {
        val evaluation = UltraFrontierEvaluationRegistry()
        evaluation.record(
            UltraFrontierExecutionOutcome(
                domain = UltraFrontierDomain.CURRENT_DATA,
                lane = UltraFrontierLane.VERIFIED_RESEARCH,
                accepted = true,
                verified = true,
                abstained = false,
                latencyMillis = 100L,
                reasonCode = null
            )
        )
        evaluation.record(
            UltraFrontierExecutionOutcome(
                domain = UltraFrontierDomain.CURRENT_DATA,
                lane = UltraFrontierLane.VERIFIED_RESEARCH,
                accepted = false,
                verified = false,
                abstained = true,
                latencyMillis = 300L,
                reasonCode = "UPSTREAM_TIMEOUT"
            )
        )

        val snapshot = evaluation.snapshot(UltraFrontierDomain.CURRENT_DATA)
        assertEquals(2, snapshot.total)
        assertEquals(1, snapshot.verified)
        assertEquals(1, snapshot.abstained)
        assertEquals(200L, snapshot.averageLatencyMillis)
        assertEquals(1, snapshot.failureReasons["UPSTREAM_TIMEOUT"])
    }

    @Test
    fun ensembleSelectorPrefersCorroboratedVerifiedCandidateOverFastWeakCandidate() {
        val selected = UltraFrontierEnsembleSelector().select(
            listOf(
                UltraFrontierEnsembleCandidate(
                    message = "rápida",
                    verified = false,
                    confidence = UltraAnswerConfidence.MEDIUM,
                    independentSourceCount = 0,
                    latencyMillis = 20L,
                    fresh = true,
                    abstained = false
                ),
                UltraFrontierEnsembleCandidate(
                    message = "corroborada",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    independentSourceCount = 3,
                    latencyMillis = 500L,
                    fresh = true,
                    abstained = false
                )
            )
        )

        assertNotNull(selected)
        assertEquals("corroborada", selected.message)
    }

    @Test
    fun deepResearchSynthesizerCombinesCompatibleIndependentEvidence() {
        val evolution = UltraFrontierEvolutionController()
        val synthesized = evolution.synthesizeResearch(
            candidates = listOf(
                UltraQueryExecutionAnswer(
                    message = "La respuesta corroborada.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-a"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 300L,
                UltraQueryExecutionAnswer(
                    message = "La respuesta corroborada.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-b"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 400L
            )
        )

        assertNotNull(synthesized)
        assertTrue(synthesized.verified)
        assertEquals(2, synthesized.independentSourceCount)
        assertEquals(setOf("source-a", "source-b"), synthesized.sources.toSet())
        assertEquals("frontier-synthesizer", synthesized.stage)
    }

    @Test
    fun deepResearchSynthesizerNeverMergesContradictoryAnswers() {
        val evolution = UltraFrontierEvolutionController()
        val synthesized = evolution.synthesizeResearch(
            candidates = listOf(
                UltraQueryExecutionAnswer(
                    message = "A",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-a"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 300L,
                UltraQueryExecutionAnswer(
                    message = "B",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-b"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 400L
            )
        )

        assertNotNull(synthesized)
        assertEquals(1, synthesized.independentSourceCount)
        assertTrue(synthesized.sources.size == 1)
    }

    @Test
    fun synthesisProvenanceBlocksConflictingVerifiedClaimsAtRuntime() {
        val evolution = UltraFrontierEvolutionController()
        val selected = evolution.synthesizeResearch(
            candidates = listOf(
                UltraQueryExecutionAnswer(
                    message = "El evento ocurrió en 2025.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("https://one.example/report"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 80L,
                UltraQueryExecutionAnswer(
                    message = "El evento ocurrió en 2024.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("https://two.example/report"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 85L
            )
        )
        assertNotNull(selected)
        assertFalse(selected.verified)
        assertTrue(selected.abstained)
        assertEquals("FRONTIER_CLAIM_PROVENANCE_CONFLICT", selected.reasonCode)
    }

    @Test
    fun synthesisProvenancePreservesAgreementWithoutInventingExtraSources() {
        val evolution = UltraFrontierEvolutionController()
        val agreed = evolution.synthesizeResearch(
            candidates = listOf(
                UltraQueryExecutionAnswer(
                    message = "El objeto tiene masa.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("https://one.example/a"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 100L,
                UltraQueryExecutionAnswer(
                    message = "El objeto tiene masa.",
                    verified = true,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("https://two.example/b"),
                    independentSourceCount = 1,
                    abstained = false
                ) to 120L
            )
        )
        assertNotNull(agreed)
        assertTrue(agreed.verified)
        assertFalse(agreed.abstained)
        assertEquals(2, agreed.sources.size)
        assertEquals(2, agreed.independentSourceCount)
    }

    @Test
    fun evolutionEnsemblePrefersVerifiedResearchOverWeakLocalCandidate() {
        val evolution = UltraFrontierEvolutionController()

        val selected = evolution.selectEnsemble(
            local = UltraQueryExecutionAnswer(
                message = "respuesta local",
                verified = false,
                confidence = UltraAnswerConfidence.MEDIUM,
                independentSourceCount = 0,
                abstained = false
            ),
            research = UltraQueryExecutionAnswer(
                message = "respuesta corroborada",
                verified = true,
                confidence = UltraAnswerConfidence.HIGH,
                sources = listOf("a", "b"),
                independentSourceCount = 2,
                abstained = false
            ),
            localLatencyMillis = 10L,
            researchLatencyMillis = 400L,
            requiresFreshData = false
        )

        assertEquals("respuesta corroborada", selected?.message)
        assertTrue(selected?.verified == true)
    }

    @Test
    fun evolutionFinalGateRejectsVerifiedAnswerThatDoesNotMeetClaimQuorum() {
        val evolution = UltraFrontierEvolutionController()
        val query = UltraGeneralQueryRouter.classify("noticias de Android hoy")
        val plan = UltraFrontierOrchestrator(evolution = evolution).plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )

        val gated = evolution.finalGate(
            request = query,
            plan = plan,
            answer = UltraQueryExecutionAnswer(
                message = "Dato candidato",
                verified = true,
                confidence = UltraAnswerConfidence.HIGH,
                sources = listOf("one"),
                independentSourceCount = 1,
                abstained = false
            ),
            nowMillis = 1_000L
        )

        assertTrue(gated.abstained)
        assertFalse(gated.verified)
        assertEquals("FRONTIER_CLAIM_QUORUM", gated.reasonCode)
    }

    @Test
    fun evolutionControllerExposesExecutableTaskGraphForTheChosenLane() {
        val evolution = UltraFrontierEvolutionController()
        val query = UltraGeneralQueryRouter.classify(
            "Compara profundamente dos teléfonos actuales"
        )
        val request = UltraFrontierRequest(
            message = query.originalText,
            query = query,
            networkAvailable = true,
            memoryAvailable = true,
            telemetryAvailable = true
        )

        val tasks = evolution.tasks(
            request = request,
            lane = UltraFrontierLane.DEEP_RESEARCH
        )

        assertTrue(tasks.any { it.specialist == UltraFrontierSpecialist.RESEARCH })
        assertTrue(tasks.any { it.specialist == UltraFrontierSpecialist.SYNTHESIZER })
        assertTrue(tasks.any { it.specialist == UltraFrontierSpecialist.VERIFIER })
        assertTrue(tasks.any { it.specialist == UltraFrontierSpecialist.CRITIC })
    }

    @Test
    fun productionEvolutionUsesVerifiedTemporalKnowledgeBeforeCallingResearchAgain() {
        var calls = 0
        var now = 1_000L
        val evolution = UltraFrontierEvolutionController()
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "ChatGPT es un asistente de IA.",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("official"),
                    independentSourceCount = 2,
                    abstained = false
                )
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = UltraFrontierOrchestrator(evolution = evolution),
            nowMillis = { now }
        )
        val request = UltraGeneralQueryRouter
            .classify("¿Qué es ChatGPT?")
            .copy(verificationMode = UltraVerificationMode.REQUIRED)

        val first = engine.answer(request) { null }
        now += 1_000L
        val second = engine.answer(request) { null }

        assertTrue(first.verified)
        assertTrue(second.verified)
        assertEquals(2, second.independentSourceCount)
        assertEquals(1, calls)
        assertEquals("frontier-knowledge-graph", second.stage)
    }

    @Test
    fun productionEvolutionDoesNotReuseExpiredTemporalKnowledge() {
        var calls = 0
        var now = 1_000L
        val evolution = UltraFrontierEvolutionController()
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "Actualización $calls",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("official-$calls"),
                    independentSourceCount = 2,
                    abstained = false
                )
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = UltraFrontierOrchestrator(evolution = evolution),
            nowMillis = { now }
        )
        val request = UltraGeneralQueryRouter.classify("noticias de Android hoy")

        engine.answer(request) { null }
        now += UltraResearchCache.CURRENT_DATA_TTL_MS + 1L
        engine.answer(request) { null }

        assertEquals(2, calls)
    }

    @Test
    fun deepResearchBranchesUseDisjointProviderPartitions() {
        val observed = java.util.Collections.synchronizedList(
            mutableListOf<Pair<Int, Int?>>()
        )
        val gateway = object : UltraResearchGateway {
            override val supportsProviderPartitioning: Boolean = true

            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                observed += request.researchProviderOffset to request.researchProviderBudget
                return UltraVerifiedResearchResult(
                    message = "Respuesta corroborada.",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-" + request.researchProviderOffset),
                    independentSourceCount = 2,
                    abstained = false
                )
            }
        }
        val evolution = UltraFrontierEvolutionController()
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = UltraFrontierOrchestrator(evolution = evolution)
        )

        engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara profundamente dos teléfonos actuales"
            )
        ) { null }

        val sorted = observed.sortedBy { it.first }
        assertTrue(sorted.size >= 2)
        val firstBudget = assertNotNull(sorted[0].second)
        assertTrue(firstBudget > 0)
        assertTrue(sorted[1].first >= sorted[0].first + firstBudget)
    }

    @Test
    fun specialistExecutorHonorsDependenciesAcrossParallelResearchTasks() {
        val query = UltraGeneralQueryRouter.classify(
            "Compara dos teléfonos actuales y dime cuál es mejor"
        )
        val tasks = UltraFrontierTaskPlanner().plan(
            request = UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            ),
            lane = UltraFrontierLane.DEEP_RESEARCH
        )
        val completed = java.util.Collections.synchronizedSet(
            linkedSetOf<String>()
        )

        val results = UltraFrontierSpecialistExecutor().execute(
            tasks = tasks,
            maxParallelism = 4,
            timeoutMillis = 5_000L
        ) { task ->
            if (task.id == "synthesize") {
                assertTrue("research-a" in completed)
                assertTrue("research-b" in completed)
            }
            completed += task.id
            task.specialist.name
        }

        assertEquals(tasks.size, results.size)
        assertTrue("critic" in completed)
    }

    @Test
    fun successfulVerifiedHistoryCanPromoteOptionalKnowledgeToResearchLane() {
        val evolution = UltraFrontierEvolutionController(
            learning = UltraFrontierLearningStore(
                minSamplesForPreference = 2
            )
        )
        repeat(2) {
            evolution.record(
                UltraFrontierExecutionOutcome(
                    domain = UltraFrontierDomain.GENERAL_KNOWLEDGE,
                    lane = UltraFrontierLane.VERIFIED_RESEARCH,
                    accepted = true,
                    verified = true,
                    abstained = false,
                    latencyMillis = 300L,
                    reasonCode = null
                )
            )
        }
        val query = UltraGeneralQueryRouter.classify("¿Qué es un libro?")
        val plan = UltraFrontierOrchestrator(
            evolution = evolution
        ).plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )

        assertEquals(UltraFrontierLane.VERIFIED_RESEARCH, plan.lane)
        assertTrue(plan.tasks.any {
            it.specialist == UltraFrontierSpecialist.RESEARCH
        })
    }

    @Test
    fun researchPartitionCapabilityIsExplicitAndForwardedByCoordinator() {
        val generic = object : UltraResearchGateway {
            override fun answer(
                request: UltraGeneralQueryRequest
            ): UltraVerifiedResearchResult =
                UltraVerifiedResearchResult(
                    message = "generic",
                    confidence = UltraAnswerConfidence.MEDIUM,
                    abstained = false
                )
        }

        assertFalse(generic.supportsProviderPartitioning)
        UltraVerifiedResearchEngine(
            providers = listOf(fakeProvider("partitioned"))
        ).use { engine ->
            assertTrue(engine.supportsProviderPartitioning)
            assertTrue(
                UltraQueryExecutionCoordinator(engine)
                    .supportsProviderPartitioning
            )
        }
    }

    @Test
    fun weightedConsensusUsesDeclaredIndependenceWhenSourceIdsAreUnavailable() {
        val decision = UltraWeightedConsensusEngine().decide(
            listOf(
                UltraWeightedEvidenceCandidate(
                    providerId = "opaque-provider",
                    claimKey = "claim",
                    value = "same",
                    displayText = "Dato corroborado.",
                    sourceIds = emptySet(),
                    independentSourceCount = 2,
                    authoritative = false,
                    providerScore = 0.8
                )
            )
        )

        assertTrue(decision.accepted)
        assertEquals(2, decision.independentSourceCount)
        assertEquals(UltraAnswerConfidence.HIGH, decision.confidence)
    }

    @Test
    fun temporalKnowledgeGraphSnapshotAndConfidenceOrderingStayDeterministic() {
        val graph = UltraTemporalKnowledgeGraph()
        graph.upsert(
            UltraTemporalFact(
                subject = "chatgpt",
                predicate = "answer",
                value = "low",
                validFromMillis = 1_000L,
                validUntilMillis = 10_000L,
                confidence = UltraAnswerConfidence.LOW
            )
        )
        graph.upsert(
            UltraTemporalFact(
                subject = "chatgpt",
                predicate = "answer",
                value = "high",
                validFromMillis = 2_000L,
                validUntilMillis = 10_000L,
                confidence = UltraAnswerConfidence.HIGH
            )
        )

        assertEquals(2, graph.snapshot().size)
        assertEquals(
            "high",
            graph.resolve(
                subject = "chatgpt",
                predicate = "answer",
                atMillis = 3_000L
            )?.value
        )
    }

    private fun fakeProvider(providerId: String): UltraResearchProvider =
        object : UltraResearchProvider {
            override val id: String = providerId

            override fun fetch(
                request: UltraGeneralQueryRequest
            ): UltraResearchEvidence =
                error("not used")
        }
}
