package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraCar73ResearchHardeningTest {

    @Test
    fun triangleRejectsZeroOrNegativeAngles() {
        assertNull(
            UltraMathEngine.solve(
                "Ultra, triángulo con -10 grados y 20 grados, cuánto mide el tercero"
            )
        )
        assertNull(
            UltraMathEngine.solve(
                "Ultra, triángulo con 0 grados y 35 grados, cuánto mide el tercero"
            )
        )
    }

    @Test
    fun verifiedResultExposesEvidenceSourceIdInsteadOfProviderId() {
        val provider = object : UltraResearchProvider {
            override val id = "weather-provider"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "weather",
                    value = "22-sunny",
                    displayText = "22 °C y despejado",
                    sourceId = "https://api.open-meteo.com/",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, clima de hoy")
        )

        assertEquals(listOf("https://api.open-meteo.com/"), result.sources)
        engine.close()
    }
}
