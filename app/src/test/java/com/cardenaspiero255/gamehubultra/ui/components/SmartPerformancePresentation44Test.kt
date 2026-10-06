package com.cardenaspiero255.gamehubultra.ui.components

import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.DriverStrategy
import com.cardenaspiero255.gamehubultra.domain.GpuFamily
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand
import com.cardenaspiero255.gamehubultra.domain.RecommendationEvidence
import com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance
import com.cardenaspiero255.gamehubultra.domain.RecommendationOutcome
import com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation
import com.cardenaspiero255.gamehubultra.domain.SmartRecommendationExplanation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartPerformancePresentation44Test {
    private fun recommendation(
        explanation: SmartRecommendationExplanation,
        strategy: DriverStrategy = DriverStrategy.TURNIP_CANDIDATE
    ) = SmartPerformanceRecommendation(
        profile = PerformanceProfile.X4,
        reason = explanation.reason,
        safeFallback = PerformanceProfile.BALANCED,
        evidence = explanation.evidence.map { it.text },
        score = 90,
        driverStrategy = strategy,
        gpuFamily = GpuFamily.MALI,
        explanation = explanation
    )

    @Test
    fun `presentation builder keeps every explanation section and provenance label`() {
        val explanation = SmartRecommendationExplanation(
            reason = "Razón",
            evidence = listOf(
                RecommendationEvidence("medido", RecommendationEvidenceProvenance.MEASURED),
                RecommendationEvidence("inferido", RecommendationEvidenceProvenance.INFERRED),
                RecommendationEvidence("recordado", RecommendationEvidenceProvenance.REMEMBERED),
                RecommendationEvidence("externo", RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED)
            ),
            unavailableData = listOf("Latencia"),
            contradictions = listOf("Fuentes en conflicto"),
            confidence = RecommendationConfidenceBand.HIGH,
            outcomes = listOf(
                RecommendationOutcome(
                    RecommendationOutcomeObjective.RECOMMENDED,
                    PerformanceProfile.X4,
                    "Rendimiento según evidencia."
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BALANCED,
                    PerformanceProfile.BALANCED,
                    "Estabilidad."
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BATTERY,
                    PerformanceProfile.BALANCED,
                    "Menor carga."
                )
            ),
            changeExplanation = "Cambió por nueva evidencia.",
            withheldReasons = listOf("No se prioriza otra opción."),
            conciseSummary = "Resumen"
        )

        val presentation = smartPerformancePresentation(
            recommendation(explanation)
        )

        assertEquals(
            R.string.smart_performance_confidence_high,
            presentation.confidenceLabelRes
        )
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_evidence })
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_unavailable })
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_contradictions })
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_changed })
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_not_recommended })
        assertTrue(presentation.sections.any { it.titleRes == R.string.smart_performance_comparison })
        assertTrue(
            presentation.sections
                .flatMap { it.lines }
                .mapNotNull { it.prefixRes }
                .containsAll(
                    listOf(
                        R.string.smart_performance_evidence_measured,
                        R.string.smart_performance_evidence_inferred,
                        R.string.smart_performance_evidence_remembered,
                        R.string.smart_performance_evidence_external,
                        R.string.smart_performance_outcome_recommended,
                        R.string.smart_performance_outcome_balanced,
                        R.string.smart_performance_outcome_battery
                    )
                )
        )
    }

    @Test
    fun `presentation builder omits empty optional sections and maps all confidence and drivers`() {
        RecommendationConfidenceBand.entries.forEach { band ->
            assertTrue(smartPerformanceConfidenceLabelRes(band) != 0)
        }
        DriverStrategy.entries.forEach { strategy ->
            assertTrue(smartPerformanceDriverLabelRes(strategy) != 0)
        }

        val explanation = SmartRecommendationExplanation(
            reason = "Razón",
            evidence = emptyList(),
            unavailableData = emptyList(),
            contradictions = emptyList(),
            confidence = RecommendationConfidenceBand.LOW,
            outcomes = emptyList(),
            changeExplanation = null,
            withheldReasons = emptyList(),
            conciseSummary = "Resumen"
        )

        val presentation = smartPerformancePresentation(
            recommendation(explanation, DriverStrategy.SYSTEM_ONLY)
        )

        assertEquals(R.string.smart_performance_confidence_low, presentation.confidenceLabelRes)
        assertTrue(presentation.sections.isEmpty())
        assertEquals(R.string.driver_system_only, presentation.driverLabelRes)
    }
}
