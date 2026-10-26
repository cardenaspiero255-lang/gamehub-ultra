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
import com.cardenaspiero255.gamehubultra.performance.pressure.PressureLevel
import com.cardenaspiero255.gamehubultra.performance.pressure.PressureTrend
import com.cardenaspiero255.gamehubultra.performance.pressure.ResourcePressureReport

/** Read-only CAR-52 dashboard, no process control or unverified RAM cleaning. */
@Composable
internal fun ResourcePressureCard(report: ResourcePressureReport?) {
    Card(modifier = Modifier.fillMaxWidth().testTag("car52_pressure_card")) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Memoria y almacenamiento", style = MaterialTheme.typography.titleLarge)
            if (report == null) {
                Text("Esperando telemetría real de Android")
            } else {
                Text("RAM disponible: " + (report.availableRamPercent?.toString()?.plus("%") ?: "no medida") +
                    " · presión " + label(report.ramPressure))
                Text("Almacenamiento libre: " + (report.freeStoragePercent?.toString()?.plus("%") ?: "no medido") +
                    " · presión " + label(report.storagePressure))
                val trend = when (report.ramTrend) {
                    PressureTrend.INSUFFICIENT_HISTORY -> "historial insuficiente"
                    PressureTrend.STABLE -> "estable"
                    PressureTrend.INCREASING -> "en aumento"
                    PressureTrend.RECOVERING -> "disminuyendo"
                }
                Text("Tendencia de presión RAM: " + trend)
                report.notes.take(3).forEach { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun label(level: PressureLevel) = when (level) {
    PressureLevel.UNKNOWN -> "desconocida"
    PressureLevel.NORMAL -> "normal"
    PressureLevel.ELEVATED -> "elevada"
    PressureLevel.CRITICAL -> "crítica"
}
