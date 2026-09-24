package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

enum class GpuFamily { ADRENO, MALI, POWERVR, OTHER, UNKNOWN }
enum class DriverStrategy { SYSTEM_ONLY, TURNIP_CANDIDATE, NATIVE_OR_VENDOR_CANDIDATE }

data class DriverCandidate(
    val id: String,
    val label: String,
    val gpuFamily: GpuFamily,
    val backend: String,
    val available: Boolean,
    val knownGood: Boolean = false,
    val knownBad: Boolean = false,
    val compatibilityScore: Int = 0,
    val benchmarkScore: Int = 0,
    val thermalScore: Int = 0,
    val isTurnip: Boolean = false
)

data class SmartPerformanceInput(
    val device: DeviceInfo,
    val runtime: RuntimeDiagnostics?,
    val gamePackage: String?,
    val gameVersion: String?,
    val emulatorBackend: String?,
    val currentProfile: PerformanceProfile,
    val historicalObservations: List<OptimizationObservation> = emptyList()
)

data class SmartPerformanceRecommendation(
    val profile: PerformanceProfile,
    val reason: String,
    val safeFallback: PerformanceProfile,
    val evidence: List<String>,
    val score: Int,
    val driverStrategy: DriverStrategy,
    val gpuFamily: GpuFamily
)

object SmartPerformanceAdvisor {
    fun recommend(input: SmartPerformanceInput): SmartPerformanceRecommendation {
        val runtime = input.runtime
        val thermalHot = runtime?.thermal?.status?.let { it >= 3 } == true ||
            runtime?.thermal?.headroom?.let { it >= 0.80f } == true
        val lowBattery = runtime?.battery?.percent?.let { it < 20 } == true
        val memoryPressure = runtime?.memory?.usedPercent?.let { it >= 90 } == true
        val storagePressure = runtime?.storage?.freePercent?.let { it < 10 } == true
        val gpuFamily = detectGpuFamily(input.device.gpuVendor, input.device.gpuRenderer)

        val knownBad = input.historicalObservations
            .filter { it.failed || it.highTemperature }
            .groupingBy { it.profile }
            .eachCount()
        val knownGood = input.historicalObservations
            .filter { it.stable && !it.failed && !it.highTemperature }
            .groupingBy { it.profile }
            .eachCount()

        val baseScores = linkedMapOf(
            PerformanceProfile.BALANCED to 60,
            PerformanceProfile.FRAME_INTERPOLATION to 64,
            PerformanceProfile.X4 to 68
        )

        baseScores[PerformanceProfile.X4] = baseScores.getValue(PerformanceProfile.X4) +
            (if (input.device.cpuCores >= 8) 8 else 0) +
            (if (input.device.totalRamMb >= 8192) 6 else 0) +
            (if (input.device.gpuRenderer != null) 8 else 0) +
            (if (runtime?.refresh?.currentRefreshRateHz?.let { it >= 90f } == true) 5 else 0) +
            (if (input.device.cpuCores >= 8 && input.device.totalRamMb >= 8192 &&
                runtime?.battery?.charging == true
            ) 4 else 0)

        if (runtime?.thermal?.headroom?.let { it <= 0.35f } == true) {
            baseScores[PerformanceProfile.BALANCED] =
                baseScores.getValue(PerformanceProfile.BALANCED) + 8
        }
        if (runtime?.connectivity?.latencyMs?.let { it <= 80L } == true) {
            baseScores[PerformanceProfile.FRAME_INTERPOLATION] =
                baseScores.getValue(PerformanceProfile.FRAME_INTERPOLATION) + 2
        }

        knownBad.forEach { (profile, count) ->
            baseScores[profile] = (baseScores.getValue(profile) - 18 * count).coerceAtLeast(0)
        }
        knownGood.forEach { (profile, count) ->
            baseScores[profile] = (baseScores.getValue(profile) + 10 * count).coerceAtMost(100)
        }

        if (thermalHot || lowBattery || memoryPressure || storagePressure) {
            baseScores[PerformanceProfile.BALANCED] = 100
            baseScores[PerformanceProfile.FRAME_INTERPOLATION] =
                baseScores.getValue(PerformanceProfile.FRAME_INTERPOLATION).coerceAtMost(55)
            baseScores[PerformanceProfile.X4] =
                baseScores.getValue(PerformanceProfile.X4).coerceAtMost(45)
        }

        if (input.gamePackage.isNullOrBlank()) {
            return SmartPerformanceRecommendation(
                profile = PerformanceProfile.BALANCED,
                reason = "Sin juego seleccionado, se conserva el perfil seguro.",
                safeFallback = PerformanceProfile.BALANCED,
                evidence = listOf("No hay un paquete de juego activo."),
                score = baseScores.getValue(PerformanceProfile.BALANCED),
                driverStrategy = DriverStrategy.SYSTEM_ONLY,
                gpuFamily = gpuFamily
            )
        }

        val supportedProfiles = PerformanceProfile.entries
            .filterNot { profile -> knownBad.getOrDefault(profile, 0) >= 2 }
            .filter { profile ->
                profile != PerformanceProfile.X4 ||
                    (input.device.cpuCores >= 4 && input.device.totalRamMb >= 4096)
            }

        val best = supportedProfiles.maxWithOrNull(
            compareBy<PerformanceProfile> { baseScores.getValue(it) }
                .thenBy { if (it == input.currentProfile) 1 else 0 }
                .thenBy { it.ordinal * -1 }
        ) ?: PerformanceProfile.BALANCED

        val evidence = buildList {
            add(input.device.cpuCores.toString() + " núcleos CPU")
            add(input.device.totalRamMb.toString() + " MB RAM")
            input.device.gpuVendor?.takeIf(String::isNotBlank)?.let { add("GPU: " + it) }
            input.device.gpuRenderer?.takeIf(String::isNotBlank)?.let { add("Renderer: " + it) }
            runtime?.refresh?.currentRefreshRateHz?.let { add("Refresco actual: " + it.toInt() + " Hz") }
            runtime?.thermal?.status?.let { add("Estado térmico: " + it) }
            runtime?.battery?.percent?.let { add("Batería: " + it + "%") }
            if (knownGood.isNotEmpty()) add("Usa resultados estables guardados localmente")
            if (knownBad.isNotEmpty()) add("Evita configuraciones con fallos/temperatura excesiva")
        }

        val reason = when {
            thermalHot -> "Fallback térmico: se evita aumentar la carga sostenida."
            lowBattery -> "Batería baja: se prioriza estabilidad y consumo."
            memoryPressure -> "Presión de memoria alta: se reduce el riesgo de inestabilidad."
            storagePressure -> "Almacenamiento libre bajo: se evita una configuración agresiva."
            knownGood.containsKey(best) -> "Existe evidencia local de estabilidad para este perfil."
            best == PerformanceProfile.X4 -> "El dispositivo expone suficiente capacidad para probar X4 de forma conservadora."
            best == PerformanceProfile.FRAME_INTERPOLATION -> "El perfil encaja con un refresco alto y sin presión térmica relevante."
            else -> "Se conserva un perfil equilibrado con la evidencia disponible."
        }

        return SmartPerformanceRecommendation(
            profile = best,
            reason = reason,
            safeFallback = if (knownGood.containsKey(PerformanceProfile.BALANCED)) {
                PerformanceProfile.BALANCED
            } else input.currentProfile,
            evidence = evidence,
            score = baseScores.getValue(best),
            driverStrategy = when (gpuFamily) {
                GpuFamily.ADRENO -> DriverStrategy.TURNIP_CANDIDATE
                GpuFamily.MALI, GpuFamily.POWERVR, GpuFamily.OTHER -> DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE
                GpuFamily.UNKNOWN -> DriverStrategy.SYSTEM_ONLY
            },
            gpuFamily = gpuFamily
        )
    }

