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

        when {
            input.cpuCores >= 8 -> {
                score += 10
                reasons += "CPU: 8 o más núcleos lógicos (+10)."
            }
            input.cpuCores >= 4 -> {
                score += 5
                reasons += "CPU: 4–7 núcleos lógicos (+5)."
            }
            else -> {
                score -= 5
                reasons += "CPU: menos de 4 núcleos lógicos (-5)."
            }
        }

        when {
            input.totalRamMb >= 8192 -> {
                score += 10
                reasons += "RAM: 8 GB o más (+10)."
            }
            input.totalRamMb >= 4096 -> {
                score += 5
                reasons += "RAM: 4–7 GB (+5)."
            }
            else -> {
                score -= 10
                reasons += "RAM: menos de 4 GB (-10)."
            }
        }

        if (input.gpuAvailable) {
            score += 10
            reasons += "GPU: identificada (+10)."
        } else {
            score -= 5
            reasons += "GPU: no identificada (-5)."
        }

        when {
            input.thermalStatus == null ->
                reasons += "Térmica: estado no disponible."
            input.thermalStatus <= 1 -> {
                score += 15
                reasons += "Térmica: sin throttling o throttling leve (+15)."
            }
            input.thermalStatus == 2 -> {
                score += 5
                reasons += "Térmica: throttling moderado (+5)."
            }
            else -> {
                score -= 25
                reasons += "Térmica: throttling severo o superior (-25)."
            }
        }

        when {
            input.thermalHeadroom == null || input.thermalHeadroom.isNaN() ->
                reasons += "Margen térmico: no disponible."
            input.thermalHeadroom <= 0.30f -> {
                score += 10
                reasons += "Margen térmico: uso bajo del sobre térmico (+10)."
            }
            input.thermalHeadroom < 0.60f -> {
                score += 3
                reasons += "Margen térmico: uso moderado del sobre térmico (+3)."
            }
            input.thermalHeadroom < 0.80f -> {
                score -= 10
                reasons += "Margen térmico: uso elevado del sobre térmico (-10)."
            }
            else -> {
                score -= 25
                reasons += "Margen térmico: cerca del umbral de throttling severo (-25)."
            }
        }

        when {
            input.batteryPercent == null ->
                reasons += "Batería: porcentaje no disponible."
            input.batteryPercent >= 60 || input.charging -> {
                score += 10
                reasons += "Batería: nivel/carga adecuados (+10)."
            }
            input.batteryPercent > 15 -> {
                score += 2
                reasons += "Batería: nivel intermedio (+2)."
            }
            else -> {
                score -= 20
                reasons += "Batería: nivel bajo sin carga (-20)."
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
            else ->
                reasons += "Refresco: por debajo de 90 Hz."
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
            input.networkLatencyMs < 120L ->
                reasons += "Latencia: 41–119 ms."
            else -> {
                score -= 10
                reasons += "Latencia: ≥120 ms (-10)."
            }
        }

        when {
            input.downstreamBandwidthKbps == null ->
                reasons += "Ancho de banda: no disponible."
            input.downstreamBandwidthKbps >= 50_000 -> {
                score += 5
                reasons += "Ancho de banda: ≥50 Mbps (+5)."
            }
            input.downstreamBandwidthKbps >= 10_000 ->
                reasons += "Ancho de banda: 10–49 Mbps."
            else -> {
                score -= 5
                reasons += "Ancho de banda: <10 Mbps (-5)."
            }
        }

        when {
            input.storageFreePercent < 10 -> {
                score -= 15
                reasons += "Almacenamiento: menos de 10 % libre (-15)."
            }
            input.storageFreePercent < 25 ->
                reasons += "Almacenamiento: 10–24 % libre."
            else -> {
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

        return GamingReadiness(
            score = finalScore,
            label = label,
            reasons = reasons.distinct()
        )
    }
}
