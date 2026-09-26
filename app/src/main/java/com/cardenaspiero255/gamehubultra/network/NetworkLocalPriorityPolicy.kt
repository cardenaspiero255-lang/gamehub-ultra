package com.cardenaspiero255.gamehubultra.network

enum class NetworkPriorityAction {
    LOW_LATENCY_WIFI,
    HIGH_PERFORMANCE_WIFI,
    RELEASE_WIFI_LOCK,
    UNAVAILABLE
}

object NetworkLocalPriorityPolicy {
    fun actionFor(
        profile: NetworkGameProfile,
        transport: String?,
        connected: Boolean,
        validated: Boolean,
        sdkInt: Int
    ): NetworkPriorityAction {
        if (profile != NetworkGameProfile.COMPETITIVE) {
            return NetworkPriorityAction.RELEASE_WIFI_LOCK
        }
        if (!connected || !validated || transport != "Wi-Fi") {
            return NetworkPriorityAction.UNAVAILABLE
        }
        return if (sdkInt >= 29) {
            NetworkPriorityAction.LOW_LATENCY_WIFI
        } else {
            NetworkPriorityAction.HIGH_PERFORMANCE_WIFI
        }
    }
}
