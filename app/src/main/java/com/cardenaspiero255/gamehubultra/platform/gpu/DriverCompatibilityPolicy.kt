package com.cardenaspiero255.gamehubultra.platform.gpu

/**
 * CAR-54: driver compatibility *planning*, not a privileged driver switch.
 * Generic Android must use the system driver until a separately verified
 * game-specific driver selection backend exists.
 */
enum class DriverLaunchRoute { SYSTEM, ALTERNATE_READY }

enum class DriverFallbackReason {
    SYSTEM_PREFERENCE,
    INVALID_PACKAGE,
    INVALID_DRIVER_ID,
    SELECTION_UNVERIFIED,
    DRIVER_UNAVAILABLE,
    GAME_UNSUPPORTED,
    AMBIGUOUS_DRIVER,
    VERIFIED_ALTERNATE_READY
}

/** Package-scoped user preference; null means the original system driver. */
data class GameDriverPreference(
    val packageName: String,
    val preferredDriverId: String? = null
)

/**
 * Capability facts from a trusted backend, not from a model string, GLES version,
 * GPU vendor or Vulkan hardware flag. Exact per-game support is mandatory.
 */
data class VerifiedAlternateDriver(
    val driverId: String,
    val allowedPackages: Set<String>,
    val installed: Boolean,
    val selectionBackendVerified: Boolean,
    val prelaunchSelectionConfirmed: Boolean
)

/** This decision is only a plan; it NEVER changes an installed graphics driver. */
data class DriverLaunchDecision(
    val route: DriverLaunchRoute,
    val reason: DriverFallbackReason,
    val driverId: String? = null
) {
    val activeDriverChanged: Boolean get() = false
}

object DriverCompatibilityPolicy {
    private val packagePattern = Regex("[A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*)+")
    private val driverPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")

    fun plan(
        preference: GameDriverPreference,
        verifiedSelectionApi: Boolean,
        availableDrivers: List<VerifiedAlternateDriver>
    ): DriverLaunchDecision {
        // Keep the system fallback explicit: this function makes no recursive
        // calls and does not attempt to install or switch graphics drivers.
        if (!packagePattern.matches(preference.packageName)) {
            return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.INVALID_PACKAGE)
        }
        val requested = preference.preferredDriverId
            ?: return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.SYSTEM_PREFERENCE)
        if (!driverPattern.matches(requested)) {
            return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.INVALID_DRIVER_ID)
        }
        if (!verifiedSelectionApi) {
            return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.SELECTION_UNVERIFIED)
        }
        val matching = availableDrivers.filter { it.driverId == requested }
        if (matching.size > 1) return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.AMBIGUOUS_DRIVER)
        val selected = matching.singleOrNull()
            ?: return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.DRIVER_UNAVAILABLE)
        if (!selected.installed) return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.DRIVER_UNAVAILABLE)
        if (preference.packageName !in selected.allowedPackages) {
            return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.GAME_UNSUPPORTED)
        }
        if (!selected.selectionBackendVerified || !selected.prelaunchSelectionConfirmed) {
            return DriverLaunchDecision(DriverLaunchRoute.SYSTEM, DriverFallbackReason.SELECTION_UNVERIFIED)
        }
        return DriverLaunchDecision(
            route = DriverLaunchRoute.ALTERNATE_READY,
            reason = DriverFallbackReason.VERIFIED_ALTERNATE_READY,
            driverId = selected.driverId
        )
    }
}
