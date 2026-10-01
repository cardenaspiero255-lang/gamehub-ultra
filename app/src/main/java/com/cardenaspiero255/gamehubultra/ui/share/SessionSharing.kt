package com.cardenaspiero255.gamehubultra.ui.share

import android.content.Context
import android.content.Intent
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineActionPolicy
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineReportFormatter
import com.cardenaspiero255.gamehubultra.ui.components.formatDuration

internal fun shareSessionHistory(
    context: Context,
    sessions: List<GameSessionRecord>
) {
    if (sessions.isEmpty()) return
    val report = buildString {
        appendLine("GameHub Ultra — historial de sesiones")
        sessions.forEach { session ->
            appendLine("Juego: ${session.packageName}")
            appendLine("Perfil: ${session.profileName}")
            appendLine("Inicio: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(session.startedAtMillis))}")
            appendLine("Duración: ${session.durationMillis?.let { formatDuration(it) } ?: "activa"}")
            session.startBatteryPercent?.let { appendLine("Batería inicio: ${it}%") }
            session.endBatteryPercent?.let { appendLine("Batería fin: ${it}%") }
            session.endRamUsedPercent?.let { appendLine("RAM usada al final: ${it}%") }
            appendLine()
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GameHub Ultra — historial de sesiones")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir historial"))
}

internal fun sharePerformanceTimeline(
    context: Context,
    gamePackage: String?,
    timeline: PerformanceTimeline
) {
    if (!PerformanceTimelineActionPolicy.canShare(timeline)) return
    val report = PerformanceTimelineReportFormatter.format(gamePackage, timeline)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GameHub Ultra — Performance Timeline")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir timeline"))
}
