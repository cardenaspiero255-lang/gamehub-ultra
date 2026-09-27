package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraCar73FinalReviewRegressionTest {

    @Test
    fun productAveragePriceDoesNotEnterStatisticsFastPath() {
        assertNull(
            UltraMathEngine.solve(
                "Ultra, precio promedio del Galaxy S26 Ultra 256 GB"
            )
        )
    }

    @Test
    fun sameDomainUrlsDoNotCountAsIndependentCorroboration() {
        val provider = object : UltraResearchProvider {
            override val id = "single-source"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "spec:phone",
                    value = "battery:5000",
                    displayText = "Batería 5000 mAh.",
                    sourceId = "https://example.com/specs/one",
                    supportingSourceIds = listOf("https://example.com/specs/two"),
                    independentSourceCount = 1,
                    authoritative = false
                )
        }

        UltraVerifiedResearchEngine(listOf(provider)).use { engine ->
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, compara Phone A vs Phone B"
                )
            )

            assertTrue(result.abstained)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        }
    }

    @Test
    fun backendRequestSeparatesCurrentQuestionFromPriorContext() {
        val request = UltraGeneralQueryRouter
            .classify(
                "Contexto previo: Ultra, clima de hoy en Santiago\n" +
                    "Pregunta actual: Ultra, y clima en Rancagua?"
            )
            .copy(
                originalText =
                    "Contexto previo: Ultra, clima de hoy en Santiago\n" +
                        "Pregunta actual: Ultra, y clima en Rancagua?"
            )

        val encoded = UltraResearchJsonCodec.encodeRequest(request)

        assertTrue(encoded.contains("\"query\":\"Ultra, y clima en Rancagua?\""))
        assertTrue(encoded.contains("\"context\":\"Ultra, clima de hoy en Santiago\""))
        assertFalse(
            encoded.contains(
                "\"query\":\"Contexto previo: Ultra, clima de hoy en Santiago"
            )
        )
    }
}
