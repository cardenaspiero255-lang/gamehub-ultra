package com.cardenaspiero255.gamehubultra.ui.components

import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.DriverStrategy
import com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand
import com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance
import com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation

internal data class SmartPerformanceDetailLine(
    val text: String,
    val prefixRes: Int? = null
)

internal data class SmartPerformanceDetailSection(
    val titleRes: Int,
    val lines: List<SmartPerformanceDetailLine>
)

internal data class SmartPerformancePresentation(
    val confidenceLabelRes: Int,
    val driverLabelRes: Int,
    val sections: List<SmartPerformanceDetailSection>
)

internal fun smartPerformanceConfidenceLabelRes(
    confidence: RecommendationConfidenceBand
): Int = when (confidence) {
    RecommendationConfidenceBand.LOW -> R.string.smart_performance_confidence_low
    RecommendationConfidenceBand.MEDIUM -> R.string.smart_performance_confidence_medium
    RecommendationConfidenceBand.HIGH -> R.string.smart_performance_confidence_high
}

internal fun smartPerformanceEvidenceLabelRes(
    provenance: RecommendationEvidenceProvenance
): Int = when (provenance) {
    RecommendationEvidenceProvenance.MEASURED -> R.string.smart_performance_evidence_measured
    RecommendationEvidenceProvenance.INFERRED -> R.string.smart_performance_evidence_inferred
    RecommendationEvidenceProvenance.REMEMBERED -> R.string.smart_performance_evidence_remembered
    RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED -> R.string.smart_performance_evidence_external
}

internal fun smartPerformanceOutcomeLabelRes(
    objective: RecommendationOutcomeObjective
): Int = when (objective) {
    RecommendationOutcomeObjective.RECOMMENDED -> R.string.smart_performance_outcome_recommended
    RecommendationOutcomeObjective.BALANCED -> R.string.smart_performance_outcome_balanced
    RecommendationOutcomeObjective.BATTERY -> R.string.smart_performance_outcome_battery
}

internal fun smartPerformanceDriverLabelRes(
    strategy: DriverStrategy
): Int = when (strategy) {
    DriverStrategy.SYSTEM_ONLY -> R.string.driver_system_only
    DriverStrategy.TURNIP_CANDIDATE -> R.string.driver_turnip_candidate
    DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE -> R.string.driver_native_candidate
}

internal fun smartPerformancePresentation(
    recommendation: SmartPerformanceRecommendation
): SmartPerformancePresentation {
    val explanation = recommendation.explanation
    val sections = buildList {
        if (explanation.evidence.isNotEmpty()) {
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_evidence,
                    lines = explanation.evidence.take(8).map { evidence ->
                        SmartPerformanceDetailLine(
                            text = evidence.text,
                            prefixRes = smartPerformanceEvidenceLabelRes(evidence.provenance)
                        )
                    }
                )
            )
        }
        if (explanation.unavailableData.isNotEmpty()) {
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_unavailable,
                    lines = listOf(
                        SmartPerformanceDetailLine(
                            explanation.unavailableData.joinToString(" · ")
                        )
                    )
                )
            )
        }
        if (explanation.contradictions.isNotEmpty()) {
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_contradictions,
                    lines = explanation.contradictions.take(3).map(::SmartPerformanceDetailLine)
                )
            )
        }
        explanation.changeExplanation?.let { change ->
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_changed,
                    lines = listOf(SmartPerformanceDetailLine(change))
                )
            )
        }
        if (explanation.withheldReasons.isNotEmpty()) {
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_not_recommended,
                    lines = explanation.withheldReasons.take(3).map(::SmartPerformanceDetailLine)
                )
            )
        }
        if (explanation.outcomes.isNotEmpty()) {
            add(
                SmartPerformanceDetailSection(
                    titleRes = R.string.smart_performance_comparison,
                    lines = explanation.outcomes.map { outcome ->
                        SmartPerformanceDetailLine(
                            text = outcome.summary,
                            prefixRes = smartPerformanceOutcomeLabelRes(outcome.objective)
                        )
                    }
                )
            )
        }
    }
    return SmartPerformancePresentation(
        confidenceLabelRes = smartPerformanceConfidenceLabelRes(explanation.confidence),
        driverLabelRes = smartPerformanceDriverLabelRes(recommendation.driverStrategy),
        sections = sections
    )
}
