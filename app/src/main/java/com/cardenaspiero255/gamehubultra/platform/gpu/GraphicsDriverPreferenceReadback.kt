package com.cardenaspiero255.gamehubultra.platform.gpu

/**
 * Android's ANGLE settings represent OpenGL ES *preferences*, not proof of the
 * graphics driver actually loaded by a game. Turnip is not a public Android
 * driver choice and is intentionally absent from this allow-list.
 */
enum class OpenGlDriverPreference(val storedValue: String) {
    SYSTEM("default"),
    ANGLE("angle"),
    NATIVE("native");

    companion object {
        fun fromStoredValue(value: String?): OpenGlDriverPreference =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

enum class DriverPreferenceStatus {
    SYSTEM_DEFAULT,
    SYSTEM_SETTING_CONFIRMED,
    MANUAL_CONFIGURATION_REQUIRED,
    UNVERIFIABLE
}

data class DriverPreferenceReadback(
    val status: DriverPreferenceStatus,
    val readback: OpenGlDriverPreference? = null
) {
    val matchesPreference: Boolean get() =
        status == DriverPreferenceStatus.SYSTEM_DEFAULT ||
            status == DriverPreferenceStatus.SYSTEM_SETTING_CONFIRMED

    // Android's configuration readback is not a measured renderer or loaded library.
    val driverLoadedVerified: Boolean get() = false
}

object GraphicsDriverPreferenceReadback {
    private val packagePattern =
        Regex("[A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*)+")

    fun validPackage(packageName: String): Boolean =
        packagePattern.matches(packageName)

    /**
     * Reads a *pair* of Android Global Settings values. Both comma-separated
     * arrays are jointly validated; never trust a partial/duplicated mapping.
     * A missing mapping means Android retains its own default for this package.
     */
    fun inspect(
        packageName: String,
        requested: OpenGlDriverPreference,
        packagesValue: String?,
        driversValue: String?
    ): DriverPreferenceReadback {
        if (!validPackage(packageName)) {
            return DriverPreferenceReadback(DriverPreferenceStatus.UNVERIFIABLE)
        }
        // Android treats jointly empty settings exactly like no per-app overrides.
        // Whitespace-only tokens are NOT empty and remain untrusted.
        if ((packagesValue == null && driversValue == null) ||
            (packagesValue == "" && driversValue == "")) {
            return if (requested == OpenGlDriverPreference.SYSTEM) {
                DriverPreferenceReadback(DriverPreferenceStatus.SYSTEM_DEFAULT, OpenGlDriverPreference.SYSTEM)
            } else {
                DriverPreferenceReadback(DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED)
            }
        }
        if (packagesValue.isNullOrBlank() || driversValue.isNullOrBlank()) {
            return DriverPreferenceReadback(DriverPreferenceStatus.UNVERIFIABLE)
        }
        // GraphicsEnvironment compares raw comma-delimited tokens literally.
        // Trimming would forge a confirmation Android did not actually select.
        val packages = packagesValue.split(',')
        val drivers = driversValue.split(',')
        if (packages.size != drivers.size ||
            packages.any { !validPackage(it) } ||
            packages.size != packages.distinct().size ||
            drivers.any { driver -> OpenGlDriverPreference.entries.none { it.storedValue == driver } }
        ) {
            return DriverPreferenceReadback(DriverPreferenceStatus.UNVERIFIABLE)
        }
        val index = packages.indexOf(packageName)
        val readback = if (index == -1) OpenGlDriverPreference.SYSTEM
            else OpenGlDriverPreference.fromStoredValue(drivers[index])
        if (readback != requested) {
            return DriverPreferenceReadback(
                DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED, readback
            )
        }
        return DriverPreferenceReadback(
            if (requested == OpenGlDriverPreference.SYSTEM) {
                DriverPreferenceStatus.SYSTEM_DEFAULT
            } else {
                DriverPreferenceStatus.SYSTEM_SETTING_CONFIRMED
            },
            readback
        )
    }
}
