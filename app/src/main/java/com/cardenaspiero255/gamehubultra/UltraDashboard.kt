package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

private val UltraRed = Color(0xFFFF1630)
private val UltraRedDeep = Color(0xFF4A0008)
private val UltraBlack = Color(0xFF050505)
private val UltraPanel = Color(0xFF0D0D0F)

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(UltraBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.35f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UltraHeader()
                    MetricsRow(device, refresh, latency, battery, storage)
                }
                PerformanceCard(
                    modifier = Modifier.weight(1f),
                    profile = profile,
                    thermal = thermal,
                    adaptiveDecision = adaptiveDecision
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UltraHeader()
                MetricsRow(device, refresh, latency, battery, storage)
                PerformanceCard(
                    modifier = Modifier.fillMaxWidth(),
                    profile = profile,
                    thermal = thermal,
                    adaptiveDecision = adaptiveDecision
                )
            }
        }
    }
}

@Composable
private fun UltraHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("GAMEHUB", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("ULTRA", style = MaterialTheme.typography.headlineSmall, color = UltraRed)
        }
        Surface(color = UltraRedDeep, shape = MaterialTheme.shapes.small) {
            Text(
                "● ULTRA CORE",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MetricsRow(
    device: DeviceInfo,
    refresh: Float?,
    latency: Long?,
    battery: Int?,
    storage: Int?
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(
            listOf(
                "RAM" to "${device.totalRamMb / 1024} GB",
                "CPU" to "${device.cpuCores} núcleos",
                "Hz" to (refresh?.let { "${it.toInt()} Hz" } ?: "—"),
                "RED" to (latency?.let { "${it} ms" } ?: "—"),
                "BAT" to (battery?.let { "${it}%" } ?: "—"),
                "STORAGE" to (storage?.let { "${it}% libre" } ?: "—")
            )
        ) { (label, value) -> MetricChip(label, value) }
    }
}

@Composable
private fun PerformanceCard(
    modifier: Modifier,
    profile: PerformanceProfile,
    thermal: Int?,
    adaptiveDecision: AdaptiveDecision?
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = UltraPanel),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MODO GAMING", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Text(profile.title.uppercase(), color = UltraRed, style = MaterialTheme.typography.labelLarge)
            }
            Surface(color = UltraRedDeep, shape = MaterialTheme.shapes.small) {
                Text(
                    when (profile) {
                        PerformanceProfile.BALANCED -> "FPS BALANCEADO"
                        PerformanceProfile.FRAME_INTERPOLATION -> "MÁS INTERPOLACIÓN"
                        PerformanceProfile.X4 -> "X4 · MÁXIMO RENDIMIENTO"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            thermal?.let {
                Text("MARGEN TÉRMICO  $it%", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { (it / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = UltraRed,
                    trackColor = Color(0xFF2A1114)
                )
            }
            adaptiveDecision?.let {
                Surface(color = Color(0xFF171014), shape = MaterialTheme.shapes.small) {
                    Text(
                        "AUTO: ${it.reason}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE8E8E8)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(color = Color(0xFF111113), shape = MaterialTheme.shapes.small) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = UltraRed)
            Text(value, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}
