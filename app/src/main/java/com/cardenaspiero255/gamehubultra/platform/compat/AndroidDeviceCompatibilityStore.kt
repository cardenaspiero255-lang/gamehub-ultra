package com.cardenaspiero255.gamehubultra.platform.compat

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily

/**
 * CAR-56: local, private and versioned compatibility rule storage.
 * No device-unique identifiers, accounts, raw hardware strings or secrets.
 * A stored rule is never authenticated by persistence alone.
 */
internal object AndroidDeviceCompatibilityStore {
    private const val PREFS = "car56_device_compatibility_v1"
    private const val RECORDS = "records"
    private const val HEADER = "CAR56:1"
    private const val MAX_RULES = 64
    private const val MAX_PAYLOAD_LENGTH = 8192
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{7,63}")
    private val packagePattern =
        Regex("[A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*)+")

    fun save(context: Context, records: List<DeviceCompatibilityRule>): Boolean {
        val payload = serialize(records) ?: return false
        return runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(RECORDS, payload).commit()
        }.getOrDefault(false)
    }

    /** Corruption, unknown versions and inaccessible storage use no records. */
    fun load(context: Context): List<DeviceCompatibilityRule> {
        val raw = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(RECORDS, null)
        }.getOrNull() ?: return emptyList()
        return parse(raw) ?: emptyList()
    }

    fun clear(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(RECORDS).commit()
    }.getOrDefault(false)

    private fun validRule(record: DeviceCompatibilityRule): Boolean =
        record.schemaVersion == DeviceCompatibilityDatabase.SCHEMA_VERSION &&
            idPattern.matches(record.ruleId) &&
            idPattern.matches(record.evidenceId) &&
            packagePattern.matches(record.gamePackage) &&
            record.key.androidApiLevel in 26..100 &&
            record.key.socFamily != VendorSocFamily.GENERIC &&
            record.key.gpuFamily != CompatibilityGpuFamily.UNKNOWN

    private fun uniqueScope(rule: DeviceCompatibilityRule): String =
        listOf(rule.gamePackage, rule.key.socFamily.name,
            rule.key.gpuFamily.name, rule.key.androidApiLevel.toString()
        ).joinToString("|")

    private fun serialize(rules: List<DeviceCompatibilityRule>): String? {
        if (rules.size > MAX_RULES || rules.any { !validRule(it) } ||
            rules.map(::uniqueScope).distinct().size != rules.size
        ) return null

        val text = buildString {
            append(HEADER)
            for (rule in rules) {
                append('\n')
                append(listOf(
                    rule.schemaVersion.toString(),
                    rule.ruleId,
                    rule.evidenceId,
                    rule.gamePackage,
                    rule.key.socFamily.name,
                    rule.key.gpuFamily.name,
                    rule.key.androidApiLevel.toString(),
                    rule.recommendedProfile.name
                ).joinToString("\t"))
            }
        }
        return text.takeIf { it.length <= MAX_PAYLOAD_LENGTH }
    }

    private fun parse(raw: String): List<DeviceCompatibilityRule>? {
        if (raw.length > MAX_PAYLOAD_LENGTH) return null
        val lines = raw.split('\n')
        if (lines.firstOrNull() != HEADER || lines.size - 1 > MAX_RULES) return null
        val records = ArrayList<DeviceCompatibilityRule>(lines.size - 1)
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size != 8) return null
            val schema = parts[0].toIntOrNull() ?: return null
            val soc = VendorSocFamily.entries.firstOrNull { it.name == parts[4] }
                ?: return null
            val gpu = CompatibilityGpuFamily.entries.firstOrNull { it.name == parts[5] }
                ?: return null
            val androidApi = parts[6].toIntOrNull() ?: return null
            val profile = PerformanceProfile.entries.firstOrNull { it.name == parts[7] }
                ?: return null
            val rule = DeviceCompatibilityRule(
                schemaVersion = schema,
                ruleId = parts[1],
                evidenceId = parts[2],
                gamePackage = parts[3],
                key = CompatibilityDeviceKey(soc, gpu, androidApi),
                recommendedProfile = profile
            )
            if (!validRule(rule)) return null
            records.add(rule)
        }
        if (records.map(::uniqueScope).distinct().size != records.size) return null
        return records
    }
}
