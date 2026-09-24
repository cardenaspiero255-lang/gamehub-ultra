package com.cardenaspiero255.gamehubultra.domain

import kotlin.math.round

data class PerformanceTimelineSample(
    val timestampMillis: Long,
    val batteryPercent: Int?,
    val thermalStatus: Int?,
    val thermalHeadroomPercent: Int?,
    val refreshRateHz: Int?,
    val ramUsedPercent: Int?
)

data class PerformanceTimeline(
    val samples: List<PerformanceTimelineSample>,
    val profileEvents: List<PerformanceEvent>,
    val thermalEvents: List<PerformanceEvent>,
    val fpsAvailable: Boolean
)

object PerformanceTimelineActionPolicy {
    fun canShare(timeline: PerformanceTimeline): Boolean =
        timeline.samples.isNotEmpty() ||
            timeline.profileEvents.isNotEmpty() ||
            timeline.thermalEvents.isNotEmpty()

    fun shareLabel(timeline: PerformanceTimeline): String =
        if (canShare(timeline)) "Compartir" else "Sin datos"

    fun disabledShareReason(timeline: PerformanceTimeline): String? =
        if (canShare(timeline)) {
            null
        } else {
            "El reporte se habilita cuando haya telemetría o eventos reales de una sesión."
        }
}

object PerformanceTimelineBuilder {
    fun sample(
        timestampMillis: Long,
        batteryPercent: Int?,
        thermalStatus: Int?,
        thermalHeadroom: Float?,
        refreshRateHz: Float?,
        ramUsedPercent: Int?
    ): PerformanceTimelineSample = PerformanceTimelineSample(
        timestampMillis = timestampMillis,
        batteryPercent = batteryPercent?.coerceIn(0, 100),
        thermalStatus = thermalStatus,
        thermalHeadroomPercent = thermalHeadroom?.takeIf { !it.isNaN() && it >= 0f }
            ?.coerceIn(0f, 1f)?.let { round(it * 100f).toInt() },
        refreshRateHz = refreshRateHz?.takeIf { !it.isNaN() && it > 0f }
            ?.let(::round)?.toInt(),
        ramUsedPercent = ramUsedPercent?.coerceIn(0, 100)
    )

    fun build(
        samples: List<PerformanceTimelineSample>,
        events: List<PerformanceEvent>,
        activeSessionId: String? = null
    ): PerformanceTimeline {
        val sessionEvents = activeSessionId?.let { id -> events.filter { it.sessionId == id } } ?: events
        val orderedEvents = sessionEvents.sortedBy { it.timestampMillis }
        return PerformanceTimeline(
            samples = samples.sortedBy { it.timestampMillis }.takeLast(24),
            profileEvents = orderedEvents.filter {
                it.type == PerformanceEventType.POLICY_CHANGED && it.profile != null
            }.takeLast(12),
            thermalEvents = orderedEvents.filter {
                it.type == PerformanceEventType.THERMAL_CHANGED
            }.takeLast(12),
            fpsAvailable = false
        )
    }
}

object PerformanceTimelineReportFormatter {
    fun format(gamePackage: String?, timeline: PerformanceTimeline): String = buildString {
        appendLine("GameHub Ultra — Performance Timeline")
        appendLine("Juego: " + (gamePackage ?: "ninguno"))
        appendLine("FPS: no disponible mediante las APIs expuestas actualmente")
        appendLine()
        if (timeline.samples.isEmpty()) {
            appendLine("No hay muestras de telemetría.")
        } else {
            appendLine("Muestras:")
            timeline.samples.forEach { sample ->
                appendLine(
                    listOf(
                        sample.timestampMillis.toString(),
                        "batería=" + (sample.batteryPercent?.let { it.toString() + "%" } ?: "n/d"),
                        "térmica=" + (sample.thermalStatus?.toString() ?: "n/d"),
                        "margen=" + (sample.thermalHeadroomPercent?.let { it.toString() + "%" } ?: "n/d"),
                        "refresco=" + (sample.refreshRateHz?.let { it.toString() + " Hz" } ?: "n/d"),
                        "RAM=" + (sample.ramUsedPercent?.let { it.toString() + "%" } ?: "n/d")
                    ).joinToString(" · ")
                )
            }
        }
        if (timeline.profileEvents.isNotEmpty()) {
            appendLine()
            appendLine("Cambios de perfil:")
            timeline.profileEvents.forEach { event ->
                appendLine(event.timestampMillis.toString() + ": " + (event.profile?.title ?: "desconocido"))
            }
        }
        if (timeline.thermalEvents.isNotEmpty()) {
            appendLine()
            appendLine("Cambios térmicos:")
            timeline.thermalEvents.forEach { event ->
                appendLine(event.timestampMillis.toString() + ": " + event.detail)
            }
        }
    }
}
