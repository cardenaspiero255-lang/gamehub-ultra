package com.cardenaspiero255.gamehubultra.ai

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

fun interface UltraPublicKnowledgeTransport {
    fun get(
        url: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse
}

object HttpUrlConnectionUltraPublicKnowledgeTransport : UltraPublicKnowledgeTransport {
    override fun get(
        url: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        val safeTimeout = timeoutMillis.coerceIn(1_000L, 20_000L).toInt()
        connection.requestMethod = "GET"
        connection.connectTimeout = safeTimeout
        connection.readTimeout = safeTimeout
        connection.setRequestProperty(
            "User-Agent",
            "GameHub-Ultra/0.3 (Android; public knowledge fallback)"
        )
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            UltraResearchHttpResponse(
                statusCode = status,
                body = stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Keyless public fallback for stable general knowledge.
 *
 * It intentionally refuses fresh/current/comparison queries. Its only job is
 * to keep ordinary stable questions answerable when the private research
 * backend is unavailable or misconfigured.
 */
class WikimediaUltraResearchProvider(
    private val transport: UltraPublicKnowledgeTransport =
        HttpUrlConnectionUltraPublicKnowledgeTransport
) : UltraResearchProvider {

    override val id: String = "wikimedia-public"

    override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
        when (val result = fetchResult(request)) {
            is UltraProviderResult.Evidence -> result.evidence
            is UltraProviderResult.Abstained ->
                error(result.message ?: "Wikimedia abstained: " + result.reasonCode)
            is UltraProviderResult.Failure ->
                error(result.message ?: "Wikimedia failed: " + result.reasonCode)
        }

    override fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult {
        if (
            request.kind != UltraGeneralQueryKind.GENERAL_KNOWLEDGE ||
            request.verificationMode != UltraVerificationMode.OPTIONAL ||
            request.requiresFreshData
        ) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NOT_APPLICABLE",
                message = "El fallback público solo se usa para conocimiento estable."
            )
        }

        val topic = extractTopic(request.originalText)
        if (topic.isBlank()) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_EMPTY_QUERY",
                message = "No pude extraer un tema estable de la pregunta."
            )
        }

        val searchUrl = buildString {
            append("https://es.wikipedia.org/w/api.php")
            append("?action=query&list=search&srlimit=1&format=json&origin=*")
            append("&srsearch=")
            append(urlEncode(topic))
        }

        val search = getSafely(searchUrl, request.timeoutMillis)
        if (search !is TransportOutcome.Success) {
            return search.toProviderResult("wikimedia-search")
        }
        if (search.response.statusCode !in 200..299) {
            return httpFailure(search.response.statusCode, "wikimedia-search")
        }

        val title = jsonString(search.response.body, "title")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NO_RESULT",
                message = "Wikimedia no encontró un resultado utilizable.",
                stage = "wikimedia-search"
            )

        val extractUrl = buildString {
            append("https://es.wikipedia.org/w/api.php")
            append("?action=query&prop=extracts%7Cinfo&inprop=url")
            append("&exintro=1&explaintext=1&redirects=1&format=json&origin=*")
            append("&titles=")
            append(urlEncode(title))
        }

        val extractResponse = getSafely(extractUrl, request.timeoutMillis)
        if (extractResponse !is TransportOutcome.Success) {
            return extractResponse.toProviderResult("wikimedia-extract")
        }
        if (extractResponse.response.statusCode !in 200..299) {
            return httpFailure(
                extractResponse.response.statusCode,
                "wikimedia-extract"
            )
        }

        val extract = jsonString(extractResponse.response.body, "extract")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NO_EVIDENCE",
                message = "Wikimedia no devolvió una explicación utilizable.",
                stage = "wikimedia-extract"
            )

        val canonicalUrl = jsonString(
            extractResponse.response.body,
            "canonicalurl"
        )
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "https://es.wikipedia.org/wiki/" +
                urlEncode(title.replace(' ', '_'))

        val concise = conciseIntro(extract)
        return UltraProviderResult.Evidence(
            UltraResearchEvidence(
                claimKey = "general:" + slug(title),
                value = normalizeForComparison(concise),
                displayText = concise,
                sourceId = canonicalUrl,
                independentSourceCount = 1,
                authoritative = true
            )
        )
    }

    private sealed interface TransportOutcome {
        data class Success(
            val response: UltraResearchHttpResponse
        ) : TransportOutcome

        data class Failure(
            val message: String?
        ) : TransportOutcome
    }

    private fun getSafely(
        url: String,
        timeoutMillis: Long
    ): TransportOutcome =
        try {
            TransportOutcome.Success(transport.get(url, timeoutMillis))
        } catch (error: Exception) {
            TransportOutcome.Failure(error.message)
        }

    private fun TransportOutcome.toProviderResult(
        stage: String
    ): UltraProviderResult =
        when (this) {
            is TransportOutcome.Success ->
                UltraProviderResult.Failure(
                    reasonCode = "PUBLIC_FALLBACK_FAILURE",
                    message = "Wikimedia no devolvió una respuesta utilizable.",
                    retryable = true,
                    stage = stage
                )

            is TransportOutcome.Failure ->
                UltraProviderResult.Failure(
                    reasonCode = "PUBLIC_FALLBACK_NETWORK_FAILURE",
                    message = message,
                    retryable = true,
                    stage = stage
                )
        }

    private fun httpFailure(
        status: Int,
        stage: String
    ): UltraProviderResult.Failure =
        UltraProviderResult.Failure(
            reasonCode = when (status) {
                408, 504 -> "UPSTREAM_TIMEOUT"
                429 -> "UPSTREAM_RATE_LIMIT"
                500, 502, 503 -> "UPSTREAM_UNAVAILABLE"
                else -> "PUBLIC_FALLBACK_HTTP_FAILURE"
            },
            message = "Wikimedia respondió HTTP " + status + ".",
            retryable = status in setOf(408, 429, 500, 502, 503, 504),
            stage = stage,
            upstreamStatus = status
        )

    private fun extractTopic(originalText: String): String {
        val current = originalText
            .substringAfterLast("Pregunta actual:", originalText)
            .trim()
            .replace(
                Regex(
                    """^\s*(?:gamehub\s+ultra|gamehub|ultra)\s*[,;:.-]?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim(' ', '¿', '?', '¡', '!')

        return current
            .replace(
                Regex(
                    """^(?:que es|qué es|que son|qué son|quien es|quién es|quienes son|quiénes son|define|definicion de|definición de|explicame|explícame|explica|dime que es|dime qué es|what is|what are|who is|who are|define)\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
    }

    private fun conciseIntro(value: String): String {
        val compact = value.replace(Regex("""\s+"""), " ").trim()
        if (compact.length <= MAX_DISPLAY_CHARS) return compact

        val candidate = compact.take(MAX_DISPLAY_CHARS)
        val sentenceEnd = maxOf(
            candidate.lastIndexOf(". "),
            candidate.lastIndexOf("? "),
            candidate.lastIndexOf("! ")
        )
        return if (sentenceEnd >= MAX_DISPLAY_CHARS / 2) {
            candidate.take(sentenceEnd + 1)
        } else {
            candidate.trimEnd() + "…"
        }
    }

    private fun slug(value: String): String =
        normalizeForComparison(value)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(120)

    private fun normalizeForComparison(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")

    private fun jsonString(
        json: String,
        key: String
    ): String? {
        val pattern = Regex(
            "\"" + Regex.escape(key) +
                "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        )
        return pattern.find(json)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeJsonString)
    }

    private fun unescapeJsonString(value: String): String =
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
                            val code = value
                                .substring(index + 2, end)
                                .toIntOrNull(16)
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

    private companion object {
        const val MAX_DISPLAY_CHARS = 1_800
    }
}
