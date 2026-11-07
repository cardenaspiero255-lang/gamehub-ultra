package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord

/**
 * CAR-61: a read-only reconstruction from already persisted session records
 * and session-scoped performance events. It does not invent telemetry samples.
 */
internal enum class SessionReplayMarkerKind {
    START, MANUAL_PROFILE, AUTOMATIC_PROFILE, UNKNOWN_PROFILE, THERMAL, END
}

internal data class SessionReplayMarker(
    val kind: SessionReplayMarkerKind,
    val elapsedMillis: Long,
    val profileName: String? = null,
    val batteryPercent: Int? = null,
    val thermalStatus: Int? = null,
    val refreshRateHz: Int? = null,
    val latencyMillis: Long? = null
) {
    /** Only format validated numbers and known enum/profile values, never raw event details. */
    fun label(): String = when (kind) {
        SessionReplayMarkerKind.START ->
            "Inicio · perfil ${profileName.orEmpty().ifBlank { "sin datos" }}" +
                (batteryPercent?.let { " · batería $it%" } ?: "")
        SessionReplayMarkerKind.MANUAL_PROFILE ->
            "Perfil manual · ${profileName ?: "sin datos"}"
        SessionReplayMarkerKind.AUTOMATIC_PROFILE ->
            "Perfil automático · ${profileName ?: "sin datos"}"
        SessionReplayMarkerKind.UNKNOWN_PROFILE ->
            "Cambio de perfil registrado · ${profileName ?: "sin datos"}"
        SessionReplayMarkerKind.THERMAL ->
            "Cambio térmico · estado ${thermalStatus ?: "sin datos"}"
        SessionReplayMarkerKind.END ->
            "Fin" +
                (batteryPercent?.let { " · batería $it%" } ?: "") +
                (thermalStatus?.let { " · estado térmico $it" } ?: "")
    }
}

internal data class SessionReplayTimeline(
    val sessionId: String,
    val markers: List<SessionReplayMarker>,
    val recordedEventCount: Int,
    val shownEventCount: Int,
    val truncated: Boolean,
    val hasRefreshHistory: Boolean = false,
    val hasLatencyHistory: Boolean = false
)

/**
 * Session records retain start/end battery and last thermal status, not full
 * refresh, latency or FPS history. A replay must never imply those samples exist.
 */
internal object SessionReplayTimelineBuilder {
    private const val MAX_REPLAY_EVENTS = 20

    fun build(
        session: GameSessionRecord,
        events: List<PerformanceEvent>
    ): SessionReplayTimeline? {
        val endMillis = session.endedAtMillis ?: return null
        if (session.id.isBlank() || session.startedAtMillis < 0L ||
            endMillis < session.startedAtMillis
        ) return null

        val matching = events.asSequence()
            .filter { event ->
                event.sessionId == session.id &&
                    event.timestampMillis in session.startedAtMillis..endMillis
            }
            .mapNotNull { event ->
                val elapsed = event.timestampMillis - session.startedAtMillis
                when (event.type) {
                    PerformanceEventType.POLICY_CHANGED -> {
                        val profile = event.profile ?: return@mapNotNull null
                        val kind = when (event.detail) {
                            "manual_profile_selection" ->
                                SessionReplayMarkerKind.MANUAL_PROFILE
                            "" -> SessionReplayMarkerKind.UNKNOWN_PROFILE
                            else -> SessionReplayMarkerKind.AUTOMATIC_PROFILE
                        }
                        SessionReplayMarker(
                            kind = kind,
                            elapsedMillis = elapsed,
                            profileName = profile.title
                        )
                    }
                    PerformanceEventType.THERMAL_CHANGED -> {
                        val thermal = event.detail.toIntOrNull()
                            ?.takeIf { it in 0..6 } ?: return@mapNotNull null
                        SessionReplayMarker(
                            kind = SessionReplayMarkerKind.THERMAL,
                            elapsedMillis = elapsed,
                            thermalStatus = thermal
                        )
                    }
                    else -> null
                }
            }
            .sortedBy { it.elapsedMillis }
            .toList()
        val visible = matching.takeLast(MAX_REPLAY_EVENTS)
        val start = SessionReplayMarker(
            kind = SessionReplayMarkerKind.START,
            elapsedMillis = 0L,
            profileName = session.profileName.takeIf(String::isNotBlank),
            batteryPercent = session.startBatteryPercent?.takeIf { it in 0..100 }
        )
        val end = SessionReplayMarker(
            kind = SessionReplayMarkerKind.END,
            elapsedMillis = endMillis - session.startedAtMillis,
            batteryPercent = session.endBatteryPercent?.takeIf { it in 0..100 },
            thermalStatus = session.endThermalStatus?.takeIf { it in 0..6 }
        )
        return SessionReplayTimeline(
            sessionId = session.id,
            markers = listOf(start) + visible + end,
            recordedEventCount = matching.size,
            shownEventCount = visible.size,
            truncated = matching.size > visible.size
        )
    }
}
