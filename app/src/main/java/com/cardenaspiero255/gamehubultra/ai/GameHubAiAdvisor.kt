package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import java.text.Normalizer
import java.util.Locale

class GameHubAiAdvisor(
    private val modelAdapter: LocalAiModelAdapter? = null,
    private val memoryGateway: UltraLongTermMemoryGateway? = null
) : AutoCloseable {

    fun hasLocalModelProvider(): Boolean = modelAdapter != null

    fun isLocalModelAvailable(): Boolean =
        runCatching { modelAdapter?.isAvailable() == true }.getOrDefault(false)

    fun advise(
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

    fun chat(
        message: String,
        context: GameHubAiContext,
        conversation: List<String> = emptyList()
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
        if (local != null) return local

        val normalized = normalize(message)
        val english = isEnglishMessage(message)
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
            return if (english) {
                "I remember: $memoryText"
            } else {
                "Recuerdo: $memoryText"
            }
        }

        val advice = advise(message, context)
        val profile = profileLabel(advice.suggestedProfile, english)

        return when {
            (normalized.contains("temperatura") || normalized.contains("caliente") ||
                normalized.contains("temperature") || normalized.contains("hot")) && english ->
                "I can help with temperature. The current thermal status is " +
                    (context.thermalStatus?.toString() ?: "not available") +
                    " and thermal headroom is " +
                    (context.thermalHeadroom?.let { (it * 100).toInt().toString() + "%" } ?: "not available") +
                    ". For stability, " + profile + " is the conservative option."

            normalized.contains("temperatura") || normalized.contains("caliente") ->
                "Puedo ayudarte con la temperatura. El estado térmico actual es " +
                    (context.thermalStatus?.toString() ?: "no disponible") +
                    " y el margen térmico es " +
                    (context.thermalHeadroom?.let { (it * 100).toInt().toString() + "%" } ?: "no disponible") +
                    ". Para priorizar estabilidad, " + profile + " es la opción conservadora."

            normalized.contains("bateria") || normalized.contains("battery") ->
                if (english) {
                    "The current battery is " +
                        (context.batteryPercent?.let { "$it%" } ?: "not available") +
                        (if (context.charging) " and it is charging" else "") +
                        ". If it is low, Balanced FPS reduces sustained cost."
                } else {
                    "La batería actual es " +
                        (context.batteryPercent?.let { "$it%" } ?: "no disponible") +
                        (if (context.charging) " y está cargando" else "") +
                        ". Si está baja, recomiendo FPS balanceado para reducir el coste sostenido."
                }

            normalized.contains("fps") || normalized.contains("modo") || normalized.contains("perfil") ||
                normalized.contains("mode") || normalized.contains("profile") ->
                if (english) {
                    "With the current data, I recommend " + profile +
                        ". Estimated gaming readiness: " + advice.readiness + "/100."
                } else {
                    "Con los datos actuales, mi recomendación es " + profile +
                        ". Preparación gaming estimada: " + advice.readiness + "/100."
                }

            normalized.contains("red") || normalized.contains("latencia") || normalized.contains("internet") ||
                normalized.contains("network") || normalized.contains("latency") ->
                if (english) {
                    "The network is validated: " + (if (context.networkValidated) "yes" else "no") +
                        ", with latency " +
                        (context.networkLatencyMs?.let { "$it ms" } ?: "not measured") +
                        ". I can analyze it, but I cannot modify another app's connection."
                } else {
                    "La red validada es " + (if (context.networkValidated) "sí" else "no") +
                        " y la latencia es " +
                        (context.networkLatencyMs?.let { "$it ms" } ?: "no medida") +
                        ". Puedo analizarla, pero no puedo modificar la conexión de otra aplicación."
                }

            normalized.contains("hola") || normalized.contains("quien eres") ||
                normalized.contains("hello") || normalized.contains("who are you") ->
                if (english) {
                    "I'm Ultra, the GameHub Ultra assistant. I can discuss gaming, analyze available device state, and help you choose profiles."
                } else {
                    "Soy Ultra, el asistente de GameHub Ultra. Puedo conversar sobre gaming, analizar el estado disponible del dispositivo y ayudarte a elegir perfiles."
                }

            else ->
                if (english) {
                    "I'm Ultra. I can talk with you about performance, FPS, temperature, battery, networking, and GameHub Ultra profiles. Local chat may be limited when a compatible model is unavailable."
                } else {
                    "Soy Ultra. Puedo hablar contigo sobre rendimiento, FPS, temperatura, batería, red y perfiles de GameHub Ultra. En este dispositivo el chat local puede estar limitado si no hay un modelo compatible."
                }
        }
    }

    private fun profileLabel(profile: PerformanceProfile, english: Boolean): String =
        if (english) {
            when (profile) {
                PerformanceProfile.BALANCED -> "Balanced FPS"
                PerformanceProfile.FRAME_INTERPOLATION -> "Prioritize interpolation"
                PerformanceProfile.X4 -> "X4"
            }
        } else {
            when (profile) {
                PerformanceProfile.BALANCED -> "FPS balanceado"
                PerformanceProfile.FRAME_INTERPOLATION -> "Priorizar interpolación"
                PerformanceProfile.X4 -> "X4"
            }
        }

    private fun isEnglishMessage(value: String): Boolean {
        if (Locale.getDefault().language.equals("en", ignoreCase = true)) return true
        val normalized = normalize(value)
        return containsAny(
            normalized,
            "hello",
            "battery",
            "temperature",
            "hot",
            "mode",
            "profile",
            "network",
            "latency",
            "internet",
            "who are you"
        )
    }

    fun intentResolver(): NaturalLanguageIntentResolver =
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
