package com.cardenaspiero255.gamehubultra.platform.gpu

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * CAR-54: user-owned per-game OpenGL preference and read-only Android status.
 * Android grants ordinary apps no public API to install/select Turnip or write
 * privileged graphics-driver settings. Never silently change global settings.
 */
internal object AndroidGraphicsDriverPreferences {
    private const val FILE = "car54_graphics_driver_preferences"
    private const val KEY_PREFIX = "driver_"
    private const val PACKAGES_SETTING = "angle_gl_driver_selection_pkgs"
    private const val VALUES_SETTING = "angle_gl_driver_selection_values"

    fun selected(context: Context, packageName: String): OpenGlDriverPreference {
        if (!GraphicsDriverPreferenceReadback.validPackage(packageName)) {
            return OpenGlDriverPreference.SYSTEM
        }
        val value = runCatching {
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + packageName, null)
        }.getOrNull()
        return OpenGlDriverPreference.fromStoredValue(value)
    }

    fun save(context: Context, packageName: String, preference: OpenGlDriverPreference): Boolean {
        if (!GraphicsDriverPreferenceReadback.validPackage(packageName)) return false
        return runCatching {
            val editor = context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            if (preference == OpenGlDriverPreference.SYSTEM) {
                editor.remove(KEY_PREFIX + packageName)
            } else {
                editor.putString(KEY_PREFIX + packageName, preference.storedValue)
            }
            editor.commit()
        }.getOrDefault(false)
    }

    /**
     * The returned status confirms only the system's stored preference. Even
     * confirmed ANGLE does NOT prove the running game's renderer changed.
     */
    fun inspect(
        context: Context,
        packageName: String,
        readSetting: (String) -> String? = { key ->
            Settings.Global.getString(context.contentResolver, key)
        }
    ): DriverPreferenceReadback {
        if (!GraphicsDriverPreferenceReadback.validPackage(packageName)) {
            return DriverPreferenceReadback(DriverPreferenceStatus.UNVERIFIABLE)
        }
        val values = runCatching {
            Pair(readSetting(PACKAGES_SETTING), readSetting(VALUES_SETTING))
        }.getOrNull()
            ?: return DriverPreferenceReadback(DriverPreferenceStatus.UNVERIFIABLE)
        return GraphicsDriverPreferenceReadback.inspect(
            packageName, selected(context, packageName), values.first, values.second
        )
    }

    /** Opens official Developer Options; it cannot pre-select a game or driver. */
    fun openSystemDeveloperOptions(context: Context): Boolean =
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
}
