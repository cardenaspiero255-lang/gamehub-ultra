package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceEventType {
    SESSION_STARTED,
    SESSION_ENDED,
    THERMAL_CHANGED,
    POLICY_CHANGED
}

data class PerformanceEvent(
    val timestampMillis: Long,
    val type: PerformanceEventType,
    val sessionId: String,
    val profile: PerformanceProfile? = null,
    val score: Int? = null,
    val detail: String = ""
)

object PerformanceEventCodec {
    fun encode(event: PerformanceEvent): String =
        listOf(
            event.timestampMillis.toString(),
            event.type.name,
            sanitize(event.sessionId),
            event.profile?.name.orEmpty(),
            event.score?.toString().orEmpty(),
            sanitize(event.detail)
        ).joinToString("\t")

    fun decode(value: String): PerformanceEvent? {
        val parts = value.split("\t", limit = 6)
        if (parts.size != 6) return null

        val timestamp = parts[0].toLongOrNull() ?: return null
        val type = runCatching {
            PerformanceEventType.valueOf(parts[1])
        }.getOrNull() ?: return null
        val sessionId = parts[2].takeIf(String::isNotBlank) ?: return null
        val profile = parts[3]
            .takeIf(String::isNotBlank)
            ?.let { raw -> PerformanceProfile.entries.firstOrNull { it.name == raw } }
        val score = parts[4].toIntOrNull()?.takeIf { it in 0..100 }

        return PerformanceEvent(
            timestampMillis = timestamp,
            type = type,
            sessionId = sessionId,
            profile = profile,
            score = score,
            detail = parts[5]
        )
    }

    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
