package com.cardenaspiero255.gamehubultra.ai

import org.json.JSONObject
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
        val body = JSONObject()
            .put("query", request.originalText)
            .put("kind", request.kind.name)
            .put("requiresFreshData", request.requiresFreshData)
            .toString()

        val response = transport.post(
            endpoint = endpoint,
            apiKey = publishableKey,
            body = body,
            timeoutMillis = request.timeoutMillis
        )
        val json = JSONObject(response)
        if (json.optBoolean("abstained", false)) {
            error(json.optString("message", "Research backend abstained"))
        }

        val claimKey = json.optString("claimKey").trim()
        val value = json.optString("value").trim()
        val displayText = json.optString("displayText").trim()
        val sourceId = json.optString("sourceId").trim()
        require(claimKey.isNotBlank()) { "Missing claimKey" }
        require(value.isNotBlank()) { "Missing value" }
        require(displayText.isNotBlank()) { "Missing displayText" }
        require(sourceId.isNotBlank()) { "Missing sourceId" }

        return UltraResearchEvidence(
            claimKey = claimKey,
            value = value,
            displayText = displayText,
            sourceId = sourceId,
            authoritative = json.optBoolean("authoritative", false)
        )
    }
}
