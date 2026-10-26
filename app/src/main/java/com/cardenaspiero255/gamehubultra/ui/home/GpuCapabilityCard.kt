package com.cardenaspiero255.gamehubultra.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.gpu.GpuCapabilityReport

/** CAR-53: a verified graphics diagnostics view, never a fake driver switch. */
@Composable
internal fun GpuCapabilityCard(report: GpuCapabilityReport) {
    Card(modifier = Modifier.fillMaxWidth().testTag("car53_gpu_card")) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Capacidades gráficas verificadas", style = MaterialTheme.typography.titleLarge)
            Text("GPU: " + (report.renderer ?: "sin identificar"))
            Text("OpenGL ES: " + (report.openGlEsVersion ?: "no verificado"))
            val vulkanLabel = when (report.vulkan) {
                CapabilityEvidence.VERIFIED -> "verificado"
                CapabilityEvidence.UNAVAILABLE -> "no disponible"
                CapabilityEvidence.UNKNOWN -> "sin verificar"
            }
            Text("Vulkan: " + vulkanLabel)
            Text(
                "Driver alternativo: " +
                    if (report.alternateDriverSelectable) "selección verificada" else "sin control verificado"
            )
            report.explanations.take(3).forEach { note ->
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
