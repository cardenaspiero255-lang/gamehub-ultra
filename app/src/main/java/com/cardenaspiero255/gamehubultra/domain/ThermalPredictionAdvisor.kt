package com.cardenaspiero255.gamehubultra.domain

object ThermalPredictionAdvisor {
    private const val PREVENTIVE_TITLE = "Riesgo térmico previsto"
    private const val RECOVERY_TITLE = "Recuperación térmica estimada"

    fun message(
        prediction: ThermalPrediction,
        previousObservation: SessionCoachMessage?
    ): SessionCoachMessage? {
        if (prediction.recovering && previousObservation?.title == PREVENTIVE_TITLE) {
            return SessionCoachMessage(
                signal = SessionCoachSignal.THERMAL,
                priority = SessionCoachPriority.INFO,
                title = RECOVERY_TITLE,
                detail = "Estimación térmica: la tendencia reciente muestra recuperación. " +
                    "No es una lectura directa de temperatura.",
                action = null
            )
        }

        if (!prediction.allowPreventiveSignal) return null
        if (
            previousObservation?.signal == SessionCoachSignal.THERMAL &&
            previousObservation.title == PREVENTIVE_TITLE
        ) {
            return null
        }

        val confidencePercent = (prediction.confidence.coerceIn(0f, 1f) * 100f).toInt()
        val detail = "Estimación predictiva: la presión térmica reciente muestra ${prediction.trend.name.lowercase()} con riesgo ${prediction.risk.name.lowercase()} y confianza $confidencePercent%. No es una lectura directa de temperatura ni confirma throttling."
        val action = "Considera un perfil menos exigente. El optimizador adaptativo mantendrá sus confirmaciones, histéresis y cooldown antes de cualquier cambio automático."
        return SessionCoachMessage(
            signal = SessionCoachSignal.THERMAL,
            priority = SessionCoachPriority.ACTION,
            title = PREVENTIVE_TITLE,
            detail = detail,
            action = action
        )
    }
}
