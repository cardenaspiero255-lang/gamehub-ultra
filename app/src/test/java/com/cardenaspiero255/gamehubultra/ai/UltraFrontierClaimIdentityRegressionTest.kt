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
