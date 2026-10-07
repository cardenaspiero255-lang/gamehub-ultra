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
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot

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
internal fun AiSessionCoachCard(
    preSession: SessionCoachMessage?,
    liveSamples: List<SessionCoachSnapshot>,
    observations: List<SessionCoachMessage>,
    postSession: SessionCoachPostSessionReport?,
    sessionActive: Boolean
) {
    val lines = SessionCoachPresentation.lines(
        preSession = preSession,
        liveSamples = liveSamples,
        observations = observations,
        postSession = postSession,
        sessionActive = sessionActive,
        activeSessionText = stringResource(
            R.string.session_coach_live,
            liveSamples.size
        ),
        stableText = stringResource(R.string.session_coach_stable)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.session_coach_title),
                style = MaterialTheme.typography.titleLarge
            )
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
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
    val presentation = smartPerformancePresentation(recommendation)
    val confidenceLabel = stringResource(presentation.confidenceLabelRes)

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
            Text(recommendation.explanation.conciseSummary)

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
                presentation.sections.forEach { section ->
                    Text(
                        stringResource(section.titleRes),
                        style = MaterialTheme.typography.labelLarge
                    )
                    section.lines.forEach { line ->
                        val prefixText = line.prefixRes
                            ?.let { stringResource(it) + " · " }
                            .orEmpty()
                        Text(
                            "• $prefixText${line.text}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                stringResource(
                    R.string.smart_performance_driver,
                    recommendation.gpuFamily.name,
                    stringResource(presentation.driverLabelRes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
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
