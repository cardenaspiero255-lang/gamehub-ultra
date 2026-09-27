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
    fun fullGameHubInvocationIdentityQuestionStaysLocal() {
        val request = UltraGeneralQueryRouter.classify(
            "GameHub Ultra, who are you?"
        )

        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun standaloneEnglishIdentityQuestionStaysLocal() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, who are you?"
        )

        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun appContextQuestionStaysOnLocalChatPath() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es mi juego seleccionado"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun factualQuestionWinsOverMixedAppContextPrompt() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es mi juego seleccionado y qué es Vulkan"
        )

        assertTrue(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun factualQuestionWinsOverMixedIdentityPrompt() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, quién eres y qué es Vulkan"
        )

        assertTrue(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun factualQuestionWinsOverGreetingOrThanks() {
        val greetingQuestion = UltraGeneralQueryRouter.classify(
            "Ultra, hola, explícame qué es Vulkan"
        )
        val thanksQuestion = UltraGeneralQueryRouter.classify(
            "Gracias, ¿por qué el cielo es azul?"
        )

        assertTrue(greetingQuestion.requiresInternet)
        assertTrue(thanksQuestion.requiresInternet)
        assertFalse(greetingQuestion.requiresFreshData)
        assertFalse(thanksQuestion.requiresFreshData)
    }

    @Test
    fun gameTitleContainingWhyDoesNotTriggerResearch() {
        val title = UltraGeneralQueryRouter.classify(
            "Ultra, háblame de Tell Me Why"
        )
        val actualQuestion = UltraGeneralQueryRouter.classify(
            "Ultra, why is the sky blue?"
        )

        assertFalse(title.requiresInternet)
        assertTrue(actualQuestion.requiresInternet)
    }

    @Test
    fun technicalTroubleshootingUsesVerifiedResearch() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cómo soluciono un error de Gradle al compilar Android"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertTrue(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun commonFactualInterrogativesUseVerifiedResearch() {
        val spanishCount = UltraGeneralQueryRouter.classify(
            "Ultra, cuántos planetas hay en el sistema solar"
        )
        val spanishName = UltraGeneralQueryRouter.classify(
            "Ultra, cómo se llama la capital de Francia"
        )
        val englishCount = UltraGeneralQueryRouter.classify(
            "Ultra, how many moons does Mars have?"
        )

        assertTrue(spanishCount.requiresInternet)
        assertTrue(spanishName.requiresInternet)
        assertTrue(englishCount.requiresInternet)
        assertFalse(spanishCount.requiresFreshData)
        assertFalse(spanishName.requiresFreshData)
        assertFalse(englishCount.requiresFreshData)
    }

    @Test
    fun definitionOfFreshKeywordUsesGeneralKnowledge() {
        val weatherDefinition = UltraGeneralQueryRouter.classify(
            "Ultra, qué es el clima"
        )
        val priceDefinition = UltraGeneralQueryRouter.classify(
            "Ultra, what is price?"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, weatherDefinition.kind)
        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, priceDefinition.kind)
        assertFalse(weatherDefinition.requiresFreshData)
        assertFalse(priceDefinition.requiresFreshData)
    }

    @Test
    fun explicitlyCurrentFactualQuestionIsMarkedFresh() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es la versión actual de Android"
        )

        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
    }

    @Test
    fun ordinaryConversationStaysOnLocalChatPath() {
        val greeting = UltraGeneralQueryRouter.classify(
            "Ultra, hola, cómo estás"
        )
        val casual = UltraGeneralQueryRouter.classify(
            "Ultra, hoy estoy aburrido"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, greeting.kind)
        assertFalse(greeting.requiresInternet)
        assertFalse(casual.requiresInternet)
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
