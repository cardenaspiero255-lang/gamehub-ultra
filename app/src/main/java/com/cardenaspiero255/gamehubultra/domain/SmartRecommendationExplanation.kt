package com.cardenaspiero255.gamehubultra.domain

enum class RecommendationEvidenceProvenance {
    MEASURED,
    INFERRED,
    REMEMBERED,
    EXTERNALLY_RESEARCHED
}

enum class RecommendationConfidenceBand {
    LOW,
    MEDIUM,
    HIGH
}

enum class RecommendationOutcomeObjective {
    RECOMMENDED,
    BALANCED,
    BATTERY
}

data class RecommendationEvidence(
    val text: String,
    val provenance: RecommendationEvidenceProvenance,
    val sourceLabel: String? = null,
    val fresh: Boolean = true,
    val authoritative: Boolean = true,
    val supportsRecommendation: Boolean? = null
)

data class RecommendationExternalEvidence(
    val text: String,
    val sourceLabel: String,
    val fresh: Boolean,
    val authoritative: Boolean,
    val supportsRecommendation: Boolean? = null
)

data class RecommendationOutcome(
    val objective: RecommendationOutcomeObjective,
    val profile: PerformanceProfile,
    val summary: String,
    val guaranteed: Boolean = false
)

data class SmartRecommendationExplanation(
    val reason: String,
    val evidence: List<RecommendationEvidence>,
    val unavailableData: List<String>,
    val contradictions: List<String>,
    val confidence: RecommendationConfidenceBand,
    val outcomes: List<RecommendationOutcome>,
    val changeExplanation: String?,
    val withheldReasons: List<String>,
    val conciseSummary: String
) {
    companion object {
        fun legacy(
            reason: String,
            evidence: List<String>
        ): SmartRecommendationExplanation =
            SmartRecommendationExplanation(
                reason = reason,
                evidence = evidence.map {
                    RecommendationEvidence(
                        text = it,
                        provenance = RecommendationEvidenceProvenance.INFERRED
                    )
                },
                unavailableData = emptyList(),
                contradictions = emptyList(),
                confidence = RecommendationConfidenceBand.MEDIUM,
                outcomes = emptyList(),
                changeExplanation = null,
                withheldReasons = emptyList(),
                conciseSummary = reason.take(180)
            )
    }
}

