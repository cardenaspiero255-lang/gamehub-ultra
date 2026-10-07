package com.cardenaspiero255.gamehubultra.domain

data class BatteryAwareGamingPolicy(
    val criticalBatteryPercent: Int = 15,
    val constrainedBatteryPercent: Int = 35,
    val highDrainPercentPerHour: Float = 20f,
    val minimumDrainWindowMillis: Long = 5 * 60_000L,
) {
    init {
        require(criticalBatteryPercent in 0..100)
        require(constrainedBatteryPercent in criticalBatteryPercent..100)
        require(highDrainPercentPerHour > 0f)
        require(minimumDrainWindowMillis > 0L)
    }
}

enum class BatteryGamingRecommendation {
    NORMAL,
    CHARGING,
    BALANCED,
    CONSERVE,
}

data class BatteryGamingAssessment(
    val currentPercent: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean,
    val observedDropPercent: Int?,
    val observedDurationMillis: Long?,
    val drainPercentPerHour: Float?,
    val recommendation: BatteryGamingRecommendation,
    val preventAggressiveProfiles: Boolean,
    val reason: String,
)

class BatteryAwareGamingEngine(
    val policy: BatteryAwareGamingPolicy = BatteryAwareGamingPolicy(),
) {
    fun assess(samples: List<SessionCoachSnapshot>): BatteryGamingAssessment {
        val ordered = samples.sortedBy { it.timestampMillis }
        val latest = ordered.lastOrNull()
        val charging = latest?.batteryCharging == true
        val powerSaveMode = latest?.powerSaveMode == true

        val currentBatteryWindow = ordered
            .asReversed()
            .takeWhile { it.batteryCharging != true }
        val currentPercent = latest?.batteryPercent
            ?: currentBatteryWindow.firstNotNullOfOrNull { it.batteryPercent }

        val drainWindow = currentBatteryWindow
            .filter { it.batteryPercent != null }
            .asReversed()

        val firstDrainSample = drainWindow.firstOrNull()
        val lastDrainSample = drainWindow.lastOrNull()
        val observedDurationMillis = if (
            firstDrainSample != null &&
            lastDrainSample != null &&
            drainWindow.size >= 2
        ) {
            (lastDrainSample.timestampMillis - firstDrainSample.timestampMillis)
                .coerceAtLeast(0L)
        } else {
            null
        }

        val observedDropPercent = observedDurationMillis?.let {
            val firstPercent = checkNotNull(firstDrainSample?.batteryPercent)
            val lastPercent = checkNotNull(lastDrainSample?.batteryPercent)
            (firstPercent - lastPercent).coerceAtLeast(0)
        }

        val drainPercentPerHour = if (
            observedDurationMillis != null &&
            observedDurationMillis >= policy.minimumDrainWindowMillis &&
            observedDropPercent != null &&
            observedDropPercent > 0
        ) {
            (
                observedDropPercent.toFloat() *
                    3_600_000f /
                    observedDurationMillis.toFloat()
                )
                .coerceAtLeast(0f)
        } else {
            null
        }

        val recommendation = when {
            powerSaveMode -> BatteryGamingRecommendation.CONSERVE
            charging -> BatteryGamingRecommendation.CHARGING
            currentPercent != null &&
                currentPercent <= policy.criticalBatteryPercent ->
                BatteryGamingRecommendation.CONSERVE
            currentPercent != null &&
                currentPercent <= policy.constrainedBatteryPercent ->
                BatteryGamingRecommendation.BALANCED
            drainPercentPerHour != null &&
                drainPercentPerHour >= policy.highDrainPercentPerHour ->
                BatteryGamingRecommendation.BALANCED
            else -> BatteryGamingRecommendation.NORMAL
        }

        val preventAggressiveProfiles =
            recommendation == BatteryGamingRecommendation.BALANCED ||
                recommendation == BatteryGamingRecommendation.CONSERVE

        val reason = when (recommendation) {
            BatteryGamingRecommendation.CONSERVE -> when {
                powerSaveMode ->
                    "El modo de ahorro de batería está activo; evita perfiles agresivos mientras permanezca esta restricción."
                currentPercent != null ->
                    "Batería baja (" + currentPercent + " %); conviene priorizar autonomía y evitar perfiles agresivos."
                else ->
                    "La batería está restringida; conviene priorizar autonomía."
            }
            BatteryGamingRecommendation.BALANCED -> when {
                drainPercentPerHour != null &&
                    drainPercentPerHour >= policy.highDrainPercentPerHour ->
                    "El drenaje medido es elevado (~" + drainPercentPerHour.toInt() + " %/h); se recomienda un perfil equilibrado."
                currentPercent != null ->
                    "La batería está en " + currentPercent + " %; se recomienda un perfil equilibrado para conservar autonomía."
                else ->
                    "Las señales de batería recomiendan un perfil equilibrado."
            }
            BatteryGamingRecommendation.CHARGING ->
                "El dispositivo está cargando; el drenaje de batería no se estima durante este tramo."
            BatteryGamingRecommendation.NORMAL ->
                "Sin restricciones relevantes de batería con las muestras disponibles."
        }

        return BatteryGamingAssessment(
            currentPercent = currentPercent,
            charging = charging,
            powerSaveMode = powerSaveMode,
            observedDropPercent = observedDropPercent,
            observedDurationMillis = observedDurationMillis,
            drainPercentPerHour = drainPercentPerHour,
            recommendation = recommendation,
            preventAggressiveProfiles = preventAggressiveProfiles,
            reason = reason,
        )
    }
}
