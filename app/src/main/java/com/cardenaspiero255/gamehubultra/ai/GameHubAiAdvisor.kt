package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import java.util.Locale

class GameHubAiAdvisor(
    private val modelAdapter: LocalAiModelAdapter? = null
) {
    fun isLocalModelAvailable(): Boolean =
        modelAdapter?.isAvailable() == true

    fun advise(
        question: String,
        context: GameHubAiContext
    ): GameHubAiAdvice {
        val modelCandidate = modelAdapter
            ?.takeIf { it.isAvailable() }
            ?.advise(question, context)
            ?.takeIf(AiActionAllowlist::validate)

        if (modelCandidate != null) {
            return adviceFromAllowlistedAction(
                candidate = modelCandidate,
                context = context,
                localModelUsed = true
            )
        }

        return deterministicAdvice(question, context)
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
            hot || lowBattery || lowStorage -> PerformanceProfile.BALANCED
            normalized.contains("interpol") && readiness >= 70 -> PerformanceProfile.FRAME_INTERPOLATION
            readiness >= 80 &&
                context.refreshRateHz?.let { it >= 90f } == true &&
                context.gpuAvailable -> PerformanceProfile.X4
            else -> PerformanceProfile.BALANCED
        }

        val reason = buildString {
            append("Preparación ")
            append(readiness)
            append("/100. ")
            when {
                hot -> append("La carga térmica está elevada; conviene priorizar estabilidad.")
                lowBattery -> append("La batería está baja; conviene limitar el coste sostenido.")
                lowStorage -> append("Queda poco almacenamiento libre; evita un perfil agresivo.")
                poorNetwork -> append("La red o la latencia no son óptimas; esto no se arregla forzando una acción externa.")
                suggested == PerformanceProfile.X4 ->
                    append("Hay margen térmico, batería y GPU suficientes para probar X4 dentro de las capacidades expuestas.")
                suggested == PerformanceProfile.FRAME_INTERPOLATION ->
                    append("La prioridad declarada es interpolación; solo se solicita si existe una API compatible.")
                else -> append("El perfil balanceado mantiene una política conservadora para fluidez, consumo y temperatura.")
            }
        }

        val title = when {
            suggested == PerformanceProfile.X4 -> "Recomendación: X4"
            suggested == PerformanceProfile.FRAME_INTERPOLATION -> "Recomendación: interpolación"
            else -> "Recomendación: FPS balanceado"
        }

        return GameHubAiAdvice(
            title = title,
            explanation = reason,
            suggestedProfile = suggested,
            localModelUsed = false,
            fallbackUsed = true
        )
    }

    private fun adviceFromAllowlistedAction(
        candidate: LocalAiActionCandidate,
        context: GameHubAiContext,
        localModelUsed: Boolean
    ): GameHubAiAdvice =
        when (candidate.action) {
            AiActionAllowlist.PROFILE_BALANCED ->
                buildActionAdvice(
                    PerformanceProfile.BALANCED,
                    "El modelo local solicitó el perfil balanceado.",
                    localModelUsed
                )
            AiActionAllowlist.PROFILE_INTERPOLATION ->
                buildActionAdvice(
                    PerformanceProfile.FRAME_INTERPOLATION,
                    "El modelo local solicitó priorizar interpolación. La aplicación no fuerza interpolación en otras apps.",
                    localModelUsed
                )
            AiActionAllowlist.PROFILE_X4 ->
                buildActionAdvice(
                    PerformanceProfile.X4,
                    "El modelo local solicitó X4. El perfil solo utiliza capacidades públicas disponibles.",
                    localModelUsed
                )
            AiActionAllowlist.ADVICE ->
                deterministicAdvice("modelo local", context).copy(
                    localModelUsed = localModelUsed,
                    fallbackUsed = false
                )
            else ->
                deterministicAdvice("fallback", context).copy(
                    localModelUsed = localModelUsed,
                    fallbackUsed = false
                )
        }

    private fun buildActionAdvice(
        profile: PerformanceProfile,
        explanation: String,
        localModelUsed: Boolean
    ) = GameHubAiAdvice(
        title = "Recomendación: " + profile.title,
        explanation = explanation,
        suggestedProfile = profile,
        localModelUsed = localModelUsed,
        fallbackUsed = false
    )

    private fun readinessScore(context: GameHubAiContext): Int {
        var score = 50
        if (context.cpuCores >= 8) score += 10 else if (context.cpuCores >= 4) score += 5
        if (context.totalRamMb >= 8192) score += 10 else if (context.totalRamMb >= 4096) score += 5
        if (context.gpuAvailable) score += 8
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
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9áéíóúüñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
