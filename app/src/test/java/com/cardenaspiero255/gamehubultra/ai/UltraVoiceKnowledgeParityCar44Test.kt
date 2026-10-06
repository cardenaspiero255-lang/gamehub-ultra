package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraVoiceKnowledgeParityCar44Test {
    private val context = GameHubAiContext(
        selectedGamePackage = null,
        sustainedPerformanceSupported = false,
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = 0.2f,
        batteryPercent = 80,
        charging = false,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 30L,
        downstreamBandwidthKbps = 100_000L,
        storageFreePercent = 50,
        inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false
    )

    @Test
    fun `one shot and continuous voice answer YouTube identically without research`() {
        assertVoiceParity("¿Qué es YouTube?", "Ultra, ¿qué es YouTube?") { answer ->
            assertTrue(answer.contains("video", ignoreCase = true))
            assertTrue(answer.contains("YouTube", ignoreCase = true))
        }
    }

    @Test
    fun `one shot and continuous voice answer pathogen identically without research`() {
        assertVoiceParity("¿Qué es un patógeno?", "Ultra, ¿qué es un patógeno?") { answer ->
            assertTrue(answer.contains("enfermed", ignoreCase = true))
            assertTrue(
                answer.contains("agente", ignoreCase = true) ||
                    answer.contains("microorgan", ignoreCase = true)
            )
        }
    }

    @Test
    fun `one shot and continuous voice answer bear identically without research`() {
        assertVoiceParity("¿Qué es un oso?", "Ultra, ¿qué es un oso?") { answer ->
            assertTrue(answer.contains("mamífer", ignoreCase = true))
        }
    }

    @Test
    fun `common general knowledge never falls into verification error offline`() {
        val cases = listOf(
            "¿Qué es el sol?" to "estrella",
            "¿Qué es la capa de ozono?" to "ultravioleta",
            "¿Qué es un velociraptor?" to "dinosaurio",
            "¿Qué es la Luna?" to "satélite",
            "¿Qué es un dinosaurio?" to "reptil",
            "¿Qué es un terremoto?" to "corteza",
            "¿Qué es un tsunami?" to "ola",
            "¿Qué es el efecto invernadero?" to "atmósfera",
            "¿Qué es el cambio climático?" to "clima",
            "¿Qué es una proteína?" to "aminoácido",
            "¿Qué es una neurona?" to "nervioso",
            "¿Qué es una mitocondria?" to "energía",
            "¿Qué es un fósil?" to "resto",
            "¿Qué es una supernova?" to "estrella",
            "¿Qué es una nube?" to "agua"
        )

        cases.forEach { (question, expectedKeyword) ->
            assertVoiceParity(question, "Ultra, $question") { answer ->
                assertTrue(
                    answer.contains(expectedKeyword, ignoreCase = true),
                    "$question -> $answer"
                )
                assertTrue(
                    !answer.contains("No pude verificar", ignoreCase = true),
                    "$question -> $answer"
                )
            }
        }
    }

    private fun assertVoiceParity(
        oneShotText: String,
        continuousText: String,
        assertions: (String) -> Unit
    ) {
        val advisor = GameHubAiAdvisor()
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            UltraQueryExecutionCoordinator(UltraVerifiedResearchEngine(emptyList()))
        )

        fun answer(text: String): String {
            val route = assertIs<UltraAgentRoute.Chat>(
                UltraUnifiedAgentRouter.route(text)
            )
            return executor.answer(
                route = route,
                stableKnowledgeFallback = {
                    advisor.generalKnowledgeChatOrNull(
                        message = route.message,
                        context = context,
                        conversation = emptyList()
                    )
                }
            ) {
                advisor.chat(route.message, context, emptyList())
            }
        }

        val oneShot = answer(oneShotText)
        val continuous = answer(continuousText)
        assertEquals(oneShot, continuous)
        assertTrue(!oneShot.contains("No pude verificar", ignoreCase = true))
        assertions(oneShot)
    }
}
