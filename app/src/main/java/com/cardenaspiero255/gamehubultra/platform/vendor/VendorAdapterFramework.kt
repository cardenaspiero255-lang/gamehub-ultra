package com.cardenaspiero255.gamehubultra.platform.vendor

import java.util.Locale

/** CAR-55: SoC identity is advisory, never a vendor privilege token. */
enum class VendorSocFamily { QUALCOMM, MEDIATEK, EXYNOS, GENERIC }
enum class VendorAdapterRoute { GENERIC_ANDROID, VERIFIED_READ_ONLY }
enum class VendorAdapterReason {
    SOC_UNVERIFIED,
    NO_VERIFIED_ADAPTER,
    AMBIGUOUS_ADAPTER,
    INVALID_ADAPTER,
    NO_VERIFIED_READOUT,
    READ_ONLY_VERIFIED
}

/** Capabilities here are *observations*, not mechanisms for controlling games. */
enum class VendorReadOnlyCapability { THERMAL_STATUS, GPU_RENDERER, SUSTAINED_PERFORMANCE }

data class VendorHardwareObservation(
    val socManufacturer: String?,
    val socModel: String?,
    val deviceManufacturer: String? = null,
    val gpuRenderer: String? = null
)

data class VendorReadOnlySnapshot(
    val publicAndroidEvidence: Boolean,
    val capabilities: Set<VendorReadOnlyCapability>
)

/**
 * Optional adapter boundary. No vendor API is linked or invoked by default.
 * Implementations must independently prove their actual public readout source.
 */
interface VendorOptionalAdapter {
    val family: VendorSocFamily
    val adapterId: String
    fun readOnlyCapabilities(): VendorReadOnlySnapshot?
}

data class VendorAdapterDecision(
    val family: VendorSocFamily,
    val route: VendorAdapterRoute,
    val reason: VendorAdapterReason,
    val adapterId: String? = null,
    val capabilities: Set<VendorReadOnlyCapability> = emptySet()
) {
    // Third-party apps cannot select another app's GPU driver or clock speeds.
    val canControlCpuFrequency: Boolean get() = false
    val canSwitchExternalGameDrivers: Boolean get() = false
}

object VendorAdapterFramework {
    private val adapterIdPattern = Regex("[a-z][a-z0-9-]{2,47}")

    /**
     * Android's SoC manufacturer field (API 31+) is the sole chipset-family
     * discriminator. Phone brand and GPU renderer alone are never sufficient.
     */
    fun detectFamily(observation: VendorHardwareObservation): VendorSocFamily {
        val maker = observation.socManufacturer
            ?.trim()?.lowercase(Locale.ROOT) ?: return VendorSocFamily.GENERIC
        val model = observation.socModel?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            maker in setOf("qualcomm", "qti", "qualcomm technologies, inc.") ->
                VendorSocFamily.QUALCOMM
            maker in setOf("mediatek", "mtk") -> VendorSocFamily.MEDIATEK
            maker in setOf("samsung", "samsung electronics") &&
                model.startsWith("exynos ") -> VendorSocFamily.EXYNOS
            else -> VendorSocFamily.GENERIC
        }
    }

    fun evaluate(
        observation: VendorHardwareObservation,
        adapters: List<VendorOptionalAdapter>
    ): VendorAdapterDecision {
        val family = detectFamily(observation)

        fun generic(reason: VendorAdapterReason) = VendorAdapterDecision(
            family, VendorAdapterRoute.GENERIC_ANDROID, reason
        )

        if (family == VendorSocFamily.GENERIC) {
            return generic(VendorAdapterReason.SOC_UNVERIFIED)
        }
        val eligible = adapters.filter { it.family == family }
        if (eligible.isEmpty()) return generic(VendorAdapterReason.NO_VERIFIED_ADAPTER)
        if (eligible.size != 1) return generic(VendorAdapterReason.AMBIGUOUS_ADAPTER)

        val adapter = eligible.single()
        if (!adapterIdPattern.matches(adapter.adapterId)) {
            return generic(VendorAdapterReason.INVALID_ADAPTER)
        }
        val snapshot = runCatching { adapter.readOnlyCapabilities() }.getOrNull()
        if (snapshot == null || !snapshot.publicAndroidEvidence ||
            snapshot.capabilities.isEmpty()
        ) {
            return generic(VendorAdapterReason.NO_VERIFIED_READOUT)
        }
        return VendorAdapterDecision(
            family = family,
            route = VendorAdapterRoute.VERIFIED_READ_ONLY,
            reason = VendorAdapterReason.READ_ONLY_VERIFIED,
            adapterId = adapter.adapterId,
            capabilities = snapshot.capabilities
        )
    }
}
