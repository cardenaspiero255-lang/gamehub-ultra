package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import kotlin.test.Test
import kotlin.test.assertEquals

/** Voice acknowledgement must match the real network optimization outcome. */
class UltraF9CompetitiveVoiceOutcomeTest {
    private val snapshot = VoiceNetworkSnapshot(
        metrics = NetworkMetrics(
            averageLatencyMs = 40.0,
            jitterMs = 5.0,
            packetLossPercent = 0.0,
            spikeCount = 0,
            stability = NetworkStability.GOOD
        ),
        recommendedProfile = NetworkGameProfile.BALANCED,
        metered = false
    )

    @Test
    fun everyCompetitiveResultSpeaksAStatusWithoutClaimingUnappliedOptimization() {
        val expected = mapOf(
            NetworkOptimizationOutcome.APPLIED to
                "Modo competitivo: ACTIVO. Prioridad local de baja latencia aplicada.",
            NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE to
                "Modo competitivo: PREPARADO. Se activará cuando GameHub Ultra esté en primer plano y la pantalla activa.",
            NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED to
                "Modo competitivo: NO ACTIVADO. La red actual no necesita o no admite esta prioridad.",
            NetworkOptimizationOutcome.UNAVAILABLE to
                "Modo competitivo: NO ACTIVADO. Android o la red no permiten aplicar la prioridad local.",
            NetworkOptimizationOutcome.NOT_REQUESTED to
                "Modo competitivo: NO ACTIVADO. No se realizó ningún cambio."
        )
        expected.forEach { (outcome, message) ->
            val report = VoiceActionResult.NetworkReport(
                request = NetworkVoiceRequest.COMPETITIVE,
                snapshot = snapshot,
                optimizationOutcome = outcome
            )
            assertEquals(message, NetworkVoiceResponseText.format(report), "outcome=$outcome")
        }
    }
}
