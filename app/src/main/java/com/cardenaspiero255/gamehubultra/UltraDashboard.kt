package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.GameHubOriginalUiContract
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.UltraFinalExperienceGate
import com.cardenaspiero255.gamehubultra.domain.UltraFinalExperienceSummary
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

private val UltraRed = Color(0xFFFF1630)
private val UltraRedBright = Color(0xFFFF3048)
private val UltraBlack = Color(0xFF030303)
private val UltraPanel = Color(0xFF0B0B0E)
private val UltraPanel2 = Color(0xFF111116)
private val UltraLine = Color(0xFF2A2A31)
private val UltraMuted = Color(0xFF9696A2)

internal data class BoosterPresentation(
    val title: String,
    val description: String,
    val badge: String
)

internal fun boosterPresentation(profile: PerformanceProfile): BoosterPresentation =
    when (profile) {
        PerformanceProfile.BALANCED -> BoosterPresentation(
            title = "FPS balanceado",
            description = "Equilibra consumo, temperatura y fluidez sin prometer una tasa de FPS.",
            badge = "AUTO"
        )
        PerformanceProfile.FRAME_INTERPOLATION -> BoosterPresentation(
            title = "Priorizar interpolación",
            description = "Solo prioriza opciones compatibles; GameHub Ultra no genera ni fuerza frames en otros juegos.",
            badge = "API"
        )
        PerformanceProfile.X4 -> BoosterPresentation(
            title = "X4",
            description = "Solicita Sustained Performance únicamente cuando Android y el dispositivo lo exponen.",
            badge = "SPM"
        )
    }

internal fun thermalEnvelopeUsagePercent(headroom: Float?): Int? =
    headroom
        ?.takeIf { it.isFinite() && it >= 0f }
        ?.let { (it * 100f).toInt() }

