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
    val latencyMs: Long?,
    val memoryUsedPercent: Int? = null,
    val batteryCharging: Boolean? = null,
    val powerSaveMode: Boolean? = null
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
    val batteryDropPercent: Int?,
    val batteryDrainPercentPerHour: Float? = null,
    val batteryChargingObserved: Boolean = false,
    val batteryRecommendation: BatteryGamingRecommendation? = null
)

object AiSessionCoach {
    private const val MEANINGFUL_BATTERY_DROP = 5
    private const val RECURRING_BATTERY_DROP = 15
    private const val MEANINGFUL_REFRESH_DROP_HZ = 20f
    private const val HIGH_LATENCY_MS = 250L
    private const val MEANINGFUL_LATENCY_JUMP_MS = 40L
    private const val HIGH_THERMAL_HEADROOM = 0.80f
    private const val MEANINGFUL_HEADROOM_JUMP = 0.15f
    private const val RECURRING_EVIDENCE_COUNT = 3

    fun preSession(
        readiness: GamingReadiness,
        snapshot: SessionCoachSnapshot
    ): SessionCoachMessage {
        val coveredFamilies = mutableSetOf<String>()
        val concerns = buildList {
            if (
                snapshot.batteryCharging != true &&
                snapshot.batteryPercent != null &&
                snapshot.batteryPercent <= 15
            ) {
                add("Batería baja: ${snapshot.batteryPercent} %.")
                coveredFamilies += "batería"
            }
            if (snapshot.powerSaveMode == true && "batería" !in coveredFamilies) {
                add("Modo de ahorro de batería activo.")
                coveredFamilies += "batería"
            }
            if (snapshot.thermalStatus != null && snapshot.thermalStatus >= 3) {
                add("Estado térmico elevado (${snapshot.thermalStatus}).")
                coveredFamilies += "térmica"
            }
            val highThermalHeadroom =
                snapshot.thermalHeadroom?.let { !it.isNaN() && it >= HIGH_THERMAL_HEADROOM } == true
            if (highThermalHeadroom) {
                add("Margen térmico reducido.")
                coveredFamilies += "térmica"
            }
            if (snapshot.latencyMs != null && snapshot.latencyMs >= HIGH_LATENCY_MS) {
                add("Latencia elevada: ${snapshot.latencyMs} ms.")
                coveredFamilies += "latencia"
            }

            readiness.reasons
                .asSequence()
                .filter(::isReadinessConcern)
                .filterNot { reason ->
                    reason.substringBefore(":").trim().lowercase() in coveredFamilies
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
                action = "Prueba un perfil menos exigente para reducir presión térmica o mejora la ventilación si el patrón se repite."
            )
        }

        val batteryValues = ordered.mapNotNull { it.batteryPercent }
        val batteryDrop = wholeSessionBatteryDrop(ordered)
        val recurringBatteryDrop =
            batteryValues.size >= RECURRING_EVIDENCE_COUNT &&
                batteryDrop != null &&
                batteryDrop >= RECURRING_BATTERY_DROP
        if (recurringBatteryDrop) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.BATTERY,
                occurrences = batteryValues.size,
                summary = "La batería cayó $batteryDrop % durante las muestras observadas.",
                action = "Si necesitas más autonomía, usa un perfil equilibrado o reduce carga visual compatible."
            )
        }

        val refreshValues = ordered.mapNotNull { it.refreshRateHz }
        val highestRefresh = refreshValues.maxOrNull()
        val refreshOccurrences = if (highestRefresh != null && highestRefresh >= 90f) {
            refreshValues.count { refresh ->
                refresh <= highestRefresh - MEANINGFUL_REFRESH_DROP_HZ
            }
        } else {
            0
        }
        if (refreshOccurrences >= RECURRING_EVIDENCE_COUNT) {
            patterns += SessionCoachPattern(
                signal = SessionCoachSignal.REFRESH,
                occurrences = refreshOccurrences,
                summary = "El refresco observado cayó repetidamente frente al máximo medido de ${highestRefresh?.toInt()} Hz.",
                action = "Comprueba que el perfil, el juego y la pantalla mantengan la frecuencia esperada."
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
                action = "Revisa estabilidad de la red, señal Wi-Fi o congestión antes de la próxima partida competitiva."
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

        val chargingObserved = ordered.any { it.batteryCharging == true }
        val batteryEngine = BatteryAwareGamingEngine()
        val batteryAssessment = batteryEngine.assess(ordered)
        val batteryDrop = wholeSessionBatteryDrop(ordered)
        val batteryDrainRate = wholeSessionBatteryDrainPercentPerHour(
            samples = ordered,
            minimumWindowMillis = batteryEngine.policy.minimumDrainWindowMillis
        )
        val patterns = recurringPatterns(ordered)
        val batteryNextStep = if (batteryAssessment.preventAggressiveProfiles) {
            "Para priorizar autonomía, evita perfiles agresivos hasta que mejore el estado de batería."
        } else {
            null
        }
        val nextSteps = (
            patterns.map(SessionCoachPattern::action) + listOfNotNull(batteryNextStep)
            ).distinct()

        val summary = buildString {
            append("Sesión analizada con ${ordered.size} muestras")
            batteryDrop?.let { append(" · batería -$it %") }
            batteryDrainRate?.let {
                append(" · drenaje estimado ~${it.toInt()} %/h")
            }
            if (chargingObserved) {
                append(" · se observó carga conectada durante la sesión")
            }
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
            batteryDropPercent = batteryDrop,
            batteryDrainPercentPerHour = batteryDrainRate,
            batteryChargingObserved = chargingObserved,
            batteryRecommendation = batteryAssessment.recommendation
        )
    }

    private data class BatteryDischargeSegment(
        val dropPercent: Int,
        val durationMillis: Long
    )

    private fun wholeSessionBatteryDrop(
        samples: List<SessionCoachSnapshot>
    ): Int? {
        val segments = batteryDischargeSegments(samples) ?: return null
        return segments.sumOf(BatteryDischargeSegment::dropPercent)
    }

    private fun wholeSessionBatteryDrainPercentPerHour(
        samples: List<SessionCoachSnapshot>,
        minimumWindowMillis: Long
    ): Float? {
        val segments = batteryDischargeSegments(samples) ?: return null
        return drainRate(
            dropPercent = segments.sumOf(BatteryDischargeSegment::dropPercent),
            durationMillis = segments.sumOf(BatteryDischargeSegment::durationMillis),
            minimumWindowMillis = minimumWindowMillis
        )
    }

    private fun batteryDischargeSegments(
        samples: List<SessionCoachSnapshot>
    ): List<BatteryDischargeSegment>? {
        val ordered = samples.sortedBy { it.timestampMillis }
        val batterySamples = ordered.filter { it.batteryPercent != null }
        if (batterySamples.isEmpty()) return null

        val segments = mutableListOf<BatteryDischargeSegment>()
        var firstPercent: Int? = null
        var firstTimestamp: Long? = null
        var lastPercent: Int? = null
        var lastTimestamp: Long? = null

        fun flushSegment() {
            val startPercent = firstPercent
            val startTimestamp = firstTimestamp
            val endPercent = lastPercent
            val endTimestamp = lastTimestamp
            if (
                startPercent != null &&
                startTimestamp != null &&
                endPercent != null &&
                endTimestamp != null &&
                endTimestamp > startTimestamp
            ) {
                segments += BatteryDischargeSegment(
                    dropPercent = (startPercent - endPercent).coerceAtLeast(0),
                    durationMillis = endTimestamp - startTimestamp
                )
            }
            firstPercent = null
            firstTimestamp = null
            lastPercent = null
            lastTimestamp = null
        }

        ordered.forEach { sample ->
            if (sample.batteryCharging == true) {
                flushSegment()
                return@forEach
            }

            val percent = sample.batteryPercent ?: return@forEach
            if (firstPercent == null) {
                firstPercent = percent
                firstTimestamp = sample.timestampMillis
            }
            lastPercent = percent
            lastTimestamp = sample.timestampMillis
        }
        flushSegment()

        return segments.takeIf { it.isNotEmpty() }
    }

    private fun drainRate(
        dropPercent: Int,
        durationMillis: Long,
        minimumWindowMillis: Long
    ): Float? {
        if (dropPercent <= 0 || durationMillis < minimumWindowMillis) return null
        return dropPercent.toFloat() * 3_600_000f / durationMillis.toFloat()
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
            action = "Vigila la temperatura y considera un perfil menos exigente si la tendencia continúa."
        )
    }

    private fun batteryObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        if (current.batteryCharging == true) return null
        val before = previous.batteryPercent ?: return null
        val after = current.batteryPercent ?: return null
        val drop = before - after
        val crossedLowBatteryThreshold = before > 15 && after <= 15
        val meaningfulDrop = drop >= MEANINGFUL_BATTERY_DROP
        if (!crossedLowBatteryThreshold && !meaningfulDrop) return null

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
        val refreshChangeIsNoise =
            before < 90f || after > before - MEANINGFUL_REFRESH_DROP_HZ
        if (refreshChangeIsNoise) return null

        return SessionCoachMessage(
            signal = SessionCoachSignal.REFRESH,
            priority = SessionCoachPriority.WATCH,
            title = "Caída de refresco observada",
            detail = "El refresco observado pasó de ${before.toInt()} Hz a ${after.toInt()} Hz.",
            action = "Comprueba si el juego, el perfil y la pantalla permiten la frecuencia esperada."
        )
    }

    private fun latencyObservation(
        previous: SessionCoachSnapshot,
        current: SessionCoachSnapshot
    ): SessionCoachMessage? {
        val after = current.latencyMs ?: return null
        val before = previous.latencyMs ?: return null
        val meaningfulJump = after - before >= MEANINGFUL_LATENCY_JUMP_MS

        if (after < HIGH_LATENCY_MS || !meaningfulJump) return null

        return SessionCoachMessage(
            signal = SessionCoachSignal.LATENCY,
            priority = SessionCoachPriority.ACTION,
            title = "Pico de latencia",
            detail = "La medición de conexión pasó de $before ms a $after ms.",
            action = "Revisa señal, congestión o cambios de conectividad antes de atribuirlo al juego."
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
