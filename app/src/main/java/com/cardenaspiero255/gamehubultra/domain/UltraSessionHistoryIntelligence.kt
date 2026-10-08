package com.cardenaspiero255.gamehubultra.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Small, locally persisted summary of real Session Coach samples.
 * Neither thermal status nor thermal headroom is a temperature in Celsius.
 */
data class UltraRecordedGameSession(
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val measuredSampleCount: Int,
    val typicalRefreshRateHz: Int?,
    val minimumBatteryPercent: Int?,
    val maximumThermalStatus: Int?,
    val playedAtNight: Boolean
)

object UltraSessionHistoryIntelligence {
    private const val MIN_VALID_REFRESH_SAMPLES = 3
    private const val MIN_NIGHT_SESSIONS = 3
    private const val LOW_BATTERY_PERCENT = 20
    private const val TARGET_NIGHT_HZ = 90

    fun fromMeasuredSamples(
        packageName: String,
        startedAtMillis: Long,
        endedAtMillis: Long,
        samples: List<SessionCoachSnapshot>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): UltraRecordedGameSession? {
        if (packageName.isBlank() || startedAtMillis < 0L || endedAtMillis < startedAtMillis) {
            return null
        }
        val valid = samples.filter {
            it.timestampMillis in startedAtMillis..endedAtMillis
        }
        if (valid.isEmpty()) return null
        val refreshes = valid.mapNotNull {
            it.refreshRateHz?.takeIf { hz ->
                hz.isFinite() && hz in 30f..240f
            }?.roundToInt()
        }.sorted()
        val refresh = refreshes.takeIf { it.size >= MIN_VALID_REFRESH_SAMPLES }
            ?.let { it[it.size / 2] }
        val hour = Instant.ofEpochMilli(startedAtMillis).atZone(zoneId).hour
        return UltraRecordedGameSession(
            packageName = packageName.trim(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            measuredSampleCount = valid.size,
            typicalRefreshRateHz = refresh,
            minimumBatteryPercent = valid.mapNotNull {
                it.batteryPercent?.takeIf { value -> value in 0..100 }
            }.minOrNull(),
            maximumThermalStatus = valid.mapNotNull {
                it.thermalStatus?.takeIf { value -> value in 0..7 }
            }.maxOrNull(),
            playedAtNight = hour >= 21 || hour < 6
        )
    }

    fun lastSessionSummary(
        history: List<UltraRecordedGameSession>,
        packageName: String
    ): String? {
        val recent = history.filter { it.packageName == packageName && it.measuredSampleCount > 0 }
            .maxByOrNull { it.endedAtMillis } ?: return null
        val observations = buildList {
            recent.typicalRefreshRateHz?.let { add("refresco observado de $it Hz") }
            recent.minimumBatteryPercent?.let { add("batería mínima del $it %") }
            recent.maximumThermalStatus?.let { add("estado térmico $it (nivel de Android; no equivale a temperatura medida)") }
        }
        return if (observations.isEmpty()) {
            "De la última sesión de $packageName tengo muestras, pero no métricas verificables."
        } else {
            "En tu última sesión registrada de $packageName: " +
                observations.joinToString(", ") + "."
        }
    }

    /**
     * Proposes only; creation and applying a profile remain explicit user actions.
     */
    fun proposeNightProfile(
        history: List<UltraRecordedGameSession>,
        packageName: String
    ): String? {
        val qualifying = history.filter { session ->
            session.packageName == packageName &&
                session.playedAtNight &&
                session.measuredSampleCount >= MIN_VALID_REFRESH_SAMPLES &&
                (session.typicalRefreshRateHz?.let { it in (TARGET_NIGHT_HZ - 2)..(TARGET_NIGHT_HZ + 2) } == true) &&
                session.minimumBatteryPercent != null &&
                session.minimumBatteryPercent <= LOW_BATTERY_PERCENT
        }.distinctBy { it.startedAtMillis }
        if (qualifying.size < MIN_NIGHT_SESSIONS) return null
        return "He observado ${qualifying.size} sesiones distintas de $packageName " +
            "de noche, cerca de 90 Hz y con batería de 20 % o menos. " +
            "¿Quieres que te proponga un perfil Noche? No cambiaré nada sin tu aprobación."
    }

    fun response(
        query: String,
        packageName: String?,
        history: List<UltraRecordedGameSession>
    ): String? {
        val game = packageName ?: return null
        val clean = java.text.Normalizer.normalize(
            query.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD
        ).replace(Regex("""\p{M}+"""), "")
        if (listOf("ultima sesion", "ultima partida", "la ultima vez que jug").any(clean::contains)) {
            return lastSessionSummary(history, game)
                ?: "No tengo una sesión anterior medida de este juego."
        }
        val profileQuestion = listOf(
            "perfil noche", "perfil nocturno", "sugerencia de perfil"
        ).any(clean::contains) || (
            clean.contains("habitos") &&
                listOf("juego", "partida", "sesion", "perfil", "gaming").any(clean::contains)
            )
        if (profileQuestion) {
            return proposeNightProfile(history, game)
                ?: "Todavía no tengo suficientes sesiones medidas para proponerte un perfil Noche."
        }
        return null
    }
}
