package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertTrue

class UltraResearchAbstentionMessageTest {
    @Test
    fun missingProvidersExplainThatNoVerifiedSourcesAreAvailable() {
        UltraVerifiedResearchEngine(emptyList()).use { engine ->
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, precio actual del teléfono")
            )

            assertTrue(result.abstained)
            assertTrue(result.message.contains("fuentes verificables", ignoreCase = true))
        }
    }

    @Test
    fun timeoutExplainsThatResearchTookTooLongInsteadOfGenericConfidenceFailure() {
        val provider = object : UltraResearchProvider {
            override val id = "slow"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                Thread.sleep(200)
                return UltraResearchEvidence(
                    claimKey = "price",
                    value = "100",
                    displayText = "100",
                    sourceId = "slow"
                )
            }
        }

        UltraVerifiedResearchEngine(listOf(provider)).use { engine ->
            val request = UltraGeneralQueryRouter
                .classify("Ultra, precio actual del teléfono")
                .copy(timeoutMillis = 20L)
            val result = engine.answer(request)

            assertTrue(result.abstained)
            assertTrue(result.timedOut)
            assertTrue(result.message.contains("tardó demasiado", ignoreCase = true))
        }
    }
}
