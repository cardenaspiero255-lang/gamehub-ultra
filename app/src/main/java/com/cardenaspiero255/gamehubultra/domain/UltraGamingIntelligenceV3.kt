package com.cardenaspiero255.gamehubultra.domain

import java.text.Normalizer
import java.util.Locale

/**
 * An active, game-scoped window of Android Session Coach observations.
 * Samples are diagnostics, NOT per-game rendered frames or server-side ping.
 */
data class UltraGamingMeasurementWindow(
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val samples: List<SessionCoachSnapshot>
)

/**
 * EVO Frontier V3 · Phase 6.
 *
 * Produces read-only advice from recent real telemetry, never attempts profile changes,
 * never converts display refresh to game FPS and never invents Celsius/ping measurements.
 */
object UltraGamingIntelligenceV3 {
    private const val FRESHNESS_MILLIS = 120_000L
    private const val MIN_SAMPLES = 3

    fun answer(
        query: String,
        selectedGamePackage: String?,
        window: UltraGamingMeasurementWindow?,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val clean = Normalizer.normalize(
            query.lowercase(Locale.ROOT), Normalizer.Form.NFD
        ).replace(Regex("""\p{M}+"""), "")
        if (!isCurrentGameRequest(clean)) return null

        if (selectedGamePackage.isNullOrBlank()) {
            return "Selecciona un juego para analizar sus mediciones reales."
        }
        if (window == null || window.packageName != selectedGamePackage ||
            window.endedAtMillis != null || window.startedAtMillis !in 0L..nowMillis
        ) {
            return "No tengo una sesión activa medida para el juego seleccionado."
        }
        // Explicit named games cannot silently fall back to the currently selected
        // game's private performance readings.
        val mentionedPackages = Regex(
            """\b[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){1,}\b"""
        ).findAll(clean).map { it.value }.toList()
        if (mentionedPackages.any { it != selectedGamePackage.lowercase(Locale.ROOT) }) {
            return "No tengo una sesión activa medida para el juego indicado."
        }

        val valid = window.samples.asSequence()
            .filter { sample ->
                sample.timestampMillis in window.startedAtMillis..nowMillis &&
                    nowMillis - sample.timestampMillis <= FRESHNESS_MILLIS
            }
            .distinctBy(SessionCoachSnapshot::timestampMillis)
            .sortedBy(SessionCoachSnapshot::timestampMillis)
            .toList()
        if (valid.size < MIN_SAMPLES) {
            return "Muestras recientes insuficientes: ${valid.size}/$MIN_SAMPLES. " +
                "No recomendaré ajustes sin mediciones suficientes."
        }

        val refresh = valid.mapNotNull { sample ->
            sample.refreshRateHz?.takeIf { it.isFinite() && it in 30f..240f }
        }
        val battery = valid.mapNotNull { sample ->
            sample.batteryPercent?.takeIf { it in 0..100 }
        }
        val thermal = valid.mapNotNull { sample ->
            sample.thermalStatus?.takeIf { it in 0..7 }
        }
        val network = valid.mapNotNull { sample ->
            sample.latencyMs?.takeIf { it in 0L..10_000L }
        }

        val sustainedThermal = thermal.count { it >= 3 } >= 2
        val sustainedLowBattery = valid.count { sample ->
            sample.batteryPercent?.let { it in 0..20 } == true &&
                sample.batteryCharging == false
        } >= 2
        val sustainedNetworkLatency = network.count { it >= 200L } >= MIN_SAMPLES

        val suggestions = buildList {
            if (sustainedThermal) {
                add("presión térmica repetida: considera un perfil equilibrado")
            }
            if (sustainedLowBattery) {
                add("batería baja repetida: considera reducir la exigencia gráfica")
            }
            if (sustainedNetworkLatency) {
                add("latencia alta repetida: revisa tu conexión Wi-Fi antes de jugar competitivo")
            }
        }

        return buildString {
            append("Análisis local de ${valid.size} muestras recientes de tu juego. ")
            append("FPS del juego no medidos; frame pacing no verificable sin frame timings del juego. ")
            if (refresh.size >= MIN_SAMPLES) {
                val ordered = refresh.sorted()
                append("Refresco de pantalla observado: ${ordered[ordered.size / 2].toInt()} Hz ")
                append("(Hz de pantalla, no FPS del juego). ")
            } else {
                append("Sin refresco de pantalla suficientemente medido. ")
            }
            if (battery.isNotEmpty()) {
                append("Batería observada: ${battery.last()} %. ")
            } else {
                append("Sin nivel de batería medido. ")
            }
            if (thermal.isNotEmpty()) {
                append("Estado térmico Android máximo: ${thermal.max()} ")
                append("(categoría del sistema; no son grados Celsius). ")
            } else {
                append("Sin estado térmico medido. ")
            }
            if (network.isNotEmpty()) {
                append("Latencia de diagnóstico: ${network.sorted()[network.size / 2]} ms ")
                append("(no es ping al servidor del juego). ")
            } else {
                append("Conectividad: sin latencia medida. ")
            }
            if (suggestions.isNotEmpty()) {
                append("Recomendaciones con evidencia: ${suggestions.joinToString("; ")}; ")
                append("sin activar ajustes sin tu aprobación.")
            } else {
                append("Sin recomendaciones de cambio respaldadas por alertas repetidas.")
            }
        }
    }

    private fun isCurrentGameRequest(clean: String): Boolean {
        // Require ownership, not merely a mention of FPS, a phone or another person.
        if (Regex("""\b(?:ultima sesion|ultima partida|ultima vez)\b""").containsMatchIn(clean)) {
            return false // answered by the completed-session history engine
        }
        val ownGame = Regex(
            """\b(?:mi|mis)\s+(?:juego|partida|gaming|fps|ping|latencia|bateria|temperatura|rendimiento)\b"""
        ).containsMatchIn(clean)
        val activePlay = clean.contains("estoy jugando") &&
            Regex("""\b(?:juego|rendimiento|fps|pacing|latencia)\b""").containsMatchIn(clean)
        return ownGame || activePlay
    }
}
