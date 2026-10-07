package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraGeneralQueryRouterRegressionTest {

    @Test
    fun stableDefinitionIsOptionalInsteadOfInternetRequired() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, ¿qué es un motor?"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun generalKnowledgeUsesOptionalVerificationWithoutPretendingItIsFresh() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, por qué el cielo es azul"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun generalScienceQuestionUsesOptionalKnowledgeRoute() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, explícame qué es un agujero negro"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun fullGameHubInvocationIdentityQuestionStaysLocal() {
        val request = UltraGeneralQueryRouter.classify(
            "GameHub Ultra, who are you?"
        )

        assertEquals(UltraVerificationMode.LOCAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun standaloneEnglishIdentityQuestionStaysLocal() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, who are you?"
        )

        assertEquals(UltraVerificationMode.LOCAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun appContextQuestionStaysOnLocalChatPath() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es mi juego seleccionado"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertEquals(UltraVerificationMode.LOCAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun factualQuestionWinsOverMixedAppContextPrompt() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es mi juego seleccionado y qué es Vulkan"
        )

        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun factualQuestionWinsOverMixedIdentityPrompt() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, quién eres y qué es Vulkan"
        )

        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
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

        assertEquals(UltraVerificationMode.OPTIONAL, greetingQuestion.verificationMode)
        assertEquals(UltraVerificationMode.OPTIONAL, thanksQuestion.verificationMode)
        assertFalse(greetingQuestion.requiresInternet)
        assertFalse(thanksQuestion.requiresInternet)
        assertFalse(greetingQuestion.requiresFreshData)
        assertFalse(thanksQuestion.requiresFreshData)
    }

    @Test
    fun conversationalGameTitleContainingWhyStaysAStableTopicRequest() {
        val title = UltraGeneralQueryRouter.classify(
            "Ultra, háblame de Tell Me Why"
        )
        val actualQuestion = UltraGeneralQueryRouter.classify(
            "Ultra, why is the sky blue?"
        )

        assertEquals(UltraVerificationMode.OPTIONAL, title.verificationMode)
        assertEquals(UltraVerificationMode.OPTIONAL, actualQuestion.verificationMode)
        assertFalse(title.requiresInternet)
        assertFalse(actualQuestion.requiresInternet)
        assertFalse(title.requiresFreshData)
    }

    @Test
    fun technicalTroubleshootingUsesOptionalKnowledgeRoute() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cómo soluciono un error de Gradle al compilar Android"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
        assertFalse(request.requiresInternet)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun commonFactualInterrogativesUseOptionalKnowledgeRoute() {
        val spanishCount = UltraGeneralQueryRouter.classify(
            "Ultra, cuántos planetas hay en el sistema solar"
        )
        val spanishName = UltraGeneralQueryRouter.classify(
            "Ultra, cómo se llama la capital de Francia"
        )
        val englishCount = UltraGeneralQueryRouter.classify(
            "Ultra, how many moons does Mars have?"
        )

        listOf(spanishCount, spanishName, englishCount).forEach { request ->
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
            assertFalse(request.requiresInternet)
            assertFalse(request.requiresFreshData)
        }
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
        assertEquals(UltraVerificationMode.OPTIONAL, weatherDefinition.verificationMode)
        assertEquals(UltraVerificationMode.OPTIONAL, priceDefinition.verificationMode)
        assertFalse(weatherDefinition.requiresFreshData)
        assertFalse(priceDefinition.requiresFreshData)
    }

    @Test
    fun explicitlyCurrentFactualQuestionIsMarkedFreshAndRequired() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es la versión actual de Android"
        )

        assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
    }

    @Test
    fun definitionQuestionWithCurrentQualifierIsMarkedFreshAndRequired() {
        val spanish = UltraGeneralQueryRouter.classify(
            "Ultra, qué es la versión actual de Android"
        )
        val english = UltraGeneralQueryRouter.classify(
            "Ultra, what is the current Android version?"
        )

        listOf(spanish, english).forEach { request ->
            assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
            assertTrue(request.requiresInternet)
            assertTrue(request.requiresFreshData)
            assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        }
    }

    @Test
    fun ordinaryConversationStaysOnLocalChatPath() {
        val greeting = UltraGeneralQueryRouter.classify(
            "Ultra, hola, cómo estás"
        )
        val casual = UltraGeneralQueryRouter.classify(
            "Ultra, hoy estoy aburrido"
        )

        assertEquals(UltraVerificationMode.LOCAL, greeting.verificationMode)
        assertEquals(UltraVerificationMode.LOCAL, casual.verificationMode)
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

        listOf(weather, price, release).forEach { request ->
            assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
            assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
            assertTrue(request.requiresInternet)
            assertTrue(request.requiresFreshData)
        }
    }

    @Test
    fun stableTimeAndTemperatureExplanationsStayOptional() {
        val morningSky = UltraGeneralQueryRouter.classify(
            "Ultra, ¿por qué el cielo es rojo en la mañana?"
        )
        val bodyTemperature = UltraGeneralQueryRouter.classify(
            "Ultra, ¿cómo funciona la temperatura corporal?"
        )
        val windowsUpdate = UltraGeneralQueryRouter.classify(
            "Ultra, ¿cómo funciona Windows Update?"
        )

        listOf(morningSky, bodyTemperature, windowsUpdate).forEach { request ->
            assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode)
            assertFalse(request.requiresInternet)
            assertFalse(request.requiresFreshData)
        }
    }

    @Test
    fun explicitTemperatureNowRemainsFresh() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, ¿qué temperatura hace ahora en Rancagua?"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
    }


    @Test
    fun tomorrowWeatherIsFreshButMorningExplanationIsStable() {
        val tomorrow = UltraGeneralQueryRouter.classify(
            "Ultra, ¿cuál es el clima mañana en Santiago?"
        )
        val morning = UltraGeneralQueryRouter.classify(
            "Ultra, ¿por qué el cielo es rojo en la mañana?"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, tomorrow.kind)
        assertEquals(UltraVerificationMode.REQUIRED, tomorrow.verificationMode)
        assertTrue(tomorrow.requiresFreshData)
        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, morning.kind)
        assertEquals(UltraVerificationMode.OPTIONAL, morning.verificationMode)
        assertFalse(morning.requiresFreshData)
    }

    @Test
    fun forecastDefinitionIsStableWhileLocatedForecastIsFresh() {
        val definition = UltraGeneralQueryRouter.classify("Ultra, what is a forecast?")
        val located = UltraGeneralQueryRouter.classify(
            "Ultra, forecast for London tomorrow"
        )

        assertEquals(UltraVerificationMode.OPTIONAL, definition.verificationMode)
        assertFalse(definition.requiresFreshData)
        assertEquals(UltraVerificationMode.REQUIRED, located.verificationMode)
        assertTrue(located.requiresFreshData)
    }

    @Test
    fun legacyExplicitInternetRequirementRemainsRequired() {
        val request = UltraGeneralQueryRequest(
            originalText = "consulta externa explícita",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = true,
            requiresFreshData = false,
            timeoutMillis = 5_000L
        )

        assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
    }
    @Test
    fun conversationalWeatherPromptIsFreshRequiredData() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, háblame del clima en Madrid"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
    }


}
