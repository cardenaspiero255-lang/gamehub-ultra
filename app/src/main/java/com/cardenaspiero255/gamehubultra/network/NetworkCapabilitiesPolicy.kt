package com.cardenaspiero255.gamehubultra.network

data class NetworkOptimizationCapabilities(
    val canUseVpnTunnel: Boolean,
    val canPrioritizeOtherApps: Boolean,
    val canChangeIspRouting: Boolean,
    val canExceedIspBandwidth: Boolean,
    val canUseRouterQos: Boolean
)

object NetworkCapabilityPolicy {
    fun evaluate(
        vpnConsentGranted: Boolean,
        routerQosIntegrationAvailable: Boolean
    ): NetworkOptimizationCapabilities = NetworkOptimizationCapabilities(
        canUseVpnTunnel = vpnConsentGranted,
        canPrioritizeOtherApps = false,
        canChangeIspRouting = false,
        canExceedIspBandwidth = false,
        canUseRouterQos = routerQosIntegrationAvailable
    )
}
