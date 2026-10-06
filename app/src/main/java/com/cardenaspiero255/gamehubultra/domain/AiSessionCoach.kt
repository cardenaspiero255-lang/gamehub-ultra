package com.cardenaspiero255.gamehubultra.domain

enum class SessionCoachSignal {
    GENERAL,
    THERMAL,
    BATTERY,
    REFRESH,
    LATENCY
}

enum class SessionCoachPriority {
    INFO,
    WATCH,
    ACTION
}

data class SessionCoachSnapshot(
    val timestampMillis: Long,
    val batteryPercent: Int?,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val refreshRateHz: Float?,
    val latencyMs: Long?
)

data class SessionCoachMessage(
    val signal: SessionCoachSignal,
    val priority: SessionCoachPriority,
    val title: String,
    val detail: String,
    val action: String? = null
)

data class SessionCoachPattern(
    val signal: SessionCoachSignal,
    val occurrences: Int,
    val summary: String,
    val action: String
)

data class SessionCoachPostSessionReport(
    val summary: String,
    val nextSteps: List<String>,
    val patterns: List<SessionCoachPattern>,
    val batteryDropPercent: Int?
)

object AiSessionCoach {
    private const val MEANINGFUL_BATTERY_DROP = 5
    private const val RECURRING_BATTERY_DROP = 15
    private const val MEANINGFUL_REFRESH_DROP_HZ = 20f
    private const val HIGH_LATENCY_MS = 120L
    private const val MEANINGFUL_LATENCY_JUMP_MS = 40L
    private const val HIGH_THERMAL_HEADROOM = 0.80f
    private const val MEANINGFUL_HEADROOM_JUMP = 0.15f
    private const val RECURRING_EVIDENCE_COUNT = 3

    fun preSession(
        readiness: GamingReadiness,
        snapshot: SessionCoachSnapshot
    ): SessionCoachMessage {
        val concerns = buildList {
            if (snapshot.batteryPercent != null && snapshot.batteryPercent <= 15) {
                add("Batería baja: ${snapshot.batteryPercent} %.")
            }
            if (snapshot.thermalStatus != null && snapshot.thermalStatus >= 3) {
                add("Estado térmico elevado (${snapshot.thermalStatus}).")
            }
            if (
                snapshot.thermalHeadroom != null &&
                !snapshot.thermalHeadroom.isNaN() &&
                snapshot.thermalHeadroom >= HIGH_THERMAL_HEADROOM
            ) {
                add("Margen térmico reducido.")
            }
            if (snapshot.latencyMs != null && snapshot.latencyMs >= HIGH_LATENCY_MS) {
                add("Latencia elevada: ${snapshot.latencyMs} ms.")
            }

            readiness.reasons
                .asSequence()
                .filter(::isReadinessConcern)
                .filterNot { reason ->
                    any { existing ->
                        existing.substringBefore(":").equals(
                            reason.substringBefore(":"),
                            ignoreCase = true
                        )
                    }
                }
                .take(3)
                .forEach(::add)
        }

        val detail = buildString {
            append(readiness.label)
            if (concerns.isEmpty()) {
                append(" · Sin alertas relevantes con las métricas disponibles.")
            } else {
                append(" · ")
                append(concerns.joinToString(" "))
            }
        }

        val priority = when {
            readiness.score < 60 -> SessionCoachPriority.ACTION
            concerns.isNotEmpty() -> SessionCoachPriority.WATCH
            else -> SessionCoachPriority.INFO
        }

        return SessionCoachMessage(
            signal = SessionCoachSignal.GENERAL,
            priority = priority,
            title = "Preparación ${readiness.score}/100",
            detail = detail,
            action = when (priority) {
                SessionCoachPriority.ACTION ->
                    "Revisa los factores marcados antes de iniciar una sesión exigente."
                SessionCoachPriority.WATCH ->
                    "Puedes jugar, pero conviene vigilar los factores señalados."
                SessionCoachPriority.INFO -> null
            }
        )
    }

