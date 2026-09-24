package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

private val UltraRed = Color(0xFFFF1630)
private val UltraRedBright = Color(0xFFFF3048)
private val UltraBlack = Color(0xFF030303)
private val UltraPanel = Color(0xFF0B0B0E)
private val UltraPanel2 = Color(0xFF111116)
private val UltraLine = Color(0xFF2A2A31)
private val UltraMuted = Color(0xFF9696A2)

@Composable
fun UltraDashboard(
    device: DeviceInfo,
    diagnostics: RuntimeDiagnostics?,
    profile: PerformanceProfile,
    adaptiveDecision: AdaptiveDecision?,
    gameName: String = "War Robots",
    onProfileSelected: (PerformanceProfile) -> Unit = {}
) {
    val battery = diagnostics?.battery?.percent
    val refresh = diagnostics?.refresh?.currentRefreshRateHz
    val latency = diagnostics?.connectivity?.latencyMs
    val thermalHeadroom = diagnostics?.thermal?.headroom
    val thermalPercent = thermalHeadroom?.let { (it.coerceIn(0f, 1f) * 100f).toInt() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(UltraBlack)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        val compact = maxWidth < 720.dp
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            UltraCoreHeader(profile)
            FeaturedGameCard(gameName, battery, refresh, thermalPercent, device.totalRamMb)
            UltraVoiceCard()
            Text("BOOSTER", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoosterModeCard("FPS BALANCEADO", "Equilibrio perfecto entre rendimiento y calidad.", "120", profile == PerformanceProfile.BALANCED) { onProfileSelected(PerformanceProfile.BALANCED) }
                    BoosterModeCard("MÁS INTERPOLACIÓN", "Mayor fluidez con interpolación de cuadros avanzada.", "165", profile == PerformanceProfile.FRAME_INTERPOLATION) { onProfileSelected(PerformanceProfile.FRAME_INTERPOLATION) }
                    BoosterModeCard("X4", "Máximo rendimiento. Sin compromisos.", "X4", profile == PerformanceProfile.X4) { onProfileSelected(PerformanceProfile.X4) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoosterModeCard("FPS BALANCEADO", "Equilibrio perfecto entre rendimiento y calidad.", "120", profile == PerformanceProfile.BALANCED, Modifier.weight(1f)) { onProfileSelected(PerformanceProfile.BALANCED) }
                    BoosterModeCard("MÁS INTERPOLACIÓN", "Mayor fluidez con interpolación de cuadros avanzada.", "165", profile == PerformanceProfile.FRAME_INTERPOLATION, Modifier.weight(1f)) { onProfileSelected(PerformanceProfile.FRAME_INTERPOLATION) }
                    BoosterModeCard("X4", "Máximo rendimiento. Sin compromisos.", "X4", profile == PerformanceProfile.X4, Modifier.weight(1f)) { onProfileSelected(PerformanceProfile.X4) }
                }
            }
            LiveStatsRow(device, diagnostics, latency)
            ThermalHeadroomCard(thermalHeadroom)
            adaptiveDecision?.let {
                Surface(color = UltraPanel2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AUTO BOOST", color = UltraRedBright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(it.reason, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UltraCoreHeader(profile: PerformanceProfile) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("GAMEHUB", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("ULTRA", color = UltraRed, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        Surface(color = UltraRed.copy(alpha = .16f), shape = RoundedCornerShape(9.dp), modifier = Modifier.border(1.dp, UltraRed.copy(alpha = .55f), RoundedCornerShape(9.dp))) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
                Text("ULTRA CORE", color = UltraRedBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(profile.title.uppercase(), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FeaturedGameCard(gameName: String, battery: Int?, refresh: Float?, thermalPercent: Int?, totalRamMb: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = UltraPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JUEGO DESTACADO", color = UltraMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(gameName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Panel en tiempo real", color = UltraRedBright, fontSize = 11.sp)
                }
                Surface(color = UltraRed, shape = RoundedCornerShape(10.dp)) {
                    Text("JUGAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MetricBlock("HZ", refresh?.toInt()?.let { it.toString() + " Hz" } ?: "No disponible", Modifier.weight(1f))
                MetricBlock("TÉRMICO", thermalPercent?.let { it.toString() + "%" } ?: "No disponible", Modifier.weight(1f))
                MetricBlock("RAM", if (totalRamMb > 0L) (totalRamMb / 1024L).toString() + " GB" else "No disponible", Modifier.weight(1f))
                MetricBlock("BAT", battery?.let { it.toString() + "%" } ?: "No disponible", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier) {
    Surface(color = UltraPanel2, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(9.dp)) {
            Text(label, color = UltraRedBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ThermalHeadroomCard(headroom: Float?) {
    val normalized = headroom?.coerceIn(0f, 1f)
    val percent = normalized?.let { (it * 100f).toInt() }
    Surface(color = UltraPanel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MARGEN TÉRMICO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(percent?.let { it.toString() + "%" } ?: "No disponible", color = UltraRedBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (normalized != null) {
                LinearProgressIndicator(progress = { normalized }, modifier = Modifier.fillMaxWidth())
                Text(
                    when {
                        normalized >= 0.7f -> "Buen margen térmico para la sesión."
                        normalized >= 0.4f -> "Margen térmico intermedio; vigilar temperatura."
                        else -> "Margen térmico reducido; el sistema puede limitar rendimiento."
                    },
                    color = UltraMuted,
                    fontSize = 10.sp
                )
            } else {
                Text("Android no expone este dato en este dispositivo.", color = UltraMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun UltraVoiceCard() {
    Card(colors = CardDefaults.cardColors(containerColor = UltraPanel), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ULTRA VOICE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("● ESCUCHANDO...", color = UltraRedBright, fontSize = 9.sp)
            }
            Text("“Ultra, abre War Robots y activa X4”", color = UltraMuted, fontSize = 11.sp)
            Text("Entendido, abriendo War Robots y activando modo X4.", color = Color.White, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BoosterModeCard(title: String, description: String, value: String, selected: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF22070B) else UltraPanel), shape = RoundedCornerShape(13.dp), modifier = modifier.border(1.dp, if (selected) UltraRed else UltraLine, RoundedCornerShape(13.dp))) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (selected) UltraRedBright else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(description, color = UltraMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(value, color = UltraRedBright, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LiveStatsRow(device: DeviceInfo, diagnostics: RuntimeDiagnostics?, latency: Long?) {
    val gpu = device.gpuRenderer?.take(18)?.takeIf { it.isNotBlank() } ?: "No disponible"
    val cpu = device.cpuCores.coerceAtLeast(1)
    val hz = diagnostics?.refresh?.currentRefreshRateHz?.toInt()?.let { it.toString() + " Hz" } ?: "No disponible"
    val ram = if (device.totalRamMb > 0L) (device.totalRamMb / 1024L).toString() + " GB" else "No disponible"
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(listOf("CPU" to (cpu.toString() + " núcleos"), "GPU" to gpu, "RAM" to ram, "HZ" to hz, "RED" to (latency?.let { it.toString() + " ms" } ?: "No disponible"))) { (label, value) ->
            Surface(color = UltraPanel2, shape = RoundedCornerShape(9.dp)) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label, color = UltraRedBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(value, color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

