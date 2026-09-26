package com.cardenaspiero255.gamehubultra.network

data class RouterCapabilityProfile(
    val manufacturer: String,
    val model: String,
    val hardwareQosCapable: Boolean,
    val supportsUpnpDiscovery: Boolean
)

object RouterCapabilityCatalog {
    private val profiles = listOf(
        RouterCapabilityProfile(
            manufacturer = "FiberHome",
            model = "HG5853SF",
            hardwareQosCapable = true,
            supportsUpnpDiscovery = true
        )
    )

    fun find(manufacturer: String?, model: String?): RouterCapabilityProfile? {
        val normalizedModel = model?.trim()?.lowercase().orEmpty()
        val normalizedManufacturer = manufacturer?.trim()?.lowercase()

        if (normalizedModel.isBlank()) return null

        return profiles.firstOrNull { profile ->
            profile.model.lowercase() == normalizedModel &&
                (
                    normalizedManufacturer.isNullOrBlank() ||
                        profile.manufacturer.lowercase() == normalizedManufacturer
                )
        }
    }
}

data class RouterDiscoveryEvidence(
    val manufacturer: String?,
    val model: String?,
    val upnpVisible: Boolean,
    val authorizedQosControlAvailable: Boolean
)

enum class RouterGamingModeStatus {
    UNKNOWN,
    COMPATIBLE_RESTRICTED,
    READY
}

data class RouterGamingModeDecision(
    val status: RouterGamingModeStatus,
    val hardwareQosCapable: Boolean,
    val canAutoConfigureQos: Boolean,
    val upnpVisible: Boolean,
    val manufacturer: String?,
    val model: String?
)

object RouterGamingModePolicy {
    fun evaluate(evidence: RouterDiscoveryEvidence): RouterGamingModeDecision {
        val profile = RouterCapabilityCatalog.find(
            manufacturer = evidence.manufacturer,
            model = evidence.model
        )

        if (profile == null) {
            return RouterGamingModeDecision(
                status = RouterGamingModeStatus.UNKNOWN,
                hardwareQosCapable = false,
                canAutoConfigureQos = false,
                upnpVisible = evidence.upnpVisible,
                manufacturer = evidence.manufacturer,
                model = evidence.model
            )
        }

        val canConfigure =
            profile.hardwareQosCapable && evidence.authorizedQosControlAvailable

        return RouterGamingModeDecision(
            status = if (canConfigure) {
                RouterGamingModeStatus.READY
            } else {
                RouterGamingModeStatus.COMPATIBLE_RESTRICTED
            },
            hardwareQosCapable = profile.hardwareQosCapable,
            canAutoConfigureQos = canConfigure,
            upnpVisible = evidence.upnpVisible,
            manufacturer = profile.manufacturer,
            model = profile.model
        )
    }
}
