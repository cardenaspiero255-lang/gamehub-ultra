package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

enum class AssistantPreset(val title: String) {
    RECOMMENDED("Recomendado"),
    BALANCED("Equilibrado"),
    BATTERY("Batería")
}

enum class ResolutionAdvice(val title: String) {
    KEEP("Mantener resolución"),
    REDUCE_ONE_STEP("Bajar un nivel si el juego lo permite"),
    AUTO("Automática / nativa")
}

data class SmartGameAssistantInput(
    val device: DeviceInfo,
    val runtime: RuntimeDiagnostics?,
    val gamePackage: String?,
    val currentProfile: PerformanceProfile,
    val historicalObservations: List<OptimizationObservation> = emptyList(),
    val existingRecommendation: SmartPerformanceRecommendation
)

data class SmartGameAssistantSuggestion(
    val preset: AssistantPreset,
    val profile: PerformanceProfile,
    val thermalPreference: ThermalPreference,
    val refreshRateTargetHz: Int?,
    val resolutionAdvice: ResolutionAdvice,
    val reason: String,
    val evidence: List<String>
)

object SmartGameAssistant {
    fun suggestAll(input: SmartGameAssistantInput): List<SmartGameAssistantSuggestion> =
        AssistantPreset.entries.map { preset -> suggest(input, preset) }

    fun suggest(
        input: SmartGameAssistantInput,
        preset: AssistantPreset
    ): SmartGameAssistantSuggestion {
        val runtime = input.runtime
        val thermalPressure = runtime?.thermal?.status?.let { it >= 3 } == true ||
            runtime?.thermal?.headroom?.let { it >= 0.80f } == true
        val memoryPressure = runtime?.memory?.usedPercent?.let { it >= 90 } == true
        val lowBattery = runtime?.battery?.percent?.let { it < 20 } == true

        val highestSupportedHz = runtime?.refresh?.supportedRefreshRatesHz?.maxOrNull()
        val conservativeHz = runtime?.refresh?.supportedRefreshRatesHz
            ?.filter { it <= 90 }
            ?.maxOrNull()
            ?: runtime?.refresh?.supportedRefreshRatesHz?.minOrNull()

        val profile = when (preset) {
            AssistantPreset.RECOMMENDED -> input.existingRecommendation.profile
            AssistantPreset.BALANCED -> PerformanceProfile.BALANCED
            AssistantPreset.BATTERY -> PerformanceProfile.BALANCED
        }

        val thermalPreference = when (preset) {
            AssistantPreset.RECOMMENDED ->
                if (profile.acceptsHigherTemperature && !thermalPressure) {
                    ThermalPreference.PERFORMANCE
                } else {
                    ThermalPreference.ADAPTIVE
                }
            AssistantPreset.BALANCED -> ThermalPreference.BALANCED
            AssistantPreset.BATTERY -> ThermalPreference.COOLER
        }

        val refresh = when (preset) {
            AssistantPreset.RECOMMENDED ->
                when {
                    thermalPressure || lowBattery || memoryPressure -> conservativeHz
                    profile == PerformanceProfile.FRAME_INTERPOLATION -> highestSupportedHz
                    else -> highestSupportedHz?.takeIf { it >= 90 } ?: conservativeHz
                }
            AssistantPreset.BALANCED ->
                highestSupportedHz?.takeIf { it in 90..120 } ?: conservativeHz
            AssistantPreset.BATTERY ->
                runtime?.refresh?.supportedRefreshRatesHz?.minOrNull()
        }

        val resolutionAdvice = when {
            thermalPressure || memoryPressure -> ResolutionAdvice.REDUCE_ONE_STEP
            preset == AssistantPreset.BATTERY -> ResolutionAdvice.REDUCE_ONE_STEP
            else -> ResolutionAdvice.AUTO
        }

        val reason = when {
            input.gamePackage.isNullOrBlank() ->
                "Selecciona un juego para personalizar la recomendación."
            thermalPressure ->
                "La telemetría térmica está elevada; se prioriza estabilidad y menor carga sostenida."
            lowBattery && preset == AssistantPreset.RECOMMENDED ->
                "La batería está baja; el perfil recomendado evita aumentar innecesariamente la carga."
            memoryPressure ->
                "Hay presión de memoria; se recomienda reducir la carga gráfica cuando el juego lo permita."
            preset == AssistantPreset.RECOMMENDED ->
                input.existingRecommendation.reason
            preset == AssistantPreset.BATTERY ->
                "Preset de batería: mantiene un perfil conservador, reduce la prioridad térmica y usa el refresco más bajo disponible."
            else ->
                "Preset equilibrado: mantiene una política conservadora y usa un refresco compatible con el dispositivo."
        }

        val evidence = buildList {
            add("Juego: " + (input.gamePackage ?: "ninguno"))
            add(input.device.cpuCores.toString() + " núcleos CPU")
            add(input.device.totalRamMb.toString() + " MB RAM")
            input.device.gpuRenderer?.takeIf(String::isNotBlank)?.let { add("GPU: " + it) }
            runtime?.battery?.percent?.let { add("Batería: " + it + "%") }
            runtime?.thermal?.status?.let { add("Estado térmico: " + it) }
            runtime?.memory?.usedPercent?.let { add("RAM usada: " + it + "%") }
            refresh?.let { add("Refresco sugerido: " + it + " Hz") }
            historicalNote(input)?.let(::add)
        }

        return SmartGameAssistantSuggestion(
            preset = preset,
            profile = profile,
            thermalPreference = thermalPreference,
            refreshRateTargetHz = refresh,
            resolutionAdvice = resolutionAdvice,
            reason = reason,
            evidence = evidence.distinct()
        )
    }

    private fun historicalNote(input: SmartGameAssistantInput): String? {
        val count = input.historicalObservations.size
        return when {
            count > 0 -> "Evidencia local: $count observaciones previas."
            else -> "Sin historial local previo; se usa la telemetría disponible."
        }
    }
}
