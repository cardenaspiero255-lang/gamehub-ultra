package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EVO Frontier V3, phase 4: a current comparison must not infer live truth
 * from a single authority or conceal conflicting prices behind a majority.
 * These are source-contract tests; no network or invented quotes are used.
 */
class UltraPhase4FreshEvidenceTrustTest {
    private fun provider(
        id: String,
        claim: String,
        value: String,
        authoritative: Boolean = true
    ): UltraResearchProvider = object : UltraResearchProvider {
        override val id: String = id

        override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
            UltraResearchEvidence(
                claimKey = claim,
                value = value,
                displayText = "Dato verificado: $value",
                sourceId = "https://evidence.example/$id",
                independentSourceCount = 1,
                authoritative = authoritative
            )
    }

    @Test
    fun currentComparisonDoesNotAcceptSingleAuthorityAsIndependentCorroboration() {
        val engine = UltraVerifiedResearchEngine(
            listOf(provider("seller", "redmagic:price", "999"))
        )
        try {
            val request = UltraGeneralQueryRouter.classify(
                "Ultra, compara el precio actual RedMagic 11S Pro vs RedMagic 12 Pro"
            )
            assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, request.kind)
            assertTrue(request.requiresFreshData)

            val result = engine.answer(request)
            assertTrue(result.abstained)
            assertEquals("INSUFFICIENT_CORROBORATION", result.reasonCode)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun contradictoryCurrentComparisonMustNotHideMinorityPriceBehindMajority() {
        val engine = UltraVerifiedResearchEngine(
            listOf(
                provider("seller-a", "redmagic:price", "999"),
                provider("seller-b", "redmagic:price", "999"),
                provider("seller-c", "redmagic:price", "1199")
            )
        )
        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, compara precios actuales de RedMagic 11S Pro vs 12 Pro"
                )
            )
            assertTrue(result.abstained)
            assertEquals("CONFLICTING_EVIDENCE", result.reasonCode)
            assertTrue(result.message.contains("contradic", ignoreCase = true))
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
            assertEquals(3, result.sources.size)
        } finally {
            engine.close()
        }
    }

    @Test
    fun twoIndependentSourcesAgreeingOnCurrentComparisonCanAnswer() {
        val engine = UltraVerifiedResearchEngine(
            listOf(
                provider("seller-a", "redmagic:price", "999"),
                provider("seller-b", "redmagic:price", "999")
            )
        )
        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, compara el precio actual de RedMagic 11S Pro vs 12 Pro"
                )
            )
            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
            assertEquals(2, result.independentSourceCount)
        } finally {
            engine.close()
        }
    }

    @Test
    fun stableComparisonCanStillUseOneAuthoritativeReference() {
        val engine = UltraVerifiedResearchEngine(
            listOf(provider("science-reference", "science:process", "photosynthesis"))
        )
        try {
            val request = UltraGeneralQueryRouter.classify(
                "Ultra, compara fotosíntesis vs respiración celular"
            )
            assertFalse(request.requiresFreshData)
            val result = engine.answer(request)
            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.MEDIUM, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun contradictoryCurrentMarketDataAbstainsEvenIfTwoOfThreeAgree() {
        val engine = UltraVerifiedResearchEngine(
            listOf(
                provider("exchange-a", "bitcoin:clp", "100000000"),
                provider("exchange-b", "bitcoin:clp", "100000000"),
                provider("exchange-c", "bitcoin:clp", "115000000")
            )
        )
        try {
            val request = UltraGeneralQueryRouter.classify(
                "Ultra, cuánto vale Bitcoin ahora"
            )
            assertTrue(request.requiresFreshData)
            val result = engine.answer(request)
            assertTrue(result.abstained)
            assertEquals("CONFLICTING_EVIDENCE", result.reasonCode)
        } finally {
            engine.close()
        }
    }
}
