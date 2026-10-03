package com.cardenaspiero255.gamehubultra.ai

internal data class UltraResearchBackendResponse(
    val claimKey: String?,
    val value: String?,
    val displayText: String?,
    val sourceId: String?,
    val sourceIds: List<String>,
    val independentSourceCount: Int,
    val authoritative: Boolean,
    val abstained: Boolean,
    val message: String?,
    val reasonCode: String?,
    val retryable: Boolean,
    val stage: String?,
    val upstreamStatus: Int?
)

/**
 * Minimal flat-JSON codec for the CAR-73 research boundary.
 * Keeping this in pure Kotlin makes the protocol testable on the JVM without
 * relying on Android's org.json implementation.
 */
internal object UltraResearchJsonCodec {
    private data class RequestText(
        val question: String,
        val context: String?
    )

    fun encodeRequest(request: UltraGeneralQueryRequest): String {
        val text = splitRequestText(request.originalText)
        return buildString {
            append('{')
            append("\"query\":\"")
            append(escape(text.question))
            append('"')
            text.context?.takeIf { it.isNotBlank() }?.let { context ->
                append(",\"context\":\"")
                append(escape(context))
                append('"')
            }
            append(",\"kind\":\"")
            append(request.kind.name)
            append("\",\"verificationMode\":\"")
            append(request.verificationMode.name)
            append("\",\"requiresFreshData\":")
            append(request.requiresFreshData)
            append(",\"correlationId\":\"")
            append(escape(request.correlationId))
            append('"')
            append('}')
        }
    }

    private fun splitRequestText(originalText: String): RequestText {
        val clean = originalText.trim()
        val contextPrefix = "Contexto previo:"
        val questionMarker = "\nPregunta actual:"
        if (!clean.startsWith(contextPrefix, ignoreCase = true)) {
            return RequestText(question = clean, context = null)
        }
        val markerIndex = clean.indexOf(questionMarker, ignoreCase = true)
        if (markerIndex < 0) {
            return RequestText(question = clean, context = null)
        }
        val context = clean.substring(contextPrefix.length, markerIndex).trim()
        val question = clean.substring(markerIndex + questionMarker.length).trim()
        return if (question.isBlank()) {
            RequestText(question = clean, context = null)
        } else {
            RequestText(question = question, context = context.takeIf { it.isNotBlank() })
        }
    }

    fun decodeResponse(json: String): UltraResearchBackendResponse =
        UltraResearchBackendResponse(
            claimKey = stringField(json, "claimKey"),
            value = stringField(json, "value"),
            displayText = stringField(json, "displayText"),
            sourceId = stringField(json, "sourceId"),
            sourceIds = stringArrayField(json, "sourceIds"),
            independentSourceCount = intField(json, "independentSourceCount") ?: 1,
            authoritative = booleanField(json, "authoritative") ?: false,
            abstained = booleanField(json, "abstained") ?: false,
            message = stringField(json, "message"),
            reasonCode = stringField(json, "reasonCode"),
            retryable = booleanField(json, "retryable") ?: false,
            stage = stringField(json, "stage"),
            upstreamStatus = intField(json, "upstreamStatus")
        )

    private fun stringField(json: String, key: String): String? {
        val pattern = Regex(
            "\"" + Regex.escape(key) +
                "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        )
        return pattern.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    private fun stringArrayField(json: String, key: String): List<String> {
        val pattern = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*\\[(.*?)]",
            RegexOption.DOT_MATCHES_ALL
        )
        val body = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(body)
            .map { match -> unescape(match.groupValues[1]) }
            .toList()
    }

    private fun intField(json: String, key: String): Int? {
        val pattern = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*(-?\\d+)"
        )
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
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
