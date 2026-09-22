package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceEventType {
    SESSION_STARTED,
    THERMAL_CHANGED,
    POLICY_CHANGED
}

data class PerformanceEvent(
    val timestampMillis: Long,
    val type: PerformanceEventType,
    val profile: PerformanceProfile? = null,
    val score: Int? = null,
    val detail: String = ""
)

object PerformanceEventCodec {
    fun encode(event: PerformanceEvent): String =
        listOf(
            event.timestampMillis.toString(),
            event.type.name,
            event.profile?.name.orEmpty(),
            event.score?.toString().orEmpty(),
            sanitize(event.detail)
        ).joinToString("\t")

    fun decode(value: String): PerformanceEvent? {
        val parts = value.split("\t", limit = 5)
        if (parts.size != 5) return null

        val timestamp = parts[0].toLongOrNull() ?: return null
        val type = runCatching { PerformanceEventType.valueOf(parts[1]) }.getOrNull() ?: return null
        val profile = parts[2]
            .takeIf(String::isNotBlank)
            ?.let { raw -> PerformanceProfile.entries.firstOrNull { it.name == raw } }
        val score = parts[3].toIntOrNull()?.takeIf { it in 0..100 }

        return PerformanceEvent(
            timestampMillis = timestamp,
            type = type,
            profile = profile,
            score = score,
            detail = parts[4]
        )
    }

    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