    fun midSession(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): List<SessionCoachMessage> {
        val observations = mutableListOf<SessionCoachMessage>()

        thermalObservation(previous, current)?.let(observations::add)
        batteryObservation(previous, current)?.let(observations::add)
        refreshObservation(previous, current)?.let(observations::add)
        latencyObservation(previous, current)?.let(observations::add)

        return observations
    }

    fun recurringPatterns(
        samples: List<SessionCoachSnapshot>
    ): List<SessionCoachPattern> {
        val ordered = samples.sortedBy { it.timestampMillis }
        if (ordered.size < RECURRING_EVIDENCE_COUNT) return emptyList()

        val patterns = mutableListOf<SessionCoachPattern>()

        val thermalOccurrences = ordered.count { sample ->
            (sample.thermalStatus != null && sample.thermalStatus >= 3) ||
                (
                    sample.thermalHeadroom != null &&
                        !sample.thermalHeadroom.isNaN() &&
                        sample.thermalHeadroom >= HIGH_THERMAL_HEADROOM
                    )
        }
        if (thermalOccurrences >= RECURRING_EVIDENCE_COUNT) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.THERMAL,
                occurrences = thermalOccurrences,
                summary = "La presión térmica elevada se repitió durante la sesión.",
                action =
                    "Prueba un perfil menos exigente para reducir presión térmica o mejora la ventilación si el patrón se repite."
            )
        }

        val batteryValues = ordered.mapNotNull { it.batteryPercent }
        val batteryDrop = batteryValues.firstOrNull()?.let { first ->
            batteryValues.lastOrNull()?.let { last -> (first - last).coerceAtLeast(0) }
        }
        if (
            batteryValues.size >= RECURRING_EVIDENCE_COUNT &&
            batteryDrop != null &&
            batteryDrop >= RECURRING_BATTERY_DROP
        ) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.BATTERY,
                occurrences = batteryValues.size,
                summary = "La batería cayó $batteryDrop % durante las muestras observadas.",
                action =
                    "Si necesitas más autonomía, usa un perfil equilibrado o reduce carga visual compatible."
            )
        }

        val refreshOccurrences = ordered.count { sample ->
            sample.refreshRateHz?.let { it in 1f..60f } == true
        }
        if (refreshOccurrences >= RECURRING_EVIDENCE_COUNT) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.REFRESH,
                occurrences = refreshOccurrences,
                summary = "El refresco observado se mantuvo en 60 Hz o menos repetidamente.",
                action =
                    "Comprueba que el perfil y la frecuencia solicitada sean compatibles con el juego y la pantalla."
            )
        }

        val latencyOccurrences = ordered.count { sample ->
            sample.latencyMs?.let { it >= HIGH_LATENCY_MS } == true
        }
        if (latencyOccurrences >= RECURRING_EVIDENCE_COUNT) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.LATENCY,
                occurrences = latencyOccurrences,
                summary = "La latencia alta se repitió durante la sesión.",
                action =
                    "Revisa estabilidad de la red, señal Wi-Fi o congestión antes de la próxima partida competitiva."
            )
        }

        return patterns
    }

    fun postSession(
        samples: List<SessionCoachSnapshot>
    ): SessionCoachPostSessionReport {
        val ordered = samples.sortedBy { it.timestampMillis }
        if (ordered.isEmpty()) {
            return SessionCoachPostSessionReport(
                summary = "No hubo telemetría suficiente para resumir la sesión.",
                nextSteps = emptyList(),
                patterns = emptyList(),
                batteryDropPercent = null
            )
        }

        val batteryValues = ordered.mapNotNull { it.batteryPercent }
        val batteryDrop = batteryValues.firstOrNull()?.let { first ->
            batteryValues.lastOrNull()?.let { last -> (first - last).coerceAtLeast(0) }
        }
        val patterns = recurringPatterns(ordered)
        val nextSteps = patterns
            .map(SessionCoachPattern::action)
            .distinct()

        val summary = buildString {
            append("Sesión analizada con ${ordered.size} muestras")
            batteryDrop?.let { append(" · batería -$it %") }
            if (patterns.isEmpty()) {
                append(" · sin patrones repetidos relevantes.")
            } else {
                append(" · ${patterns.size} patrón(es) relevante(s) detectado(s).")
            }
        }

        return SessionCoachPostSessionReport(
            summary = summary,
            nextSteps = nextSteps,
            patterns = patterns,
            batteryDropPercent = batteryDrop
        )
    }

    private fun thermalObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        val statusEscalated =
            current.thermalStatus != null &&
                current.thermalStatus >= 3 &&
                (previous.thermalStatus == null || current.thermalStatus > previous.thermalStatus)

        val headroomEscalated =
            current.thermalHeadroom != null &&
                !current.thermalHeadroom.isNaN() &&
                current.thermalHeadroom >= HIGH_THERMAL_HEADROOM &&
                (
                    previous.thermalHeadroom == null ||
                        previous.thermalHeadroom.isNaN() ||
                        current.thermalHeadroom - previous.thermalHeadroom >=
                        MEANINGFUL_HEADROOM_JUMP
                    )

        if (!statusEscalated && !headroomEscalated) return null

        return SessionCoachMessage(
            signal = SessionCoachSignal.THERMAL,
            priority = SessionCoachPriority.ACTION,
            title = "Cambio térmico relevante",
            detail = "La presión térmica aumentó de forma suficiente como para afectar una sesión exigente.",
            action =
                "Vigila la temperatura y considera un perfil menos exigente si la tendencia continúa."
        )
    }

    private fun batteryObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        val before = previous.batteryPercent ?: return null
        val after = current.batteryPercent ?: return null
        val drop = before - after
        if (drop < MEANINGFUL_BATTERY_DROP && after > 15) return null

        return SessionCoachMessage(
            signal = SessionCoachSignal.BATTERY,
            priority = if (after <= 15) {
                SessionCoachPriority.ACTION
            } else {
                SessionCoachPriority.WATCH
            },
            title = if (after <= 15) "Batería baja" else "Consumo de batería relevante",
            detail = "La batería pasó de $before % a $after %.",
            action = if (after <= 15) {
                "Conecta el cargador si es seguro para tu dispositivo o termina la sesión pronto."
            } else {
                "Si priorizas autonomía, considera un perfil equilibrado."
            }
        )
    }

    private fun refreshObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        val before = previous.refreshRateHz ?: return null
        val after = current.refreshRateHz ?: return null
        if (
            before < 90f ||
            after > before - MEANINGFUL_REFRESH_DROP_HZ
        ) {
            return null
        }

        return SessionCoachMessage(
            signal = SessionCoachSignal.REFRESH,
            priority = SessionCoachPriority.WATCH,
            title = "Caída de refresco observada",
            detail = "El refresco observado pasó de ${before.toInt()} Hz a ${after.toInt()} Hz.",
            action =
                "Comprueba si el juego, el perfil y la pantalla permiten la frecuencia esperada."
        )
    }

    private fun latencyObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        val after = current.latencyMs ?: return null
        val before = previous.latencyMs
        val meaningfulJump =
            before == null || after - before >= MEANINGFUL_LATENCY_JUMP_MS

        if (after < HIGH_LATENCY_MS || !meaningfulJump) return null

        return SessionCoachMessage(
            signal = SessionCoachSignal.LATENCY,
            priority = SessionCoachPriority.ACTION,
            title = "Pico de latencia",
            detail = if (before == null) {
                "Se observaron $after ms de latencia."
            } else {
                "La latencia pasó de $before ms a $after ms."
            },
            action =
                "Revisa señal, congestión o cambios de conectividad antes de atribuirlo al juego."
        )
    }

    private fun isReadinessConcern(reason: String): Boolean {
        val normalized = reason.lowercase()
        return normalized.contains("no disponible") ||
            normalized.contains("no medida") ||
            normalized.contains("sin validación") ||
            normalized.contains("nivel bajo") ||
            normalized.contains("severo") ||
            normalized.contains("elevado") ||
            normalized.contains("menos de")
    }
}
