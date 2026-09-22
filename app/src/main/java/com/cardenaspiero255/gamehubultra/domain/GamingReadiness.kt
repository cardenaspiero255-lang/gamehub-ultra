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
            input.totalRamMb >= 4096 -> {
                score += 5
                reasons += "RAM: 4–7 GB (+5)."
            }
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
            input.batteryPercent == null ->
                reasons += "Batería: porcentaje no disponible."
            input.batteryPercent <= 15 && !input.charging -> {
                score -= 15
                reasons += "Batería baja sin carga."
            }
            input.batteryPercent >= 60 || input.charging -> {
                score += 5
                reasons += "Batería: nivel/carga adecuados (+5)."
            }
        }

        when {
            input.refreshRateHz == null ->
                reasons += "Refresco: no disponible."
            input.refreshRateHz >= 120f -> {
                score += 10
                reasons += "Refresco: 120 Hz o superior (+10)."
            }
            input.refreshRateHz >= 90f -> {
                score += 5
                reasons += "Refresco: 90–119 Hz (+5)."
            }
        }

        if (input.networkValidated) {
            score += 5
            reasons += "Red: conexión validada para Internet (+5)."
        } else {
            reasons += "Red: sin validación de Internet."
        }

        when {
            input.networkLatencyMs == null ->
                reasons += "Latencia: no medida."
            input.networkLatencyMs <= 40L -> {
                score += 5
                reasons += "Latencia: ≤40 ms (+5)."
            }
            input.networkLatencyMs >= 120L -> {
                score -= 10
                reasons += "Latencia de red alta."
            }
        }

        when {
            input.downstreamBandwidthKbps == null ->
                reasons += "Ancho de banda: no disponible."
            input.downstreamBandwidthKbps >= 50_000 -> {
                score += 5
                reasons += "Ancho de banda: ≥50 Mbps (+5)."
            }
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
            input.storageFreePercent >= 25 -> {
                score += 5
                reasons += "Almacenamiento: 25 % o más libre (+5)."
            }
        }

        if (input.inputDeviceCount > 0) {
            score += 2
            reasons += "Periféricos: dispositivo físico detectado (+2)."
        } else {
            reasons += "Periféricos: no se detectaron dispositivos físicos."
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
