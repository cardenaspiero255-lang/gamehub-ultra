package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraGeneralKnowledgeCorpusRegressionTest {

    @Test
    fun stableGeneralKnowledgeCorpusAlwaysUsesOptionalVerification() {
        stableQuestions.forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)

            assertEquals(
                UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                request.kind,
                question
            )
            assertEquals(
                UltraVerificationMode.OPTIONAL,
                request.verificationMode,
                question
            )
            assertFalse(request.requiresInternet, question)
            assertFalse(request.requiresFreshData, question)
        }
    }

    @Test
    fun volatileCorpusAlwaysRequiresFreshVerifiedResearch() {
        volatileQuestions.forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)

            assertEquals(
                UltraVerificationMode.REQUIRED,
                request.verificationMode,
                question
            )
            assertTrue(request.requiresInternet, question)
            assertTrue(request.requiresFreshData, question)
        }
    }

    @Test
    fun comparisonCorpusAlwaysUsesRequiredResearch() {
        comparisonQuestions.forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)

            assertEquals(
                UltraGeneralQueryKind.COMPARISON_RESEARCH,
                request.kind,
                question
            )
            assertEquals(
                UltraVerificationMode.REQUIRED,
                request.verificationMode,
                question
            )
            assertTrue(request.requiresInternet, question)
        }
    }

    @Test
    fun localConversationCorpusNeverTriggersResearch() {
        localQuestions.forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)

            assertEquals(
                UltraVerificationMode.LOCAL,
                request.verificationMode,
                question
            )
            assertFalse(request.requiresInternet, question)
            assertFalse(request.requiresFreshData, question)
        }
    }

    @Test
    fun stableCorpusCannotBeShadowedByGenericUltraChat() {
        val providerCalls = AtomicInteger(0)
        val genericLocalCalls = AtomicInteger(0)
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(
                object : UltraResearchProvider {
                    override val id = "corpus-encyclopedia"

                    override fun fetch(
                        request: UltraGeneralQueryRequest
                    ): UltraResearchEvidence {
                        providerCalls.incrementAndGet()
                        return UltraResearchEvidence(
                            claimKey = request.originalText.lowercase(),
                            value = "stable-answer",
                            displayText = "Respuesta estable del corpus.",
                            sourceId = "corpus://encyclopedia",
                            authoritative = true
                        )
                    }
                }
            )
        )
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )

        try {
            stableQuestions.forEach { question ->
                val route = UltraAgentRoute.Chat(
                    message = question,
                    query = UltraGeneralQueryRouter.classify(question)
                )

                val answer = executor.answer(
                    route = route,
                    stableKnowledgeFallback = { null },
                    localChat = {
                        genericLocalCalls.incrementAndGet()
                        "Soy Ultra. Puedo ayudarte con consultas generales."
                    }
                )

                assertEquals(
                    "Respuesta estable del corpus.",
                    answer,
                    question
                )
            }
        } finally {
            engine.close()
        }

        assertEquals(stableQuestions.size, providerCalls.get())
        assertEquals(0, genericLocalCalls.get())
    }

    private companion object {
        val stableQuestions = listOf(
            "Ultra, ¿qué es un motor?",
            "Ultra, ¿qué es la fotosíntesis?",
            "Ultra, ¿qué es un agujero negro?",
            "Ultra, ¿qué es el ADN?",
            "Ultra, ¿qué es una célula?",
            "Ultra, ¿qué es introvertido?",
            "Ultra, ¿qué es un exo planeta?",
            "Ultra, ¿qué significa ser introvertido?",
            "Ultra, ¿qué es un volcán?",
            "Ultra, ¿qué es la gravedad?",
            "Ultra, ¿qué es la electricidad?",
            "Ultra, ¿qué es la energía cinética?",
            "Ultra, ¿qué es la energía potencial?",
            "Ultra, ¿qué es una molécula?",
            "Ultra, ¿qué es un átomo?",
            "Ultra, ¿qué es el sistema solar?",
            "Ultra, ¿qué es una galaxia?",
            "Ultra, ¿qué es la Vía Láctea?",
            "Ultra, ¿qué es un eclipse?",
            "Ultra, ¿qué es la evaporación?",
            "Ultra, ¿qué es la condensación?",
            "Ultra, ¿qué es la presión atmosférica?",
            "Ultra, ¿qué es un ecosistema?",
            "Ultra, ¿qué es una cadena alimentaria?",
            "Ultra, ¿qué es la selección natural?",
            "Ultra, ¿qué es una vacuna?",
            "Ultra, ¿qué es una bacteria?",
            "Ultra, ¿qué es un virus?",
            "Ultra, ¿qué es un algoritmo?",
            "Ultra, ¿qué es una API?",
            "Ultra, ¿qué es una base de datos?",
            "Ultra, ¿qué es la memoria RAM?",
            "Ultra, ¿qué es una GPU?",
            "Ultra, ¿qué es Vulkan?",
            "Ultra, ¿qué es un compilador?",
            "Ultra, ¿qué es una variable en programación?",
            "Ultra, ¿qué es una función en programación?",
            "Ultra, ¿qué es una red neuronal?",
            "Ultra, ¿qué es aprendizaje automático?",
            "Ultra, ¿qué es inteligencia artificial?",
            "Ultra, ¿por qué el cielo es azul?",
            "Ultra, ¿por qué flotan los barcos?",
            "Ultra, ¿para qué sirve un motor eléctrico?",
            "Ultra, explícame cómo funciona un panel solar",
            "Ultra, explícame cómo funciona un refrigerador",
            "Ultra, ¿cuántos planetas hay en el sistema solar?",
            "Ultra, ¿cómo se llama la capital de Francia?",
            "Ultra, ¿dónde está el monte Everest?",
            "Ultra, ¿cuándo fue la Revolución Francesa?",
            "Ultra, what is photosynthesis?",
            "Ultra, what is a combustion engine?",
            "Ultra, ¿qué es Instagram?",
            "Ultra, ¿qué es chat gpt?",
            "Ultra, ¿qué es OpenAI?",
            "Ultra, ¿qué es WhatsApp?",
            "Ultra, ¿qué es Discord?",
            "Ultra, ¿qué es Wikipedia?",
            "Ultra, ¿qué es un modelo de lenguaje?",
            "Ultra, ¿qué es la computación en la nube?",
            "Ultra, ¿qué es Internet?"
        )

        val volatileQuestions = listOf(
            "Ultra, ¿cuál es el precio actual del RedMagic 12 Pro?",
            "Ultra, clima de hoy en Santiago",
            "Ultra, ¿qué temperatura hace ahora en Rancagua?",
            "Ultra, ¿cuál es la versión actual de Android?",
            "Ultra, ¿qué noticias hay hoy de Resident Evil?",
            "Ultra, ¿cuándo sale actualmente el próximo Resident Evil?",
            "Ultra, latest Android security patch",
            "Ultra, current Bitcoin price",
            "Ultra, what is the weather today in Santiago?",
            "Ultra, ¿cuánto cuesta ahora una RTX 5090?",
            "Ultra, pronóstico para mañana en Rancagua",
            "Ultra, novedades actuales de NVIDIA"
        )

        val comparisonQuestions = listOf(
            "Ultra, compara Snapdragon 8 Elite con Dimensity 9400",
            "Ultra, Redmi Watch 5 Lite vs Xiaomi Watch 2 Pro",
            "Ultra, ¿cuál es mejor Vulkan o OpenGL?",
            "Ultra, compara una RTX 4090 versus RTX 5090",
            "Ultra, which is better, Android or iOS?",
            "Ultra, compara Wi-Fi 6 con Wi-Fi 7",
            "Ultra, compara SSD NVMe vs SATA",
            "Ultra, ¿cuál tiene mejor rendimiento, CPU o GPU para IA?"
        )

        val localQuestions = listOf(
            "Ultra, hola",
            "Ultra, ¿cómo estás?",
            "Ultra, gracias",
            "Ultra, buenas noches",
            "Ultra, ¿quién eres?",
            "Ultra, ¿cuál es mi juego seleccionado?",
            "Ultra, dime mi perfil activo",
            "Ultra, conversa conmigo"
        )
    }
    @Test
    fun `cost phrasing always requires fresh current data`() {
        listOf(
            "Ultra, what is the price of the RedMagic 12 Pro?",
            "Ultra, what does a RTX 5090 cost?"
        ).forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind, question)
            assertTrue(request.requiresFreshData, question)
            assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode, question)
        }
    }

    @Test
    fun `economic cost concepts remain stable knowledge`() {
        listOf(
            "Ultra, what is opportunity cost?",
            "Ultra, what is marginal cost?",
            "Ultra, what is sunk cost?"
        ).forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind, question)
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode, question)
            assertFalse(request.requiresFreshData, question)
        }
    }

}
