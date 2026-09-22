package com.cardenaspiero255.gamehubultra.domain

data class GamingReadinessInput(
    val cpuCores: Int,
    val totalRamMb: Long,
    val gpuAvailable: Boolean,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val refreshRateHz: Float?,
    val networkValidated: Boolean,
    val networkLatencyMs: Long?,
    val downstreamBandwidthKbps: Int?,
    val storageFreePercent: Int,
    val inputDeviceCount: Int
)

data class GamingReadiness(
    val score: Int,
    val label: String,
    val reasons: List<String>
)

object GamingReadinessCalculator {
    fun calculate(input: GamingReadinessInput): GamingReadiness {
        var score = 50
        val reasons = mutableListOf<String>()

        if (input.cpuCores >= 8) {
            score += 10
            reasons += "CPU con múltiples núcleos lógicos detectados."
        } else if (input.cpuCores >= 4) {
            score += 5
            reasons += "CPU con una base adecuada de núcleos lógicos."
        } else {
            score -= 5
            reasons += "Pocos núcleos lógicos disponibles."
        }

        when {
            input.totalRamMb >= 8192 -> {
                score += 10
                reasons += "RAM disponible en un rango amplio para juegos modernos."
            }
            input.totalRamMb >= 4096 -> score += 5
            else -> {
                score -= 10
                reasons += "RAM total limitada para cargas pesadas."
            }
        }

        if (input.gpuAvailable) {
            score += 10
            reasons += "GPU identificable."
        } else {
            score -= 5
            reasons += "No se pudo identificar la GPU."
        }

        when {
            input.thermalStatus != null && input.thermalStatus >= 3 -> {
                score -= 25
                reasons += "Estado térmico elevado."
            }
            input.thermalHeadroom != null && input.thermalHeadroom < 0.20f -> {
                score -= 20
                reasons += "Margen térmico bajo."
            }
            input.thermalHeadroom != null && input.thermalHeadroom >= 0.50f -> score += 5
        }

        when {
            input.batteryPercent == null -> Unit
            input.batteryPercent <= 15 && !input.charging -> {
                score -= 15
                reasons += "Batería baja sin carga."
            }
            input.batteryPercent >= 60 || input.charging -> score += 5
        }

        when {
            input.refreshRateHz == null -> Unit
            input.refreshRateHz >= 120f -> {
                score += 10
                reasons += "Pantalla con refresco de 120 Hz o superior."
            }
            input.refreshRateHz >= 90f -> score += 5
        }

        if (input.networkValidated) {
            score += 5
        } else {
            reasons += "La red no está validada para Internet."
        }

        when {
            input.networkLatencyMs == null -> Unit
            input.networkLatencyMs <= 40L -> score += 5
            input.networkLatencyMs >= 120L -> {
                score -= 10
                reasons += "Latencia de red alta."
            }
        }

        when {
            input.downstreamBandwidthKbps == null -> Unit
            input.downstreamBandwidthKbps >= 50_000 -> score += 5
            input.downstreamBandwidthKbps < 10_000 -> {
                score -= 5
                reasons += "Ancho de banda estimado limitado."
            }
        }

        when {
            input.storageFreePercent < 10 -> {
                score -= 15
                reasons += "Poco espacio libre."
            }
            input.storageFreePercent >= 25 -> score += 5
        }

        if (input.inputDeviceCount > 0) {
            score += 2
        }

        val finalScore = score.coerceIn(0, 100)
        val label = when {
            finalScore >= 80 -> "Listo"
            finalScore >= 60 -> "Aceptable"
            else -> "Requiere atención"
        }
        return GamingReadiness(finalScore, label, reasons.distinct())
    }
}
