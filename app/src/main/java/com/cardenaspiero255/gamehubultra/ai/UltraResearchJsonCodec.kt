package com.cardenaspiero255.gamehubultra.ai

internal data class UltraResearchBackendResponse(
    val claimKey: String?,
    val value: String?,
    val displayText: String?,
    val sourceId: String?,
    val authoritative: Boolean,
    val abstained: Boolean,
    val message: String?
)

/**
 * Minimal flat-JSON codec for the CAR-73 research boundary.
 * Keeping this in pure Kotlin makes the protocol testable on the JVM without
 * relying on Android's org.json implementation.
 */
internal object UltraResearchJsonCodec {
    fun encodeRequest(request: UltraGeneralQueryRequest): String =
        buildString {
            append('{')
            append("\"query\":\"")
            append(escape(request.originalText))
            append("\",\"kind\":\"")
            append(request.kind.name)
            append("\",\"requiresFreshData\":")
            append(request.requiresFreshData)
            append('}')
        }

    fun decodeResponse(json: String): UltraResearchBackendResponse =
        UltraResearchBackendResponse(
            claimKey = stringField(json, "claimKey"),
            value = stringField(json, "value"),
            displayText = stringField(json, "displayText"),
            sourceId = stringField(json, "sourceId"),
            authoritative = booleanField(json, "authoritative") ?: false,
            abstained = booleanField(json, "abstained") ?: false,
            message = stringField(json, "message")
        )

    private fun stringField(json: String, key: String): String? {
        val pattern = Regex(
            "\"" + Regex.escape(key) +
                "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        )
        return pattern.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    private fun booleanField(json: String, key: String): Boolean? {
        val pattern = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*(true|false)",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(json)
            ?.groupValues
            ?.get(1)
            ?.equals("true", ignoreCase = true)
    }

    private fun escape(value: String): String =
        buildString(value.length + 16) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append("\\u")
                            append(ch.code.toString(16).padStart(4, '0'))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
        }

    private fun unescape(value: String): String =
        buildString(value.length) {
            var index = 0
            while (index < value.length) {
                val ch = value[index]
                if (ch != '\\' || index + 1 >= value.length) {
                    append(ch)
                    index++
                    continue
                }

                when (val escaped = value[index + 1]) {
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'b' -> append('\b')
                    'f' -> append('\u000C')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        val end = index + 6
                        if (end <= value.length) {
                            val code = value.substring(index + 2, end).toIntOrNull(16)
                            if (code != null) {
                                append(code.toChar())
                                index = end
                                continue
                            }
                        }
                        append('u')
                    }
                    else -> append(escaped)
                }
                index += 2
            }
        }
}
