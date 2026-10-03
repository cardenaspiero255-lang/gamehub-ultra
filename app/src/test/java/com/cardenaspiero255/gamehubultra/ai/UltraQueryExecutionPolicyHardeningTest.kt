package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraQueryExecutionPolicyHardeningTest {

    @Test
    fun freshDataFlagForcesVerifiedResearchEvenIfInternetFlagIsFalse() {
        val provider = fixedProvider(
            text = "Dato fresco verificado",
            authoritative = true
        )
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        try {
            val answer = coordinator.answer(
                request = UltraGeneralQueryRequest(
                    originalText = "dato estable marcado explícitamente como fresco",
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = true,
                    timeoutMillis = 5_000L
                ),
                localChat = {
                    localCalls += 1
                    "dato local viejo"
                }
            )

            assertEquals("Dato fresco verificado", answer.message)
            assertTrue(answer.verified)
            assertEquals(0, localCalls)
        } finally {
            engine.close()
        }
    }

    @Test
    fun currentDataKindNeverFallsBackToLocalWhenFlagsAreInconsistent() {
        val engine = UltraVerifiedResearchEngine(emptyList())
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        try {
            val answer = coordinator.answer(
                request = UltraGeneralQueryRequest(
                    originalText = "clima de hoy",
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = 5_000L
                ),
                localChat = {
                    localCalls += 1
                    "clima local posiblemente viejo"
                }
            )

            assertTrue(answer.abstained)
            assertFalse(answer.verified)
            assertEquals(0, localCalls)
        } finally {
            engine.close()
        }
    }

    @Test
    fun comparisonKindForcesResearchEvenIfInternetFlagIsFalse() {
        val provider = fixedProvider(
            text = "Comparación verificada",
            authoritative = true
        )
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        try {
            val answer = coordinator.answer(
                request = UltraGeneralQueryRequest(
                    originalText = "compara A con B",
                    kind = UltraGeneralQueryKind.COMPARISON_RESEARCH,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = 5_000L
                ),
                localChat = {
                    localCalls += 1
                    "comparación local"
                }
            )

            assertEquals("Comparación verificada", answer.message)
            assertTrue(answer.verified)
            assertEquals(0, localCalls)
        } finally {
            engine.close()
        }
    }

    @Test
    fun stableGeneralKnowledgeCanStillStayLocalWhenResearchIsNotRequired() {
        var providerCalls = 0
        val provider = object : UltraResearchProvider {
            override val id = "should-not-run"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                providerCalls += 1
                return UltraResearchEvidence(
                    claimKey = "general",
                    value = "online",
                    displayText = "respuesta online",
                    sourceId = id,
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)

        try {
            val answer = coordinator.answer(
                request = UltraGeneralQueryRequest(
                    originalText = "explica una idea estable",
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = 5_000L
                ),
                localChat = { "respuesta local estable" }
            )

            assertEquals("respuesta local estable", answer.message)
            assertFalse(answer.verified)
            assertEquals(0, providerCalls)
        } finally {
            engine.close()
        }
    }

    @Test
    fun blankStableLocalAnswerAbstainsWithoutCallingResearch() {
        var providerCalls = 0
        val provider = object : UltraResearchProvider {
            override val id = "should-not-run"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                providerCalls += 1
                return UltraResearchEvidence(
                    claimKey = "general",
                    value = "online",
                    displayText = "respuesta online",
                    sourceId = id,
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)

        try {
            val answer = coordinator.answer(
                request = UltraGeneralQueryRequest(
                    originalText = "pregunta local estable",
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = 5_000L
                ),
                localChat = { "   " }
            )

            assertTrue(answer.abstained)
            assertFalse(answer.verified)
            assertEquals(0, providerCalls)
        } finally {
            engine.close()
        }
    }


    @Test
    fun stableKnowledgeFallsBackEvenWhenResearchHasPartialSources() {
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult =
                UltraVerifiedResearchResult(
                    message = "No encontré fuentes suficientes para confirmar ese dato.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = listOf("https://es.wikipedia.org/wiki/Motor"),
                    abstained = true
                )
        }
        val coordinator = UltraQueryExecutionCoordinator(gateway)

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?"),
            localChat = {
                "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico."
            }
        )

        assertFalse(answer.abstained)
        assertFalse(answer.verified)
        assertTrue(answer.fallbackUsed)
        assertTrue(answer.message.startsWith("Un motor"))
    }

    private fun fixedProvider(
        text: String,
        authoritative: Boolean
    ): UltraResearchProvider =
        object : UltraResearchProvider {
            override val id = "verified-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "claim",
                    value = "verified",
                    displayText = text,
                    sourceId = id,
                    authoritative = authoritative
                )
        }
    @Test
    fun freshGeneralKnowledgeDoesNotReuseStableCacheEntry() {
        var calls = 0
        val provider = object : UltraResearchProvider {
            override val id = "changing-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                calls += 1
                return UltraResearchEvidence(
                    claimKey = "claim",
                    value = "value-$calls",
                    displayText = "Respuesta $calls",
                    sourceId = id,
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))

        try {
            val stable = UltraGeneralQueryRequest(
                originalText = "mismo tema",
                kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                requiresInternet = true,
                requiresFreshData = false,
                timeoutMillis = 5_000L
            )
            val fresh = stable.copy(requiresFreshData = true)

            val first = engine.answer(stable)
            val second = engine.answer(fresh)

            assertEquals("Respuesta 1", first.message)
            assertEquals("Respuesta 2", second.message)
            assertFalse(second.fromCache)
            assertEquals(2, calls)
        } finally {
            engine.close()
        }
    }


}
