package com.cardenaspiero255.gamehubultra.store

import java.net.URI

object EpicAuthSecurity {
    fun requireSecureBackendUrl(rawUrl: String): String {
        val normalized = rawUrl.trim()
        require(normalized.isNotBlank()) { "Epic backend URL is required" }

        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("Epic backend URL is invalid", it) }

        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Epic backend URL must use HTTPS"
        }
        require(uri.userInfo == null) {
            "Epic backend URL must not contain embedded credentials"
        }
        require(!uri.host.isNullOrBlank()) {
            "Epic backend URL must include a valid host"
        }

        return normalized
    }
}
