package com.cardenaspiero255.gamehubultra.network.diagnostics

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import kotlin.math.abs
import kotlin.math.max

/**
 * CAR-51 — Network Gaming Diagnostics 2.0.
 *
 * Analiza observaciones y mediciones legítimas de red sin atribuirse control del
 * stack de red. Las salidas son diagnóstico y recomendación, nunca una acción aplicada.
 */
class NetworkGamingDiagnosticsEngine(
    private val policy: NetworkGamingDiagnosticsPolicy =
        NetworkGamingDiagnosticsPolicy()
) {
    fun analyze(
        samples: List<NetworkGamingDiagnosticSample>
    ): NetworkGamingDiagnostics {
        val validTimeline = samples
            .asSequence()
            .filter { it.timestampMs >= 0L }
            .sortedBy(NetworkGamingDiagnosticSample::timestampMs)
            .toList()

        val now = validTimeline.lastOrNull()?.timestampMs
        val recent = if (now == null) {
            emptyList()
        } else {
            val windowStart = (now - policy.historyWindowMs).coerceAtLeast(0L)
            validTimeline.filter { it.timestampMs >= windowStart }
        }

        // Current connectivity wins over historical samples. A prior successful
        // probe is never evidence that a disconnected or new network is healthy.
        val latest = recent.lastOrNull()
        val connected = if (latest?.connected == true && latest.validated) {
            recent.asReversed()
                .takeWhile {
                    it.connected && it.validated &&
                        it.networkHandle == latest.networkHandle
                }
                .asReversed()
        } else {
            emptyList()
        }
        // Keep transitions visible, but do not combine measurements across networks.
        val connectionTimeline = recent.filter { it.connected && it.validated }
        val latencyHistory = connected.mapNotNull { sample ->
            sample.latencyMs?.takeIf { latency ->
                sample.latencyMeasured &&
                    latency > 0L &&
                    latency <= policy.maxLatencyMs
            }
        }
        val packetLossValues = connected.mapNotNull { sample ->
            sample.packetLossPercent?.takeIf { loss ->
                sample.packetLossMeasured &&
                    loss.isFinite() &&
                    loss in 0.0..100.0
            }
        }

        val averageLatency = latencyHistory
            .takeIf { it.isNotEmpty() }
            ?.map(Long::toDouble)
            ?.average()
        val jitter = latencyHistory
            .map(Long::toDouble)
            .zipWithNext { before, after -> abs(after - before) }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val averageLoss = packetLossValues
            .takeIf { it.isNotEmpty() }
            ?.average()

        val spikeCount = countSpikes(latencyHistory)
        val transportChanges = transportChanges(connectionTimeline)
        val networkHandleChangeCount = connectionTimeline
            .zipWithNext()
            .count { (before, after) ->
                before.networkHandle != null &&
                    after.networkHandle != null &&
                    before.networkHandle != after.networkHandle
            }

        val stability = classify(
            connected = connected,
            latencySampleCount = latencyHistory.size,
            packetLossSampleCount = packetLossValues.size,
            averageLatencyMs = averageLatency,
            jitterMs = jitter,
            packetLossPercent = averageLoss,
            spikeCount = spikeCount
        )
        val metrics = NetworkMetrics(
            averageLatencyMs = averageLatency,
            jitterMs = jitter,
            packetLossPercent = averageLoss,
            spikeCount = spikeCount,
            stability = stability
        )

        val latestConnected = connected.lastOrNull()
        val recommendedProfile = recommend(
            metrics = metrics,
            metered = latestConnected?.metered == true,
            transportChanged = transportChanges.isNotEmpty(),
            networkChanged = networkHandleChangeCount > 0
        )
        val packetLossMeasured = packetLossValues.isNotEmpty()

        return NetworkGamingDiagnostics(
            metrics = metrics,
            latencyHistoryMs = latencyHistory,
            packetLossMeasured = packetLossMeasured,
            packetLossSampleCount = packetLossValues.size,
            transportChanges = transportChanges,
            networkHandleChangeCount = networkHandleChangeCount,
            recommendedProfile = recommendedProfile,
            competitiveRecommended =
                recommendedProfile == NetworkGameProfile.COMPETITIVE,
            networkControlApplied = false,
            evidence = evidence(
                connected = connected,
                latencyHistory = latencyHistory,
                packetLossMeasured = packetLossMeasured,
                transportChanges = transportChanges,
                networkHandleChangeCount = networkHandleChangeCount,
                spikeCount = spikeCount
            ),
            explanation = explanation(
                metrics = metrics,
                packetLossMeasured = packetLossMeasured,
                transportChanges = transportChanges,
                networkHandleChangeCount = networkHandleChangeCount
            )
        )
    }

    private fun classify(
        connected: List<NetworkGamingDiagnosticSample>,
        latencySampleCount: Int,
        packetLossSampleCount: Int,
        averageLatencyMs: Double?,
        jitterMs: Double?,
        packetLossPercent: Double?,
        spikeCount: Int
    ): NetworkStability {
        if (connected.isEmpty()) return NetworkStability.OFFLINE
        if (
            latencySampleCount < policy.minLatencySamples &&
            packetLossSampleCount < policy.minPacketLossSamples
        ) {
            return NetworkStability.UNMEASURED
        }

        return when {
            packetLossPercent != null &&
                packetLossPercent >= policy.packetLossPoorPercent ->
                NetworkStability.POOR
            jitterMs != null && jitterMs >= policy.jitterPoorMs ->
                NetworkStability.POOR
            averageLatencyMs != null &&
                averageLatencyMs >= policy.averagePoorMs ->
                NetworkStability.POOR
            spikeCount >= policy.poorSpikeCount ->
                NetworkStability.POOR

            packetLossPercent != null &&
                packetLossPercent >= policy.packetLossFairPercent ->
                NetworkStability.FAIR
            jitterMs != null && jitterMs >= policy.jitterFairMs ->
                NetworkStability.FAIR
            averageLatencyMs != null &&
                averageLatencyMs >= policy.averageFairMs ->
                NetworkStability.FAIR
            spikeCount >= policy.fairSpikeCount ->
                NetworkStability.FAIR

            packetLossPercent != null &&
                packetLossPercent >= policy.packetLossGoodPercent ->
                NetworkStability.GOOD
            jitterMs != null && jitterMs >= policy.jitterGoodMs ->
                NetworkStability.GOOD
            averageLatencyMs != null &&
                averageLatencyMs >= policy.averageGoodMs ->
                NetworkStability.GOOD
            else -> NetworkStability.EXCELLENT
        }
    }

    private fun recommend(
        metrics: NetworkMetrics,
        metered: Boolean,
        transportChanged: Boolean,
        networkChanged: Boolean
    ): NetworkGameProfile {
        if (
            metrics.stability == NetworkStability.OFFLINE ||
            metrics.stability == NetworkStability.UNMEASURED
        ) {
            return NetworkGameProfile.BALANCED
        }
        if (metered) return NetworkGameProfile.DATA_SAVER
        if (
            transportChanged ||
            networkChanged ||
            metrics.spikeCount > 0
        ) {
            return NetworkGameProfile.BALANCED
        }
        if (
            metrics.stability == NetworkStability.EXCELLENT ||
            metrics.stability == NetworkStability.GOOD
        ) {
            val latency = metrics.averageLatencyMs
            val loss = metrics.packetLossPercent
            if (
                latency != null &&
                latency <= policy.competitiveLatencyMaxMs &&
                (loss != null && loss <= policy.competitivePacketLossMaxPercent)
            ) {
                return NetworkGameProfile.COMPETITIVE
            }
        }
        return NetworkGameProfile.BALANCED
    }

    private fun countSpikes(latencies: List<Long>): Int {
        if (latencies.size < policy.minLatencySamples) return 0
        val values = latencies.map(Long::toDouble)
        val baseline = median(values)
        val threshold = baseline + max(
            policy.spikeAbsoluteDeltaMs,
            baseline * policy.spikeRelativeIncreaseRatio
        )
        return values.count { it > threshold }
    }

    private fun transportChanges(
        connected: List<NetworkGamingDiagnosticSample>
    ): List<NetworkTransportChange> =
        connected.zipWithNext().mapNotNull { (before, after) ->
            val from = before.transport
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val to = after.transport
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (from == null || to == null || from == to) {
                null
            } else {
                NetworkTransportChange(
                    timestampMs = after.timestampMs,
                    fromTransport = from,
                    toTransport = to
                )
            }
        }

    private fun evidence(
        connected: List<NetworkGamingDiagnosticSample>,
        latencyHistory: List<Long>,
        packetLossMeasured: Boolean,
        transportChanges: List<NetworkTransportChange>,
        networkHandleChangeCount: Int,
        spikeCount: Int
    ): List<String> = buildList {
        if (connected.isEmpty()) {
            add("No hay una red conectada y validada en la ventana analizada.")
        }
        if (latencyHistory.isEmpty()) {
            add("Latencia no medida; CAR-51 no inventa valores desde el tipo de red.")
        } else {
            add(
                "Historial de latencia medido: " +
                    latencyHistory.joinToString(separator = ", ", postfix = " ms")
            )
        }
        if (!packetLossMeasured) {
            add("Pérdida de paquetes no medida; el diagnóstico permanece no verificable.")
        }
        if (transportChanges.isNotEmpty()) {
            add("Cambios de transporte detectados: " + transportChanges.size + ".")
        }
        if (networkHandleChangeCount > 0) {
            add("Cambios de red detectados: " + networkHandleChangeCount + ".")
        }
        if (spikeCount > 0) {
            add("Picos grandes de latencia detectados: " + spikeCount + ".")
        }
        add("CAR-51 solo recomienda; no afirma haber controlado el stack de red.")
    }

    private fun explanation(
        metrics: NetworkMetrics,
        packetLossMeasured: Boolean,
        transportChanges: List<NetworkTransportChange>,
        networkHandleChangeCount: Int
    ): String = buildString {
        append("Diagnóstico de red: ")
        append(metrics.stability.name)
        metrics.averageLatencyMs?.let {
            append(", latencia media ")
            append("%.1f".format(java.util.Locale.US, it))
            append(" ms")
        }
        metrics.jitterMs?.let {
            append(", jitter ")
            append("%.1f".format(java.util.Locale.US, it))
            append(" ms")
        }
        if (packetLossMeasured) {
            metrics.packetLossPercent?.let {
                append(", pérdida ")
                append("%.2f".format(java.util.Locale.US, it))
                append("%")
            }
        } else {
            append(", pérdida de paquetes no medida")
        }
        if (transportChanges.isNotEmpty() || networkHandleChangeCount > 0) {
            append(", hubo cambios de conectividad durante la sesión")
        }
        append(".")
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
