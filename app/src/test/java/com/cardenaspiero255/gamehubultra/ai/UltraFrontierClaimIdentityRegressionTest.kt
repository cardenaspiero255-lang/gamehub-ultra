package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class UltraFrontierClaimIdentityRegressionTest {
    private fun checked(message: String, source: String) =
        UltraQueryExecutionAnswer(
            message = message,
            verified = true,
            abstained = false,
            confidence = UltraAnswerConfidence.HIGH,
            sources = listOf(source),
            independentSourceCount = 1
        )

    @Test
    fun contradictoryRevolutionYearsAreNotAcceptedAsVerified() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("La Revolución comenzó en 1789.", "source-a") to 100L,
                checked("La Revolución comenzó en 1790.", "source-b") to 110L
            )
        ))
        assertEquals("FRONTIER_CLAIM_PROVENANCE_CONFLICT", result.reasonCode)
        assertFalse(result.verified)
    }

    @Test
    fun isoDateDisagreementsAlsoTriggerConflict() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("El evento ocurrió el 2024-10-05.", "source-a") to 100L,
                checked("El evento ocurrió el 2024-10-06.", "source-b") to 110L
            )
        ))
        assertEquals("FRONTIER_CLAIM_PROVENANCE_CONFLICT", result.reasonCode)
    }

    @Test
    fun modelNumbersArePartOfSubjectNotDisputedPrice() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("El iPhone 15 cuesta 800 euros.", "shop-iphone-15") to 100L,
                checked("El iPhone 16 cuesta 900 euros.", "shop-iphone-16") to 110L
            )
        ))
        assertFalse(result.abstained)
        assertEquals(null, result.reasonCode)
    }

    @Test
    fun sameModelDifferentPriceIsContradiction() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("El iPhone 15 cuesta 800 euros.", "shop-a") to 100L,
                checked("El iPhone 15 cuesta 900 euros.", "shop-b") to 110L
            )
        ))
        assertEquals("FRONTIER_CLAIM_PROVENANCE_CONFLICT", result.reasonCode)
    }

    @Test
    fun equivalentThousandsAndDecimalNotationMustNotConflict() {
        val variants = listOf(
            "1.299" to "1299",
            "5,000" to "5000",
            "800,0" to "800",
            "1.234,50" to "1234,5"
        )
        variants.forEach { (left, right) ->
            val selected = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
                listOf(
                    checked("El móvil cuesta $left euros.", "store-one") to 100L,
                    checked("El móvil cuesta $right euros.", "store-two") to 110L
                )
            ))
            assertFalse(selected.abstained, "Matching values $left and $right must agree")
            assertEquals(null, selected.reasonCode)
        }
    }

    @Test
    fun mixedGroupingAndDecimalSeparatorsCompareAtEqualValue() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("La medición fue 1.234,567 unidades.", "lab-one") to 100L,
                checked("La medición fue 1234,567 unidades.", "lab-two") to 110L
            )
        ))
        assertFalse(result.abstained)
        assertEquals(null, result.reasonCode)
    }

    @Test
    fun distinctPricesMustRemainConflictingAfterCanonicalization() {
        val selected = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("El móvil cuesta 1.299 euros.", "store-one") to 100L,
                checked("El móvil cuesta 1298 euros.", "store-two") to 110L
            )
        ))
        assertEquals("FRONTIER_CLAIM_PROVENANCE_CONFLICT", selected.reasonCode)
    }

    @Test
    fun accentsAndPunctuationDoNotInventNumericalDisagreement() {
        val result = assertNotNull(UltraFrontierEvolutionController().synthesizeResearch(
            listOf(
                checked("La batería tiene 5000 mAh.", "source-a") to 100L,
                checked("La bateria tiene 5000 mAh", "source-b") to 110L
            )
        ))
        assertFalse(result.abstained)
        assertEquals(null, result.reasonCode)
    }
}