@Composable
fun UltraDashboard(
    device: DeviceInfo,
    diagnostics: RuntimeDiagnostics?,
    telemetryTrend: List<RuntimeDiagnostics> = emptyList(),
    profile: PerformanceProfile,
    adaptiveDecision: AdaptiveDecision?,
    gameName: String = "Ningún juego seleccionado",
    onProfileSelected: (PerformanceProfile) -> Unit = {},
    onPlay: () -> Unit = {}
) {
    val battery = diagnostics?.battery?.percent
    val refresh = diagnostics?.refresh?.currentRefreshRateHz
    val latency = diagnostics?.connectivity?.latencyMs
    val thermalHeadroom = diagnostics?.thermal?.headroom
    val thermalUsagePercent = thermalEnvelopeUsagePercent(thermalHeadroom)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(UltraBlack)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        val compact = maxWidth < 720.dp
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            FeaturedGameCard(
                gameName = gameName,
                profile = profile,
                battery = battery,
                refresh = refresh,
                thermalUsagePercent = thermalUsagePercent,
                totalRamMb = device.totalRamMb,
                onPlay = onPlay
            )
            UltraVoiceCard()
            Text("BOOSTER", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            val presentations = PerformanceProfile.entries.associateWith(::boosterPresentation)
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerformanceProfile.entries.forEach { candidate ->
                        val presentation = presentations.getValue(candidate)
                        BoosterModeCard(
                            presentation.title,
                            presentation.description,
                            presentation.badge,
                            profile == candidate
                        ) { onProfileSelected(candidate) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerformanceProfile.entries.forEach { candidate ->
                        val presentation = presentations.getValue(candidate)
                        BoosterModeCard(
                            presentation.title,
                            presentation.description,
                            presentation.badge,
                            profile == candidate,
                            Modifier.weight(1f)
                        ) { onProfileSelected(candidate) }
                    }
                }
            }
            LiveStatsRow(device, diagnostics, latency)
            TelemetryTrendCard(telemetryTrend)
            ThermalHeadroomCard(thermalHeadroom)
            adaptiveDecision?.let {
                Surface(color = UltraPanel2, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("ADAPTACIÓN", color = UltraRedBright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                Text("PERFIL ACTIVO", color = UltraRedBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(profile.title.uppercase(), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GameHubOriginalShellStrip(compact: Boolean) {
    val navigationItems = GameHubOriginalUiContract.topNavigation
    val actionItems = GameHubOriginalUiContract.primaryActions
    Surface(
        color = Color(0xFF120205),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UltraRed.copy(alpha = .48f), RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "CAR-31 interfaz GameHub original con Inicio, Biblioteca, Perfil, importar juegos y rojo neón estilo Canva"
            }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("CAR-31 · ORIGINAL SHELL", color = UltraRedBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("GameHub original + Canva neon", color = Color.White, fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Black)
                }
                Surface(color = UltraRed.copy(alpha = .18f), shape = RoundedCornerShape(999.dp)) {
                    Text("ROJO / NEGRO", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = UltraRedBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(navigationItems) { title ->
                    val selected = title == "Inicio"
                    Surface(
                        color = if (selected) UltraRed.copy(alpha = .22f) else UltraPanel2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, if (selected) UltraRed else UltraLine, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            title.uppercase(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected) Color.White else UltraMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(actionItems) { action ->
                    Surface(color = UltraPanel, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            action,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = UltraRedBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UltraFinalExperienceCard(summary: UltraFinalExperienceSummary, compact: Boolean) {
    val surfaces = listOf(
        "Dashboard" to "métricas reales",
        "Perfiles" to "selector seguro",
        "Diagnósticos" to "Android expuesto",
        "Biblioteca" to "juegos locales",
        "Historial" to "sesiones",
        "Accesibilidad" to "texto claro",
        "Rendimiento" to "sin recomposición extra",
        "Sin claims falsos" to "solo APIs reales"
    )
    val summaryText = if (summary.readyForRelease) {
        "Dashboard, perfiles, diagnósticos, biblioteca e historial quedan unificados en una experiencia final sin prometer capacidades privilegiadas."
    } else {
        "Pendiente: ${summary.missingSummary}"
    }
    val a11ySummary = if (summary.readyForRelease) {
        "CAR-30 Ultra Final Experience ${summary.readinessRatio} ${summary.statusLabel}"
    } else {
        "CAR-30 Ultra Final Experience ${summary.readinessRatio} ${summary.statusLabel}. Pendiente: ${summary.missingSummary}"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF160307)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UltraRed.copy(alpha = .65f), RoundedCornerShape(16.dp))
            .semantics { contentDescription = a11ySummary }
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CAR-30 · ULTRA FINAL EXPERIENCE", color = UltraRedBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(summary.statusLabel.uppercase(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(summary.readinessRatio, color = UltraRedBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("CAR-30 · ULTRA FINAL EXPERIENCE", color = UltraRedBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text(summary.statusLabel.uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Surface(color = UltraRed.copy(alpha = .18f), shape = RoundedCornerShape(12.dp)) {
                        Text(summary.readinessRatio, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = UltraRedBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                summaryText,
                color = if (summary.readyForRelease) UltraMuted else UltraRedBright,
                fontSize = 11.sp
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(surfaces) { (title, subtitle) ->
                    val ready = title !in summary.missingSurfaces
                    Surface(
                        color = if (ready) UltraPanel2 else Color(0xFF2A070C),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, if (ready) UltraLine else UltraRed, RoundedCornerShape(10.dp))
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(title.uppercase(), color = if (ready) UltraRedBright else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(if (ready) subtitle else "pendiente", color = if (ready) Color.White else UltraRedBright, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedGameCard(
    gameName: String,
    profile: PerformanceProfile,
    battery: Int?,
    refresh: Float?,
    thermalUsagePercent: Int?,
    totalRamMb: Long,
    onPlay: () -> Unit
) {
    Surface(
        color = Color(0xFF100104),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .border(1.dp, UltraRed.copy(alpha = .72f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "JUEGO SELECCIONADO",
                        color = UltraRedBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        gameName.uppercase(),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "PERFIL ${profile.title.uppercase()}",
                        color = UltraMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    onClick = onPlay,
                    color = UltraRed,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, UltraRedBright, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        "JUGAR",
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 15.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MetricBlock("PERFIL", profile.title, Modifier.weight(1f))
                MetricBlock("REFRESCO", refresh?.toInt()?.let { it.toString() + " Hz" } ?: "No disponible", Modifier.weight(1f))
                MetricBlock("USO TÉRMICO", thermalUsagePercent?.let { it.toString() + "%" } ?: "No disponible", Modifier.weight(1f))
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
private fun TelemetryTrendCard(samples: List<RuntimeDiagnostics>) {
    val latest = samples.lastOrNull()
    val previous = samples.dropLast(1).lastOrNull()
    Surface(
        color = UltraPanel,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TENDENCIAS EN TIEMPO REAL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (latest == null) {
                Text("Aún no hay muestras de la sesión.", color = UltraMuted, fontSize = 10.sp)
            } else {
                TrendMetricRow("BATERÍA", latest.battery.percent, previous?.battery?.percent, "%")
                TrendMetricRow("RAM USADA", latest.memory.usedPercent, previous?.memory?.usedPercent, "%")
                TrendMetricRow("USO TÉRMICO",
                    latest.thermal.headroom?.let { (it.coerceIn(0f, 1f) * 100f).toInt() },
                    previous?.thermal?.headroom?.let { (it.coerceIn(0f, 1f) * 100f).toInt() },
                    "%")
                TrendMetricRow("REFRESCO", latest.refresh.currentRefreshRateHz?.toInt(), previous?.refresh?.currentRefreshRateHz?.toInt(), " Hz")
                Text(samples.size.toString() + " muestras · actualización cada 10 s", color = UltraMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun TrendMetricRow(label: String, current: Int?, previous: Int?, suffix: String) {
    val delta = if (current != null && previous != null) current - previous else null
    val arrow = when {
        delta == null -> "·"
        delta > 1 -> "↑"
        delta < -1 -> "↓"
        else -> "→"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = UltraMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(if (current == null) "No disponible" else current.toString() + suffix + " " + arrow, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ThermalHeadroomCard(headroom: Float?) {
    val usage = headroom?.takeIf { it.isFinite() && it >= 0f }
    val progress = usage?.coerceIn(0f, 1f)
    val percent = thermalEnvelopeUsagePercent(usage)
    Surface(color = UltraPanel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("USO DEL SOBRE TÉRMICO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(percent?.let { it.toString() + "%" } ?: "No disponible", color = UltraRedBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (usage != null && progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    when {
                        usage <= 0.30f -> "Uso térmico bajo según la señal expuesta por Android."
                        usage < 0.60f -> "Uso térmico moderado."
                        usage < 0.80f -> "Uso térmico elevado; conviene vigilar la sesión."
                        else -> "Uso térmico cerca o por encima del umbral de throttling severo."
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
                Text("ESTADO BAJO DEMANDA", color = UltraRedBright, fontSize = 9.sp)
            }
            Text(
                "Abre el asistente para ver el estado real del micrófono y ejecutar comandos permitidos.",
                color = UltraMuted,
                fontSize = 11.sp
            )
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
