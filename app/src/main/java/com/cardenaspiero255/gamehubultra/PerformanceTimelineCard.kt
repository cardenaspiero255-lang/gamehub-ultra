package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineActionPolicy
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import kotlin.math.roundToInt

@Composable
fun PerformanceTimelineCard(
    timeline: PerformanceTimeline,
    telemetryTrend: List<RuntimeDiagnostics> = emptyList(),
    sessionActive: Boolean = false,
    onShare: () -> Unit
) {
    val shareEnabled = PerformanceTimelineActionPolicy.canShare(timeline)
    var expanded by remember { mutableStateOf(false) }
    val latest = timeline.samples.lastOrNull()
    val latestRuntime = telemetryTrend.lastOrNull()
    val compactMetrics = buildList {
        latest?.batteryPercent
            ?.let { add("BAT " + it + "%") }
            ?: latestRuntime?.battery?.percent?.let { add("BAT " + it + "%") }
        latest?.refreshRateHz
            ?.let { add(it.toString() + " Hz") }
            ?: latestRuntime?.refresh?.currentRefreshRateHz
                ?.let { add(it.roundToInt().toString() + " Hz") }
        latest?.thermalHeadroomPercent
            ?.let { add("Térmica " + it + "%") }
            ?: latestRuntime?.thermal?.headroom
                ?.let { add("Térmica " + (it * 100).roundToInt() + "%") }
        latest?.ramUsedPercent
            ?.let { add("RAM " + it + "%") }
            ?: latestRuntime?.memory?.usedPercent?.let { add("RAM " + it + "%") }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("ESTADO DE LA SESIÓN")
                    Text(
                        when {
                            latest != null -> compactMetrics
                                .ifEmpty { listOf("Telemetría activa") }
                                .joinToString(" · ")
                            sessionActive -> "Sesión activa · esperando la primera muestra"
                            latestRuntime != null -> compactMetrics
                                .ifEmpty { listOf("Telemetría del dispositivo activa") }
                                .joinToString(" · ")
                            else -> "Sin sesión activa · los datos aparecerán al jugar"
                        }
                    )
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(if (expanded) "Ocultar" else "Ver detalles")
                }
            }

            if (expanded) {
                Text("Batería · temperatura · margen térmico · refresco · RAM")
                Text(
                    "FPS: " +
                        if (timeline.fpsAvailable) {
                            "disponible"
                        } else {
                            "no disponible mediante las APIs expuestas"
                        }
                )

                if (timeline.samples.isNotEmpty()) {
                    Text("Muestras de la sesión")
                    timeline.samples.takeLast(8).asReversed().forEach { sample ->
                        val time = java.text.DateFormat.getTimeInstance(
                            java.text.DateFormat.SHORT
                        ).format(java.util.Date(sample.timestampMillis))
                        val metrics = buildList {
                            sample.batteryPercent?.let { add("BAT " + it + "%") }
                            sample.refreshRateHz?.let { add(it.toString() + " Hz") }
                            sample.thermalHeadroomPercent?.let { add("Térmica " + it + "%") }
                            sample.ramUsedPercent?.let { add("RAM " + it + "%") }
                        }
                        Text(
                            time + " · " +
                                if (metrics.isEmpty()) {
                                    "Sin métricas disponibles"
                                } else {
                                    metrics.joinToString(" · ")
                                }
                        )
                    }
                }

                if (telemetryTrend.isNotEmpty()) {
                    Text("Tendencia reciente del dispositivo")
                    telemetryTrend.takeLast(4).asReversed().forEach { sample ->
                        val metrics = buildList {
                            sample.battery.percent?.let { add("BAT " + it + "%") }
                            sample.refresh.currentRefreshRateHz
                                ?.let { add(it.roundToInt().toString() + " Hz") }
                            sample.thermal.headroom
                                ?.let { add("Térmica " + (it * 100).roundToInt() + "%") }
                            add("RAM " + sample.memory.usedPercent + "%")
                        }
                        Text(metrics.joinToString(" · "))
                    }
                }

                if (timeline.profileEvents.isNotEmpty()) {
                    Text("Cambios de perfil")
                    timeline.profileEvents.takeLast(4).asReversed().forEach { event ->
                        Text(
                            (event.profile?.title ?: "Perfil desconocido") + " · " +
                                java.text.DateFormat.getTimeInstance(
                                    java.text.DateFormat.SHORT
                                ).format(java.util.Date(event.timestampMillis))
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

                TextButton(
                    onClick = onShare,
                    enabled = shareEnabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(PerformanceTimelineActionPolicy.shareLabel(timeline))
                }
                if (!shareEnabled) {
                    PerformanceTimelineActionPolicy
                        .disabledShareReason(timeline)
                        ?.let { Text(it) }
                }
            }
        }
    }
}
