package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics

enum class NetworkVoiceRequest {
    OPTIMIZE,
    STATUS,
    PACKET_LOSS
}

data class VoiceNetworkSnapshot(
    val metrics: NetworkMetrics,
    val recommendedProfile: NetworkGameProfile,
    val metered: Boolean
)
