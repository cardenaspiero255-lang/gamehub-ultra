package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

@Composable
fun UltraDashboard(
    device: DeviceInfo,
    diagnostics: RuntimeDiagnostics?,
    profile: PerformanceProfile,
    adaptiveDecision: AdaptiveDecision?
) {
    val battery = diagnostics?.battery?.percent
    val refresh = diagnostics?.refresh?.currentRefreshRateHz
    val latency = diagnostics?.connectivity?.latencyMs
    val storage = diagnostics?.storage?.freePercent
    val thermal = diagnostics?.thermal?.headroom?.let { (it * 100f).toInt() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "CENTRO ULTRA",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                listOf(
                    "RAM" to "${device.totalRamMb / 1024} GB",
                    "CPU" to "${device.cpuCores} núcleos",
                    "Hz" to (refresh?.let { "${it.toInt()} Hz" } ?: "—"),
                    "Red" to (latency?.let { "${it} ms" } ?: "—"),
                    "Batería" to (battery?.let { "${it}%" } ?: "—"),
                    "Almacenamiento" to (storage?.let { "${it}% libre" } ?: "—")
                )
            ) { (label, value) ->
                MetricChip(label, value)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Perfil activo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        profile.title,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                thermal?.let {
                    Text("Margen térmico: ${it}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { (it / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                adaptiveDecision?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Adaptación: ${it.reason}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