internal object SmartRecommendationExplanationFactory {
    fun build(
        input: SmartPerformanceInput,
        recommendedProfile: PerformanceProfile,
        reason: String,
        measuredGood: Map<PerformanceProfile, Int>,
        acceptedFeedback: Map<PerformanceProfile, Int>,
        knownBad: Map<PerformanceProfile, Int>,
        thermalHot: Boolean,
        lowBattery: Boolean,
        memoryPressure: Boolean,
        storagePressure: Boolean
    ): SmartRecommendationExplanation {
        val runtime = input.runtime
        val evidence = buildList {
            add(
                RecommendationEvidence(
                    text = input.device.cpuCores.toString() + " núcleos CPU",
                    provenance = RecommendationEvidenceProvenance.MEASURED,
                    sourceLabel = "DeviceInfo"
                )
            )
            add(
                RecommendationEvidence(
                    text = input.device.totalRamMb.toString() + " MB RAM",
                    provenance = RecommendationEvidenceProvenance.MEASURED,
                    sourceLabel = "DeviceInfo"
                )
            )
            input.device.gpuVendor?.takeIf(String::isNotBlank)?.let {
                add(
                    RecommendationEvidence(
                        text = "GPU: $it",
                        provenance = RecommendationEvidenceProvenance.MEASURED,
                        sourceLabel = "DeviceInfo"
                    )
                )
            }
            input.device.gpuRenderer?.takeIf(String::isNotBlank)?.let {
                add(
                    RecommendationEvidence(
                        text = "Renderer: $it",
                        provenance = RecommendationEvidenceProvenance.MEASURED,
                        sourceLabel = "DeviceInfo"
                    )
                )
            }
            runtime?.refresh?.currentRefreshRateHz?.let {
                add(
                    RecommendationEvidence(
                        text = "Refresco actual: ${it.toInt()} Hz",
                        provenance = RecommendationEvidenceProvenance.MEASURED,
                        sourceLabel = "RuntimeDiagnostics"
                    )
                )
            }
            runtime?.thermal?.status?.let {
                add(
                    RecommendationEvidence(
                        text = "Estado térmico: $it",
                        provenance = RecommendationEvidenceProvenance.MEASURED,
                        sourceLabel = "RuntimeDiagnostics"
                    )
                )
            }
            runtime?.battery?.percent?.let {
                add(
                    RecommendationEvidence(
                        text = "Batería: $it%",
                        provenance = RecommendationEvidenceProvenance.MEASURED,
                        sourceLabel = "RuntimeDiagnostics"
                    )
                )
            }
            add(
                RecommendationEvidence(
                    text = "La recomendación se infiere combinando capacidad, seguridad y contexto actual.",
                    provenance = RecommendationEvidenceProvenance.INFERRED,
                    sourceLabel = "SmartPerformanceAdvisor"
                )
            )
            if (measuredGood.isNotEmpty()) {
                add(
                    RecommendationEvidence(
                        text = "Usa resultados estables guardados localmente.",
                        provenance = RecommendationEvidenceProvenance.REMEMBERED,
                        sourceLabel = "OptimizationMemory"
                    )
                )
            }
            if (acceptedFeedback.isNotEmpty()) {
                add(
                    RecommendationEvidence(
                        text = "Considera recomendaciones aceptadas previamente.",
                        provenance = RecommendationEvidenceProvenance.REMEMBERED,
                        sourceLabel = "OptimizationMemory"
                    )
                )
            }
            if (knownBad.isNotEmpty()) {
                add(
                    RecommendationEvidence(
                        text = "Considera fallos, rechazos o reversiones anteriores.",
                        provenance = RecommendationEvidenceProvenance.REMEMBERED,
                        sourceLabel = "OptimizationMemory"
                    )
                )
            }
            input.externalEvidence.forEach { external ->
                add(
                    RecommendationEvidence(
                        text = external.text,
                        provenance = RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED,
                        sourceLabel = external.sourceLabel,
                        fresh = external.fresh,
                        authoritative = external.authoritative,
                        supportsRecommendation = external.supportsRecommendation
                    )
                )
            }
        }

        val unavailable = buildList {
            if (runtime == null) {
                add("Telemetría en tiempo real")
                add("Estado térmico")
                add("Batería")
                add("Frecuencia de refresco")
                add("Latencia de red")
            } else {
                if (runtime.thermal.status == null && runtime.thermal.headroom == null) {
                    add("Estado térmico")
                }
                if (runtime.battery.percent == null) add("Batería")
                if (runtime.refresh.currentRefreshRateHz == null) add("Frecuencia de refresco")
                if (runtime.connectivity.latencyMs == null) add("Latencia de red")
            }
            if (input.device.gpuVendor.isNullOrBlank() && input.device.gpuRenderer.isNullOrBlank()) {
                add("Identidad de GPU")
            }
            if (input.gameVersion.isNullOrBlank()) add("Versión del juego")
        }

        val contradictions = buildList {
            PerformanceProfile.entries.forEach { profile ->
                if (measuredGood.getOrDefault(profile, 0) > 0 && knownBad.getOrDefault(profile, 0) > 0) {
                    add(
                        "El historial de ${profile.name} contiene resultados estables y señales negativas."
                    )
                }
            }
            val externalOpinions = input.externalEvidence.mapNotNull { it.supportsRecommendation }.toSet()
            if (externalOpinions.size > 1) {
                add("Las fuentes externas disponibles no coinciden sobre la recomendación.")
            }
            if ((thermalHot || lowBattery || memoryPressure || storagePressure) && measuredGood.keys.any { it != PerformanceProfile.BALANCED }) {
                add(
                    "El historial favorable de perfiles agresivos no coincide con las restricciones actuales de seguridad."
                )
            }
        }

        val weakExternalEvidence = input.externalEvidence.any {
            !it.fresh || !it.authoritative
        }
        val confidence = when {
            runtime == null -> RecommendationConfidenceBand.LOW
            contradictions.isNotEmpty() -> RecommendationConfidenceBand.LOW
            unavailable.size >= 3 -> RecommendationConfidenceBand.LOW
            weakExternalEvidence || unavailable.isNotEmpty() -> RecommendationConfidenceBand.MEDIUM
            input.historicalObservations.isNotEmpty() ||
                input.externalEvidence.any { it.fresh && it.authoritative } ->
                RecommendationConfidenceBand.HIGH
            else -> RecommendationConfidenceBand.MEDIUM
        }

        val safetyConstrained = thermalHot || lowBattery || memoryPressure || storagePressure
        val withheldReasons = buildList {
            if (recommendedProfile != PerformanceProfile.X4) {
                when {
                    thermalHot -> add("X4 no se recomienda por presión térmica actual.")
                    lowBattery -> add("X4 no se recomienda con batería baja.")
                    memoryPressure -> add("X4 no se recomienda con presión de memoria alta.")
                    storagePressure -> add("X4 no se recomienda con almacenamiento libre crítico.")
                    knownBad.getOrDefault(PerformanceProfile.X4, 0) >= 2 ->
                        add("X4 no se recomienda por resultados negativos repetidos.")
                    input.device.cpuCores < 4 || input.device.totalRamMb < 4096 ->
                        add("X4 no se recomienda porque la capacidad mínima no está confirmada.")
                }
            }
            if (recommendedProfile != PerformanceProfile.FRAME_INTERPOLATION && runtime?.refresh?.currentRefreshRateHz?.let { it < 90f } == true) {
                add("La interpolación no se prioriza con el refresco actual.")
            }
        }

        val changeExplanation = when {
            recommendedProfile == input.currentProfile -> null
            safetyConstrained ->
                "La recomendación cambia por una restricción de seguridad observada ahora."
            knownBad.getOrDefault(input.currentProfile, 0) > 0 ->
                "La recomendación cambia porque el perfil actual tiene señales negativas en el historial."
            else ->
                "La recomendación cambia al combinar la evidencia actual con el historial disponible."
        }

        val outcomes = listOf(
            RecommendationOutcome(
                objective = RecommendationOutcomeObjective.RECOMMENDED,
                profile = recommendedProfile,
                summary = "Prioriza la opción elegida por la evidencia actual; el resultado real debe medirse."
            ),
            RecommendationOutcome(
                objective = RecommendationOutcomeObjective.BALANCED,
                profile = PerformanceProfile.BALANCED,
                summary = "Prioriza estabilidad y menor agresividad, con posible coste de rendimiento máximo."
            ),
            RecommendationOutcome(
                objective = RecommendationOutcomeObjective.BATTERY,
                profile = PerformanceProfile.BALANCED,
                summary = "Orienta a menor carga sostenida; el ahorro real depende del juego y del dispositivo."
            )
        )

        val concise = reason.let {
            if (it.length <= 180) it else it.take(177) + "..."
        }

        return SmartRecommendationExplanation(
            reason = reason,
            evidence = evidence,
            unavailableData = unavailable,
            contradictions = contradictions,
            confidence = confidence,
            outcomes = outcomes,
            changeExplanation = changeExplanation,
            withheldReasons = withheldReasons,
            conciseSummary = concise
        )
    }
}
