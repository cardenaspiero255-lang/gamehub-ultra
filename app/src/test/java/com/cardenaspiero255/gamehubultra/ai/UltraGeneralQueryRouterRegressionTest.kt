package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraGeneralQueryRouterRegressionTest {

    @Test
    fun genericTodayPhraseDoesNotForceOnlineResearch() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, qué hacemos hoy"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertFalse(request.requiresInternet)
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