    fun selectDriver(
        gpuFamily: GpuFamily,
        backend: String,
        candidates: List<DriverCandidate>
    ): DriverCandidate? =
        candidates
            .filter { it.available && it.gpuFamily == gpuFamily }
            .filter { backend.isBlank() || it.backend.equals(backend, ignoreCase = true) }
            .filterNot { gpuFamily != GpuFamily.ADRENO && it.isTurnip }
            .filterNot { it.knownBad }
            .maxWithOrNull(
                compareBy<DriverCandidate> { if (it.knownGood) 1 else 0 }
                    .thenBy { it.compatibilityScore.coerceIn(0, 100) }
                    .thenBy { it.benchmarkScore.coerceIn(0, 100) }
                    .thenBy { it.thermalScore.coerceIn(0, 100) }
                    .thenBy { it.id }
            )

    fun detectGpuFamily(vendor: String?, renderer: String?): GpuFamily {
        val text = listOf(vendor, renderer)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" ")
            .lowercase()
        return when {
            "adreno" in text || "qualcomm" in text -> GpuFamily.ADRENO
            "mali" in text || "arm" in text -> GpuFamily.MALI
            "powervr" in text || "imagination" in text -> GpuFamily.POWERVR
            text.isBlank() -> GpuFamily.UNKNOWN
            else -> GpuFamily.OTHER
        }
    }
}
