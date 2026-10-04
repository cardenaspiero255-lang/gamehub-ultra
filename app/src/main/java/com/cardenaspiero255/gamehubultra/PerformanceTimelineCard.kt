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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineActionPolicy

@Composable
fun PerformanceTimelineCard(timeline: PerformanceTimeline, onShare: () -> Unit) {
    val shareEnabled = PerformanceTimelineActionPolicy.canShare(timeline)
    var expanded by remember { mutableStateOf(false) }
    val latest = timeline.samples.lastOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ESTADO DE LA SESIÓN")
                    Text(
                        if (latest == null) {
                            "Sin sesión activa · Los datos aparecerán al jugar"
                        } else {
                            buildList {
                                latest.batteryPercent?.let { add("BAT " + it + "%") }
                                latest.refreshRateHz?.let { add(it.toString() + " Hz") }
                                latest.thermalHeadroomPercent?.let { add("Térmica " + it + "%") }
                                latest.ramUsedPercent?.let { add("RAM " + it + "%") }
                            }.ifEmpty { listOf("Telemetría activa") }.joinToString(" · ")
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
                Text("FPS: " + if (timeline.fpsAvailable) "disponible" else "no disponible mediante las APIs expuestas")

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

                TextButton(
                    onClick = onShare,
                    enabled = shareEnabled,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(PerformanceTimelineActionPolicy.shareLabel(timeline))
                }
            }
        }
    }
}
