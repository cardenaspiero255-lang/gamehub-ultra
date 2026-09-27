package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraGeneralQueryRouterRegressionTest {

    @Test
    fun generalKnowledgeUsesVerifiedResearchWithoutPretendingItIsFresh() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, por qué el cielo es azul"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertTrue(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun generalScienceQuestionUsesVerifiedResearchInsteadOfGamingFallback() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, explícame qué es un agujero negro"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertTrue(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun domainSpecificFreshQueriesStillRequireOnlineResearch() {
        val weather = UltraGeneralQueryRouter.classify(
            "Ultra, clima de hoy en Santiago"
        )
        val price = UltraGeneralQueryRouter.classify(
            "Ultra, precio del RedMagic 12 Pro"
        )
        val release = UltraGeneralQueryRouter.classify(
            "Ultra, qué salió nuevo de Resident Evil"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, weather.kind)
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, price.kind)
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, release.kind)
        assertTrue(weather.requiresFreshData)
        assertTrue(price.requiresFreshData)
        assertTrue(release.requiresFreshData)
    }
}
