package com.cardenaspiero255.gamehubultra.voice

internal data class UltraSessionMetrics(
    val sessionActive: Boolean,
    val fps: Float? = null,
    val refreshRateHz: Float? = null,
    val batteryPercent: Int? = null,
    val thermalLabel: String? = null,
    val ramMb: Long? = null,
    val latencyMs: Int? = null
)

internal sealed interface UltraInSessionVoiceResponse {
    val handled: Boolean
    val message: String

    data object NotHandled : UltraInSessionVoiceResponse {
        override val handled: Boolean = false
        override val message: String = ""
    }

    data class Answer(override val message: String) : UltraInSessionVoiceResponse {
        override val handled: Boolean = true
    }
}

internal object UltraInSessionVoiceResponder {
    fun respond(query: String, metrics: UltraSessionMetrics): UltraInSessionVoiceResponse {
        if (!metrics.sessionActive) return UltraInSessionVoiceResponse.NotHandled

        val normalized = VoiceCommandParser.stripLeadingAssistantInvocation(query)
        val asksSession = listOf(
            "estado de la partida", "estado partida", "fps", "hz", "refresco",
            "bateria", "temperatura", "termico", "ram", "latencia", "ping"
        ).any(normalized::contains)
        if (!asksSession) return UltraInSessionVoiceResponse.NotHandled

        val parts = buildList {
            metrics.fps?.takeIf { it.isFinite() && it >= 0f }?.let { add("${it.toInt()} FPS") }
            metrics.refreshRateHz?.takeIf { it.isFinite() && it > 0f }?.let { add("${it.toInt()} Hz") }
            metrics.batteryPercent?.takeIf { it in 0..100 }?.let { add("batería ${it}%") }
            metrics.thermalLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { add("térmica ${it}") }
            metrics.ramMb?.takeIf { it >= 0 }?.let { add("RAM ${it} MB") }
            metrics.latencyMs?.takeIf { it >= 0 }?.let { add("latencia ${it} ms") }
        }

        return UltraInSessionVoiceResponse.Answer(
            if (parts.isEmpty()) "Métricas de sesión no disponibles."
            else parts.joinToString(" · ").take(160)
        )
    }
}
