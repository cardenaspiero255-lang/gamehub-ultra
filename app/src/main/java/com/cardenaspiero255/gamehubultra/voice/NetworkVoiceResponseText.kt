package com.cardenaspiero255.gamehubultra.voice

import java.util.Locale

object NetworkVoiceResponseText {
    fun format(result: VoiceActionResult.NetworkReport): String {
        val metrics = result.snapshot.metrics
        return when (result.request) {
            NetworkVoiceRequest.STATUS -> buildString {
                append("Conexión ")
                append(metrics.stability.name.lowercase(Locale.ROOT))
                metrics.averageLatencyMs?.let {
                    append(", latencia media ")
                    append(formatNumber(it))
                    append(" ms")
                }
                metrics.jitterMs?.let {
                    append(", jitter ")
                    append(formatNumber(it))
                    append(" ms")
                }
                metrics.packetLossPercent?.let {
                    append(", pérdida ")
                    append(formatNumber(it))
                    append("%")
                } ?: append(", pérdida de paquetes no medida")
                append(". Perfil recomendado: ")
                append(result.snapshot.recommendedProfile.name.lowercase(Locale.ROOT))
                append(".")
            }

            NetworkVoiceRequest.PACKET_LOSS -> {
                val loss = metrics.packetLossPercent
                if (loss == null) {
                    "No tengo una medición fiable de pérdida de paquetes todavía."
                } else {
                    "La pérdida de paquetes medida es ${formatNumber(loss)}%."
                }
            }

            NetworkVoiceRequest.OPTIMIZE -> {
                val profile = result.snapshot.recommendedProfile.name.lowercase(Locale.ROOT)
                if (result.optimizationApplied) {
                    "Perfil de red $profile aplicado con las capacidades disponibles en Android."
                } else {
                    "Recomiendo el perfil de red $profile. No pude aplicar cambios adicionales automáticamente con los permisos o capacidades actuales."
                }
            }
        }
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
