package com.cardenaspiero255.gamehubultra.platform

import java.net.URI

data class RouterSsdpResponse(
    val location: String,
    val server: String?
)

data class RouterIdentity(
    val manufacturer: String?,
    val model: String?
)

object RouterDiscoveryParser {
    fun parseSsdpResponse(response: String): RouterSsdpResponse? {
        val headers = response
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase() to
                    line.substring(separator + 1).trim()
            }
            .toMap()

        val location = headers["location"]?.takeIf(String::isNotBlank) ?: return null
        return RouterSsdpResponse(
            location = location,
            server = headers["server"]?.takeIf(String::isNotBlank)
        )
    }

    fun locationMatchesGateway(location: String, gatewayAddress: String): Boolean =
        runCatching {
            URI(location).host?.equals(gatewayAddress.trim(), ignoreCase = true) == true
        }.getOrDefault(false)

    fun parseDeviceDescription(xml: String): RouterIdentity = RouterIdentity(
        manufacturer = tagValue(xml, "manufacturer"),
        model = tagValue(xml, "modelName")
    )

    private fun tagValue(xml: String, tag: String): String? {
        val pattern = Regex(
            pattern = """<$tag(?:\s[^>]*)?>(.*?)</$tag>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return pattern.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }
}
