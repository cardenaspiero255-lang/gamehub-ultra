package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome

object NetworkVoiceResponseText {
    fun format(result: VoiceActionResult.NetworkReport): String {
        val metrics = result.snapshot.metrics
        return when (result.request) {
            NetworkVoiceRequest.STATUS -> buildString {
                append("Conexión ")
                append(stabilityLabel(metrics.stability))
                metrics.averageLatencyMs?.let {
                    append(", latencia media ")
                    append(formatNumber(it))
                    append(" ms")
                }
                metrics.jitterMs?.let {
                    append(", variación ")
                    append(formatNumber(it))
                    append(" ms")
                }
                metrics.packetLossPercent?.let {
                    append(", pérdida de paquetes ")
                    append(formatNumber(it))
                    append("%")
                } ?: append(", pérdida de paquetes todavía no medida")
                append(". Perfil recomendado: ")
                append(profileLabel(result.snapshot.recommendedProfile))
                append(".")
            }
            NetworkVoiceRequest.PACKET_LOSS -> {
                val loss = metrics.packetLossPercent
                if (loss == null) {
                    "Todavía no tengo una medición fiable de pérdida de paquetes."
                } else {
                    "La pérdida de paquetes medida es ${formatNumber(loss)}%."
                }
            }
            NetworkVoiceRequest.OPTIMIZE -> {
                val profile = profileLabel(result.snapshot.recommendedProfile)
                when (result.optimizationOutcome) {
                    NetworkOptimizationOutcome.APPLIED ->
                        "Optimización de red activada. Perfil de red $profile aplicado con las capacidades permitidas por Android."
                    NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED ->
                        "No fue necesario activar prioridad competitiva. Dejé la red en perfil $profile con los ajustes locales disponibles."
                    NetworkOptimizationOutcome.UNAVAILABLE ->
                        "No pude aplicar la optimización automáticamente. Recomiendo el perfil $profile; revisa que estés conectado a una red compatible."
                    NetworkOptimizationOutcome.NOT_REQUESTED ->
                        "No se solicitó ningún cambio de red."
                }
            }
        }
    }

    private fun stabilityLabel(value: NetworkStability): String = when (value) {
        NetworkStability.OFFLINE -> "sin conexión"
        NetworkStability.UNMEASURED -> "activa, todavía sin medir"
        NetworkStability.EXCELLENT -> "excelente"
        NetworkStability.GOOD -> "buena"
        NetworkStability.FAIR -> "regular"
        NetworkStability.POOR -> "deficiente"
    }

    private fun profileLabel(value: NetworkGameProfile): String = when (value) {
        NetworkGameProfile.COMPETITIVE -> "competitivo"
        NetworkGameProfile.BALANCED -> "equilibrado"
        NetworkGameProfile.DATA_SAVER -> "ahorro de datos"
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
