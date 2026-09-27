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
                    originalText = "precio actual",
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
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
}
