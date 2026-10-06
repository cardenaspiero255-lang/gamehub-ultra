package com.cardenaspiero255.gamehubultra.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceState

internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
internal fun SessionCenterCard(
    context: Context,
    sessions: List<GameSessionRecord>,
    onClear: () -> Unit,
    onShare: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.session_center_title), style = MaterialTheme.typography.titleLarge)
                Text(sessions.size.toString(), color = MaterialTheme.colorScheme.primary)
            }
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.session_center_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                sessions.take(5).forEach { session ->
                    val duration = session.durationMillis?.let(::formatDuration)
                        ?: stringResource(R.string.session_active)
                    DeviceRow(
                        label = session.packageName,
                        value = session.profileName + " · " + duration
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_share))
                    }
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_clear))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActiveProfileCard(state: PerformanceState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.active_profile),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                localizedProfileTitle(state.selectedProfile),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(localizedProfileDescription(state.selectedProfile))
            Text(
                if (state.sustainedModeApplied) {
                    stringResource(R.string.sustained_applied)
                } else {
                    stringResource(R.string.sustained_not_applied)
                }
            )
            Text(
                stringResource(R.string.interpolation_intent) + ": " +
                    if (state.selectedProfile.frameInterpolationIntent) {
                        stringResource(R.string.interpolation_prioritized)
                    } else {
                        stringResource(R.string.interpolation_not_requested)
                    }
            )
            Text(
                stringResource(R.string.thermal_tradeoff) + ": " +
                    if (state.selectedProfile.acceptsHigherTemperature) {
                        stringResource(R.string.accepts_higher_temperature)
                    } else {
                        stringResource(R.string.no_extra_thermal)
                    }
            )
        }
    }
}

@Composable
internal fun SmartPerformanceCard(
    recommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation,
    observations: List<OptimizationObservation>,
    canRevert: Boolean,
    onApply: () -> Unit,
    onReject: () -> Unit,
    onRevert: () -> Unit,
    onClearMemory: () -> Unit
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val explanation = recommendation.explanation
    val confidenceLabel = stringResource(
        smartPerformanceConfidenceLabelRes(explanation.confidence)
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.smart_performance_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(localizedProfileTitle(recommendation.profile))
            Text(
                stringResource(R.string.smart_performance_confidence, confidenceLabel),
                style = MaterialTheme.typography.labelLarge
            )
            Text(explanation.conciseSummary)

            TextButton(onClick = { showDetails = !showDetails }) {
                Text(
                    stringResource(
                        if (showDetails) {
                            R.string.smart_performance_hide_details
                        } else {
                            R.string.smart_performance_why
                        }
                    )
                )
            }

            if (showDetails) {
                SmartPerformanceExplanationDetails(recommendation)
            }

            Text(
                stringResource(R.string.smart_performance_observation_count, observations.size),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_apply))
                }
                TextButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_reject))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onRevert,
                    enabled = canRevert,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.smart_performance_revert))
                }
                TextButton(onClick = onClearMemory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_reset))
                }
            }
        }
    }
}

internal fun smartPerformanceConfidenceLabelRes(
    confidence: com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand
): Int = when (confidence) {
    com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand.LOW ->
        R.string.smart_performance_confidence_low
    com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand.MEDIUM ->
        R.string.smart_performance_confidence_medium
    com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand.HIGH ->
        R.string.smart_performance_confidence_high
}

internal fun smartPerformanceEvidenceLabelRes(
    provenance: com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance
): Int = when (provenance) {
    com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance.MEASURED ->
        R.string.smart_performance_evidence_measured
    com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance.INFERRED ->
        R.string.smart_performance_evidence_inferred
    com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance.REMEMBERED ->
        R.string.smart_performance_evidence_remembered
    com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED ->
        R.string.smart_performance_evidence_external
}

internal fun smartPerformanceOutcomeLabelRes(
    objective: com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective
): Int = when (objective) {
    com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective.RECOMMENDED ->
        R.string.smart_performance_outcome_recommended
    com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective.BALANCED ->
        R.string.smart_performance_outcome_balanced
    com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective.BATTERY ->
        R.string.smart_performance_outcome_battery
}

internal fun smartPerformanceDriverLabelRes(
    strategy: com.cardenaspiero255.gamehubultra.domain.DriverStrategy
): Int = when (strategy) {
    com.cardenaspiero255.gamehubultra.domain.DriverStrategy.SYSTEM_ONLY ->
        R.string.driver_system_only
    com.cardenaspiero255.gamehubultra.domain.DriverStrategy.TURNIP_CANDIDATE ->
        R.string.driver_turnip_candidate
    com.cardenaspiero255.gamehubultra.domain.DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE ->
        R.string.driver_native_candidate
}

@Composable
internal fun SmartPerformanceExplanationDetails(
    recommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation
) {
    val explanation = recommendation.explanation
    if (explanation.evidence.isNotEmpty()) {
        Text(
            stringResource(R.string.smart_performance_evidence),
            style = MaterialTheme.typography.labelLarge
        )
        explanation.evidence.take(8).forEach { evidence ->
            val provenance = stringResource(
                smartPerformanceEvidenceLabelRes(evidence.provenance)
            )
            Text("• [$provenance] ${evidence.text}", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (explanation.unavailableData.isNotEmpty()) {
        Text(
            stringResource(R.string.smart_performance_unavailable),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            explanation.unavailableData.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (explanation.contradictions.isNotEmpty()) {
        Text(
            stringResource(R.string.smart_performance_contradictions),
            style = MaterialTheme.typography.labelLarge
        )
        explanation.contradictions.take(3).forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall)
        }
    }

    explanation.changeExplanation?.let {
        Text(
            stringResource(R.string.smart_performance_changed),
            style = MaterialTheme.typography.labelLarge
        )
        Text(it, style = MaterialTheme.typography.bodySmall)
    }

    if (explanation.withheldReasons.isNotEmpty()) {
        Text(
            stringResource(R.string.smart_performance_not_recommended),
            style = MaterialTheme.typography.labelLarge
        )
        explanation.withheldReasons.take(3).forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (explanation.outcomes.isNotEmpty()) {
        Text(
            stringResource(R.string.smart_performance_comparison),
            style = MaterialTheme.typography.labelLarge
        )
        explanation.outcomes.forEach { outcome ->
            val label = stringResource(
                smartPerformanceOutcomeLabelRes(outcome.objective)
            )
            Text("• $label: ${outcome.summary}", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text(
        stringResource(
            R.string.smart_performance_driver,
            recommendation.gpuFamily.name,
            stringResource(smartPerformanceDriverLabelRes(recommendation.driverStrategy))
        ),
        style = MaterialTheme.typography.bodySmall
    )
}

