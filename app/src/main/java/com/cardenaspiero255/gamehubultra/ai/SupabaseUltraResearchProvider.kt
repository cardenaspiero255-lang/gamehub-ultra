package com.cardenaspiero255.gamehubultra.ai

import java.net.HttpURLConnection
import java.net.URL

interface UltraResearchBackendTransport {
    fun post(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): String
}

object HttpUrlConnectionUltraResearchTransport : UltraResearchBackendTransport {
    override fun post(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        val safeTimeout = timeoutMillis.coerceIn(1_000L, 60_000L).toInt()
        connection.requestMethod = "POST"
        connection.connectTimeout = safeTimeout
        connection.readTimeout = safeTimeout
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("apikey", apiKey)
        connection.setRequestProperty("Accept", "application/json")

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                error("Research backend HTTP $status")
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}

class SupabaseUltraResearchProvider(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val transport: UltraResearchBackendTransport =
        HttpUrlConnectionUltraResearchTransport
) : UltraResearchProvider {
    override val id: String = "supabase-ultra-research"

    override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
        require(supabaseUrl.isNotBlank()) { "Supabase URL unavailable" }
        require(publishableKey.isNotBlank()) { "Supabase publishable key unavailable" }

        val endpoint = supabaseUrl.trimEnd('/') + "/functions/v1/ultra-research"
        val response = transport.post(
            endpoint = endpoint,
            apiKey = publishableKey,
            body = UltraResearchJsonCodec.encodeRequest(request),
            timeoutMillis = request.timeoutMillis
        )
        val decoded = UltraResearchJsonCodec.decodeResponse(response)
        if (decoded.abstained) {
            error(decoded.message ?: "Research backend abstained")
        }

        val claimKey = decoded.claimKey.orEmpty().trim()
        val value = decoded.value.orEmpty().trim()
        val displayText = decoded.displayText.orEmpty().trim()
        val sourceId = decoded.sourceId.orEmpty().trim()
        require(claimKey.isNotBlank()) { "Missing claimKey" }
        require(value.isNotBlank()) { "Missing value" }
        require(displayText.isNotBlank()) { "Missing displayText" }
        require(sourceId.isNotBlank()) { "Missing sourceId" }

        return UltraResearchEvidence(
            claimKey = claimKey,
            value = value,
            displayText = displayText,
            sourceId = sourceId,
            supportingSourceIds = decoded.sourceIds
                .map(String::trim)
                .filter { it.isNotBlank() && it != sourceId }
                .distinct(),
            independentSourceCount = decoded.independentSourceCount.coerceAtLeast(1),
            authoritative = decoded.authoritative,
            trustedReference = decoded.trustedReference
        )
    }
}
