package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraCar73ReviewRegressionTest {

    @Test
    fun triangleRejectsNegativeOrZeroInputAngles() {
        assertNull(
            UltraMathEngine.solve(
                "Ultra, en un triángulo tengo -10 grados y 20 grados, cuál falta"
            )
        )
        assertNull(
            UltraMathEngine.solve(
                "Ultra, en un triángulo tengo 0 grados y 20 grados, cuál falta"
            )
        )
    }

    @Test
    fun verifiedResearchReturnsEvidenceSourceIdentifiers() {
        val provider = object : UltraResearchProvider {
            override val id = "weather-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "weather:santiago",
                    value = "22|sunny",
                    displayText = "Santiago: 22 °C y despejado.",
                    sourceId = "https://weather.example/current/santiago",
                    authoritative = true
                )
        }

        UltraVerifiedResearchEngine(listOf(provider)).use { engine ->
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, clima de hoy en Santiago")
            )

            assertEquals(
                listOf("https://weather.example/current/santiago"),
                result.sources
            )
        }
    }
}
