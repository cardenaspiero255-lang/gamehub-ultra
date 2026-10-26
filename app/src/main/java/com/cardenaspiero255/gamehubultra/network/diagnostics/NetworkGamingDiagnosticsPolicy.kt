package com.cardenaspiero255.gamehubultra.network.diagnostics

/**
 * Política calibrable para CAR-51 Network Gaming Diagnostics 2.0.
 *
 * Los valores son heurísticas iniciales conservadoras para diagnóstico de juego;
 * no representan garantías universales de un ISP, router, radio o servidor.
 */
data class NetworkGamingDiagnosticsPolicy(
    val historyWindowMs: Long = 120_000L,
    val minLatencySamples: Int = 3,
    val minPacketLossSamples: Int = 1,
    val maxLatencyMs: Long = 10_000L,
    val averageGoodMs: Double = 80.0,
    val averageFairMs: Double = 120.0,
    val averagePoorMs: Double = 180.0,
    val jitterGoodMs: Double = 12.0,
    val jitterFairMs: Double = 30.0,
    val jitterPoorMs: Double = 80.0,
    val packetLossGoodPercent: Double = 0.5,
    val packetLossFairPercent: Double = 2.0,
    val packetLossPoorPercent: Double = 5.0,
    val spikeAbsoluteDeltaMs: Double = 50.0,
    val spikeRelativeIncreaseRatio: Double = 0.5,
    val fairSpikeCount: Int = 1,
    val poorSpikeCount: Int = 2,
    val competitiveLatencyMaxMs: Double = 80.0,
    val competitivePacketLossMaxPercent: Double = 1.0
) {
    init {
        require(historyWindowMs >= 1_000L)
        require(minLatencySamples >= 2)
        require(minPacketLossSamples >= 1)
        require(maxLatencyMs >= 100L)

        require(averageGoodMs.isFinite() && averageGoodMs > 0.0)
        require(averageFairMs.isFinite() && averageFairMs > averageGoodMs)
        require(averagePoorMs.isFinite() && averagePoorMs > averageFairMs)

        require(jitterGoodMs.isFinite() && jitterGoodMs >= 0.0)
        require(jitterFairMs.isFinite() && jitterFairMs > jitterGoodMs)
        require(jitterPoorMs.isFinite() && jitterPoorMs > jitterFairMs)

        require(packetLossGoodPercent.isFinite() && packetLossGoodPercent in 0.0..100.0)
        require(
            packetLossFairPercent.isFinite() &&
                packetLossFairPercent in packetLossGoodPercent..100.0
        )
        require(
            packetLossPoorPercent.isFinite() &&
                packetLossPoorPercent in packetLossFairPercent..100.0
        )

        require(spikeAbsoluteDeltaMs.isFinite() && spikeAbsoluteDeltaMs > 0.0)
        require(
            spikeRelativeIncreaseRatio.isFinite() &&
                spikeRelativeIncreaseRatio >= 0.0
        )
        require(fairSpikeCount >= 1)
        require(poorSpikeCount >= fairSpikeCount)

        require(
            competitiveLatencyMaxMs.isFinite() &&
                competitiveLatencyMaxMs > 0.0
        )
        require(
            competitivePacketLossMaxPercent.isFinite() &&
                competitivePacketLossMaxPercent in 0.0..100.0
        )
    }
}
