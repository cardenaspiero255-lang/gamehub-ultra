package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraGeneralAssistantCar73Test {
    @Test
    fun arithmeticFastPathIsDeterministicAndOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, cuánto es 47 por 18")
        )

        assertEquals("846", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun linearEquationIsSolvedDeterministically() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, resuelve 3x + 7 = 25")
        )

        assertEquals("x = 6", solution.resultText)
        assertTrue(solution.explanation.contains("3x"))
    }

    @Test
    fun missingTriangleAngleUsesLocalFastPath() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, tengo un triángulo de 90 grados y otro ángulo de 35, cuánto mide el tercero"
            )
        )

        assertEquals("55°", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun mathQuestionRoutesAsUtilityInsteadOfOnlineChat() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, cuánto es 47 por 18",
            optionalResolver = null
        )

        val utility = assertIs<UltraAgentRoute.Utility>(route)
        assertIs<UltraUtilityIntent.StudyMath>(utility.answer.intent)
        assertTrue(utility.answer.message.contains("846"))
        assertTrue(utility.answer.canRunDuringGame)
    }

    @Test
    fun currentWeatherIsMarkedAsFreshOnlineData() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, ¿qué clima habrá hoy?"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
        assertEquals(20_000L, request.timeoutMillis)
    }

    @Test
    fun productComparisonIsMarkedAsResearch() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra"
        )

        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, request.kind)
        assertTrue(request.requiresInternet)
        assertEquals(60_000L, request.timeoutMillis)
    }

    @Test
    fun stableGeneralQuestionDoesNotRequireFreshInternet() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, explícame qué es Vulkan"
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind)
        assertFalse(request.requiresFreshData)
    }

    @Test
    fun conversationalRouteCarriesGeneralQueryMetadata() {
        val route = assertIs<UltraAgentRoute.Chat>(
            UltraUnifiedAgentRouter.route(
                transcript = "Ultra, qué clima habrá hoy",
                optionalResolver = null
            )
        )

        val query = assertNotNull(route.query)
        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, query.kind)
        assertTrue(query.requiresFreshData)
    }

    @Test
    fun conversationalTopicVariantsRouteToGeneralKnowledgeChat() {
        listOf(
            "Ultra, háblame de la marca Nike",
            "Ultra, háblame de los osos",
            "Ultra, cuéntame sobre los tiburones",
            "Ultra, dime qué sabes de Saturno",
            "Ultra, quiero que me hables de Adidas",
            "Ultra, ¿me puedes hablar de los lobos?",
            "Ultra, explícame sobre los volcanes",
            "Ultra, dame información sobre Nintendo"
        ).forEach { transcript ->
            val route = kotlin.test.assertIs<UltraAgentRoute.Chat>(
                UltraUnifiedAgentRouter.route(transcript)
            )
            assertEquals(
                UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                route.query?.kind,
                transcript
            )
        }
    }

}
