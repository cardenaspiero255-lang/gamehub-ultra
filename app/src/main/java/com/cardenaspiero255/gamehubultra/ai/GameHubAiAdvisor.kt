package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import java.text.Normalizer
import java.util.Locale

class GameHubAiAdvisor(
    private val modelAdapter: LocalAiModelAdapter? = null,
    private val memoryGateway: UltraLongTermMemoryGateway? = null
) : UltraAssistantGateway {

    override fun hasLocalModelProvider(): Boolean = modelAdapter != null

    override fun isLocalModelAvailable(): Boolean =
        runCatching { modelAdapter?.isAvailable() == true }.getOrDefault(false)

    /**
     * Returns only a substantive local-model answer for stable knowledge.
     * Unlike chat(), this never substitutes the generic capability boilerplate.
     */
    override fun generalKnowledgeChatOrNull(
        message: String,
        context: GameHubAiContext,
        conversation: List<String>
    ): String? {
        val modelAnswer = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.chat(message, context, conversation.takeLast(18))
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { AiChatSafetyFilter.sanitize(it, message) }
            ?.takeUnless(::looksPredominantlyEnglish)

        return modelAnswer ?: deterministicStableKnowledgeOrNull(message)
    }

    override fun advise(
        question: String,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        val modelCandidate = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.advise(question, context)
        }.getOrNull()
            ?.takeIf(AiActionAllowlist::validate)

        if (modelCandidate != null) {
            return adviceFromAllowlistedAction(
                candidate = modelCandidate,
                context = context
            )
        }

        return deterministicAdvice(question, context)
    }

    override fun chat(
        message: String,
        context: GameHubAiContext,
        conversation: List<String>
    ): String {
        val memoryScope = UltraMemoryScope(
            userId = "local",
            gamePackage = context.selectedGamePackage
        )
        val memoryCommandResponse = runCatching {
            memoryGateway?.handleCommand(message, memoryScope)
        }.getOrNull()
        if (memoryCommandResponse != null) return memoryCommandResponse

        val visibleTexts = conversation
            .map { normalize(it.substringAfter(':').trim()) }
            .filter(String::isNotBlank)
            .toMutableSet()
            .apply { add(normalize(message)) }

        val recalled = runCatching {
            memoryGateway
                ?.recallContext(message, memoryScope, limit = 6)
                .orEmpty()
        }.getOrDefault(emptyList())
            .filterNot { recall ->
                recall.record.kind == UltraMemoryKind.CONVERSATION &&
                    normalize(recall.record.text) in visibleTexts
            }
        val recalledConversation = recalled.map { recall ->
            val source = when (recall.provenance) {
                UltraMemoryProvenance.REMEMBERED_FACT -> "hecho recordado"
                UltraMemoryProvenance.PRIOR_CONVERSATION -> "conversación anterior"
                UltraMemoryProvenance.SUMMARY -> "resumen anterior"
            }
            "[Memoria previa · $source] ${recall.record.text}"
        }
        val modelConversation = (
            recalledConversation + conversation.takeLast(12)
        ).takeLast(18)

        val local = runCatching {
            modelAdapter
                ?.takeIf { it.isAvailable() }
                ?.chat(message, context, modelConversation)
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { AiChatSafetyFilter.sanitize(it, message) }
            ?.takeUnless(::looksPredominantlyEnglish)
        if (local != null) return local

        val normalized = normalize(message)
        val memoryRecallQuestion = listOf(
            "que recuerdas",
            "que sabes de mi",
            "recuerdas de mi",
            "what do you remember",
            "what do you know about me"
        ).any(normalized::contains)
        if (memoryRecallQuestion && recalled.isNotEmpty()) {
            val memoryText = recalled
                .take(4)
                .joinToString(" · ") { it.record.text }
            return "Recuerdo: $memoryText"
        }

        deterministicStableKnowledgeOrNull(message)?.let { return it }

        val advice = advise(message, context)
        val profile = profileLabel(advice.suggestedProfile)

        return when {
            normalized.contains("temperatura") || normalized.contains("caliente") ||
                normalized.contains("temperature") || normalized.contains("hot") ->
                "Puedo ayudarte con la temperatura. El estado térmico actual es " +
                    (context.thermalStatus?.toString() ?: "no disponible") +
                    " y el margen térmico es " +
                    (context.thermalHeadroom?.let { (it * 100).toInt().toString() + "%" } ?: "no disponible") +
                    ". Para priorizar estabilidad, " + profile + " es la opción conservadora."

            normalized.contains("bateria") || normalized.contains("battery") ->
                "La batería actual es " +
                    (context.batteryPercent?.let { "$it%" } ?: "no disponible") +
                    (if (context.charging) " y está cargando" else "") +
                    ". Si está baja, recomiendo FPS balanceado para reducir el coste sostenido."

            normalized.contains("fps") || normalized.contains("modo") || normalized.contains("perfil") ||
                normalized.contains("mode") || normalized.contains("profile") ->
                "Con los datos actuales, mi recomendación es " + profile +
                    ". Preparación gaming estimada: " + advice.readiness + "/100."

            normalized.contains("red") || normalized.contains("latencia") || normalized.contains("internet") ||
                normalized.contains("network") || normalized.contains("latency") ->
                "La red validada es " + (if (context.networkValidated) "sí" else "no") +
                    " y la latencia es " +
                    (context.networkLatencyMs?.let { "$it ms" } ?: "no medida") +
                    ". Puedo analizarla, pero no puedo modificar la conexión de otra aplicación."

            normalized.contains("hola") || normalized.contains("quien eres") ||
                normalized.contains("hello") || normalized.contains("who are you") ->
                "Soy Ultra, el asistente de GameHub Ultra. Puedo conversar, analizar el estado disponible del dispositivo y ayudarte con juegos, rendimiento y consultas generales."

            else ->
                "Soy Ultra. Puedo ayudarte en español con rendimiento, FPS, temperatura, batería, red, perfiles de GameHub Ultra y consultas generales. Si una respuesta necesita datos externos, intentaré usar información verificada."
        }
    }

    private fun deterministicStableKnowledgeOrNull(message: String): String? {
        val normalized = normalize(message)
        return when {
            Regex("""\b(?:sentimientos?|emociones?)\b""").containsMatchIn(normalized) ->
                "Los sentimientos son experiencias afectivas conscientes que surgen al interpretar emociones, pensamientos y situaciones. Pueden influir en cómo percibimos, decidimos y actuamos, y suelen durar más que una reacción emocional instantánea."
            else -> null
        }
    }

    private fun profileLabel(profile: PerformanceProfile): String =
        when (profile) {
            PerformanceProfile.BALANCED -> "FPS balanceado"
            PerformanceProfile.FRAME_INTERPOLATION -> "Priorizar interpolación"
            PerformanceProfile.X4 -> "X4"
        }

    private fun looksPredominantlyEnglish(value: String): Boolean {
        val englishWords = setOf(
            "i", "am", "m", "is", "are", "was", "were", "the", "a", "an",
            "to", "of", "for", "you", "your", "can", "could", "will", "would",
            "that", "this", "with", "and", "or", "but", "sure", "explain",
            "how", "what", "why", "low", "level", "graphics", "current",
            "hello", "help"
        )
        val spanishWords = setOf(
            "yo", "soy", "es", "son", "el", "la", "los", "las", "un", "una",
            "de", "del", "para", "que", "tu", "tus", "puedo", "puede", "con",
            "y", "o", "pero", "claro", "explicar", "como", "por", "bajo",
            "nivel", "grafica", "graficos", "actual", "hola", "ayudar"
        )

        val originalNormalized = normalize(value)
        val originalTokens = originalNormalized
            .split(' ')
            .filter(String::isNotBlank)
        val originalSpanishScore = originalTokens.count(spanishWords::contains)

        val valueForScoring = if (originalSpanishScore > 0) {
            value.replace(
                Regex(
                    """\b[A-Z][\p{L}\p{N}'’.-]*(?:\s+(?:(?:of|the|and|to|in|on|for)\s+)?[A-Z][\p{L}\p{N}'’.-]*)+\b"""
                ),
                " "
            )
        } else {
            value
        }

        val normalized = normalize(valueForScoring)
        val padded = " $normalized "
        val reliableEnglishPhrases = listOf(
            " how are you ",
            " who are you ",
            " help you "
        )
        if (reliableEnglishPhrases.any(padded::contains)) return true
        if (originalSpanishScore == 0 && padded.contains(" hello ")) return true

        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val englishScore = tokens.count(englishWords::contains)
        val spanishScore = tokens.count(spanishWords::contains)
        return englishScore >= 2 && englishScore > spanishScore
    }

    override fun intentResolver(): NaturalLanguageIntentResolver =
        object : NaturalLanguageIntentResolver {
            override fun resolve(transcript: String): VoiceCommand? {
                val clean = normalize(transcript)
                if (clean.isBlank()) return null
                return when {
                    containsAny(
                        clean,
                        "que modo me recomiendas",
                        "que perfil me recomiendas",
                        "cual modo me recomiendas",
                        "cual perfil me recomiendas",
                        "recomiendame un modo",
                        "recomiendame un perfil",
                        "optimiza mi juego",
                        "optimiza el juego",
                        "estoy listo para jugar",
                        "is my device ready",
                        "what mode do you recommend",
                        "what profile do you recommend",
                        "optimize my game"
                    ) -> VoiceCommand.AskAi(transcript)

                    else -> null
                }
            }
        }

    override fun close() {
        modelAdapter?.runCatching { close() }
    }

    private fun deterministicAdvice(
        question: String,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        val readiness = readinessScore(context)
        val hot = context.thermalHeadroom?.let { it >= 0.80f } == true ||
            context.thermalStatus != null && context.thermalStatus >= 3
        val lowBattery = context.batteryPercent?.let { it < 20 } == true
        val poorNetwork = !context.networkValidated ||
            context.networkLatencyMs?.let { it > 120L } == true
        val lowStorage = context.storageFreePercent < 10
        val normalized = normalize(question)

        val suggested = when {
            hot || lowBattery || lowStorage ->
                PerformanceProfile.BALANCED
            normalized.contains("interpol") && readiness >= 70 ->
                PerformanceProfile.FRAME_INTERPOLATION
            readiness >= 80 &&
                context.refreshRateHz?.let { it >= 90f } == true &&
                context.gpuAvailable &&
                context.sustainedPerformanceSupported ->
                PerformanceProfile.X4
            else ->
                PerformanceProfile.BALANCED
        }

        val reason = when {
            hot -> AiAdviceReason.THERMAL
            lowBattery -> AiAdviceReason.LOW_BATTERY
            lowStorage -> AiAdviceReason.LOW_STORAGE
            poorNetwork -> AiAdviceReason.NETWORK
            suggested == PerformanceProfile.X4 -> AiAdviceReason.X4_READY
            suggested == PerformanceProfile.FRAME_INTERPOLATION ->
                AiAdviceReason.INTERPOLATION
            else -> AiAdviceReason.BALANCED_GENERAL
        }

        return GameHubAiAdvice(
            readiness = readiness,
            suggestedProfile = suggested,
            reason = reason,
            localModelUsed = false,
            fallbackUsed = true
        )
    }

    private fun adviceFromAllowlistedAction(
        candidate: LocalAiActionCandidate,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        val readiness = readinessScore(context)
        return when (candidate.action) {
            AiActionAllowlist.PROFILE_BALANCED ->
                GameHubAiAdvice(
                    readiness = readiness,
                    suggestedProfile = PerformanceProfile.BALANCED,
                    reason = AiAdviceReason.LOCAL_MODEL_BALANCED,
                    localModelUsed = true,
                    fallbackUsed = false
                )

            AiActionAllowlist.PROFILE_INTERPOLATION ->
                GameHubAiAdvice(
                    readiness = readiness,
                    suggestedProfile = PerformanceProfile.FRAME_INTERPOLATION,
                    reason = AiAdviceReason.LOCAL_MODEL_INTERPOLATION,
                    localModelUsed = true,
                    fallbackUsed = false
                )

            AiActionAllowlist.PROFILE_X4 ->
                if (context.sustainedPerformanceSupported) {
                    GameHubAiAdvice(
                        readiness = readiness,
                        suggestedProfile = PerformanceProfile.X4,
                        reason = AiAdviceReason.LOCAL_MODEL_X4,
                        localModelUsed = true,
                        fallbackUsed = false
                    )
                } else {
                    deterministicAdvice("local model unsupported x4", context)
                }

            AiActionAllowlist.ADVICE ->
                deterministicAdvice("local model advice", context).copy(
                    localModelUsed = true,
                    fallbackUsed = false
                )

            else ->
                deterministicAdvice("invalid local action", context)
        }
    }

    private fun readinessScore(context: GameHubAiContext): Int {
        var score = 50
        if (context.cpuCores >= 8) score += 10 else if (context.cpuCores >= 4) score += 5
        if (context.totalRamMb >= 8192) score += 10 else if (context.totalRamMb >= 4096) score += 5
        if (context.gpuAvailable) score += 8
        if (context.sustainedPerformanceSupported) score += 5
        if (context.thermalStatus == 0) score += 8
        if (context.thermalHeadroom != null && context.thermalHeadroom <= 0.60f) score += 7
        if (context.batteryPercent == null || context.batteryPercent >= 50) score += 5
        if (context.charging) score += 2
        if (context.refreshRateHz != null && context.refreshRateHz >= 90f) score += 5
        if (context.networkValidated) score += 3
        if (context.networkLatencyMs != null && context.networkLatencyMs <= 80L) score += 3
        if (context.storageFreePercent >= 20) score += 2
        return score.coerceIn(0, 100)
    }

    private fun containsAny(value: String, vararg patterns: String): Boolean =
        patterns.any(value::contains)

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
