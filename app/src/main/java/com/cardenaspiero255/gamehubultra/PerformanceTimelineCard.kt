package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineActionPolicy

@Composable
fun PerformanceTimelineCard(timeline: PerformanceTimeline, onShare: () -> Unit) {
    val shareEnabled = PerformanceTimelineActionPolicy.canShare(timeline)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PERFORMANCE TIMELINE")
                TextButton(
                    onClick = onShare,
                    enabled = shareEnabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(PerformanceTimelineActionPolicy.shareLabel(timeline))
                }
            }
            PerformanceTimelineActionPolicy.disabledShareReason(timeline)?.let { reason ->
                Text(reason)
            }
            Text("Telemetría real de la sesión: batería, temperatura, margen térmico, refresco y RAM.")
            Text("FPS: " + if (timeline.fpsAvailable) "disponible" else "no disponible mediante las APIs expuestas")
            if (timeline.samples.isEmpty()) {
                Text("Aún no hay muestras de esta sesión.")
            } else {
                timeline.samples.takeLast(8).asReversed().forEach { sample ->
                    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(java.util.Date(sample.timestampMillis))
                    val metrics = buildList {
                        sample.batteryPercent?.let { add("BAT " + it + "%") }
                        sample.refreshRateHz?.let { add(it.toString() + " Hz") }
                        sample.thermalHeadroomPercent?.let { add("Térmica " + it + "%") }
                        sample.ramUsedPercent?.let { add("RAM " + it + "%") }
                    }
                    Text(time + " · " + if (metrics.isEmpty()) "Sin métricas disponibles" else metrics.joinToString(" · "))
                }
            }
            if (timeline.profileEvents.isNotEmpty()) {
                Text("Cambios de perfil")
                timeline.profileEvents.takeLast(4).asReversed().forEach { event ->
                    Text(
                        (event.profile?.title ?: "Perfil desconocido") + " · " +
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                                .format(java.util.Date(event.timestampMillis))
                    )
                }
            }
            val thermalPressureSamples = timeline.samples.count {
                it.thermalStatus?.let { status -> status >= 3 } == true
            }
            if (thermalPressureSamples > 0 || timeline.thermalEvents.isNotEmpty()) {
                Text(
                    "Cambios térmicos: " + timeline.thermalEvents.size +
                        " · muestras bajo presión térmica: " + thermalPressureSamples
                )
            }
        }
    }
}
