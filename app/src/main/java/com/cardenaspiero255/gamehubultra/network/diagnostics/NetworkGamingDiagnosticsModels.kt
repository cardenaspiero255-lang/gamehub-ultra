package com.cardenaspiero255.gamehubultra.network.diagnostics

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics

/**
 * Muestra de sesión con procedencia explícita.
 *
 * Un valor de latencia o pérdida solo puede usarse como medición cuando su flag
 * correspondiente confirma que provino de una medición legítima.
 */
data class NetworkGamingDiagnosticSample(
    val timestampMs: Long,
    val networkHandle: Long?,
    val transport: String?,
    val connected: Boolean,
    val validated: Boolean,
    val metered: Boolean,
    val latencyMs: Long?,
    val latencyMeasured: Boolean,
    val packetLossPercent: Double?,
    val packetLossMeasured: Boolean
)

data class NetworkTransportChange(
    val timestampMs: Long,
    val fromTransport: String,
    val toTransport: String
)

data class NetworkGamingDiagnostics(
    val metrics: NetworkMetrics,
    val latencyHistoryMs: List<Long>,
    val packetLossMeasured: Boolean,
    val packetLossSampleCount: Int,
    val transportChanges: List<NetworkTransportChange>,
    val networkHandleChangeCount: Int,
    val recommendedProfile: NetworkGameProfile,
    val competitiveRecommended: Boolean,
    val networkControlApplied: Boolean = false,
    val evidence: List<String>,
    val explanation: String
)
