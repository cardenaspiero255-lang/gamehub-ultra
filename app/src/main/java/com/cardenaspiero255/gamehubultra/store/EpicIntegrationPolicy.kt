package com.cardenaspiero255.gamehubultra.store

import java.net.URI
import java.net.URLDecoder

/** CAR-58: only a verified, exact Epic OAuth callback can authorize a new link. */
internal enum class EpicAccountAuthorization {
    NEW_CONNECTION,
    MATCHED_ACCOUNT,
    ACCOUNT_MISMATCH,
    INVALID_SESSION,
    UNVERIFIABLE_TARGET
}

internal object EpicIntegrationPolicy {
    private const val REDIRECT_HOST = "www.epicgames.com"
    private const val REDIRECT_PATH = "/id/api/redirect"
    private val oauthState = Regex("[0-9a-f]{64}")
    private val epicAccountId = Regex("[0-9a-fA-F]{32}")

    /**
     * Never read authorization codes from arbitrary web page bodies or cookies.
     * Reject ambiguous, repeated or unbound state/code parameters.
     */
    fun authorizationCode(callbackUrl: String, expectedState: String?): String? {
        if (expectedState == null || !oauthState.matches(expectedState)) return null
        val uri = runCatching { URI(callbackUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals(REDIRECT_HOST, ignoreCase = true) ||
            uri.rawPath != REDIRECT_PATH ||
            uri.port != -1 ||
            uri.userInfo != null ||
            uri.rawFragment != null
        ) return null

        val rawQuery = uri.rawQuery ?: return null
        val params = runCatching {
            rawQuery.split('&').map { entry ->
                val separator = entry.indexOf('=')
                require(separator > 0) { "Malformed OAuth callback query" }
                URLDecoder.decode(entry.substring(0, separator), "UTF-8") to
                    URLDecoder.decode(entry.substring(separator + 1), "UTF-8")
            }
        }.getOrNull() ?: return null

        val states = params.filter { it.first == "state" }
        val codes = params.filter { it.first == "code" }
        if (states.size != 1 || states.single().second != expectedState ||
            codes.size != 1
        ) return null
        return codes.single().second.takeIf { code ->
            code.isNotBlank() && code.length <= 2048 &&
                code.none { it.isWhitespace() || it.isISOControl() }
        }
    }

    /** A selected Epic account cannot be silently switched by a shared browser session. */
    fun authorizeAccount(
        expectedAccountId: String?,
        authenticatedAccountId: String
    ): EpicAccountAuthorization {
        if (!epicAccountId.matches(authenticatedAccountId)) {
            return EpicAccountAuthorization.INVALID_SESSION
        }
        if (expectedAccountId == null) return EpicAccountAuthorization.NEW_CONNECTION
        if (!epicAccountId.matches(expectedAccountId)) {
            return EpicAccountAuthorization.UNVERIFIABLE_TARGET
        }
        return if (expectedAccountId.equals(authenticatedAccountId, ignoreCase = true)) {
            EpicAccountAuthorization.MATCHED_ACCOUNT
        } else {
            EpicAccountAuthorization.ACCOUNT_MISMATCH
        }
    }
}
