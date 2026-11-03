package com.cardenaspiero255.gamehubultra.store

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** CAR-58: only a verified OAuth callback from the registered redirect can authorize Epic. */
internal enum class EpicAccountAuthorization {
    NEW_CONNECTION,
    MATCHED_ACCOUNT,
    ACCOUNT_MISMATCH,
    INVALID_SESSION,
    UNVERIFIABLE_TARGET
}

internal object EpicIntegrationPolicy {
    private const val EPIC_AUTHORIZE_URL = "https://www.epicgames.com/id/authorize"
    private val oauthState = Regex("[0-9a-f]{64}")
    private val epicClientId = Regex("[0-9a-fA-F]{32}")
    private val epicAccountId = Regex("[0-9a-fA-F]{32}")

    /** A redirect URI must be registered to the app's own OAuth client in Epic Developer Portal. */
    private fun registeredRedirectUri(raw: String): URI? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return uri.takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                !it.host.isNullOrBlank() &&
                it.port == -1 &&
                it.userInfo == null &&
                it.rawFragment == null &&
                it.rawQuery == null &&
                it.rawPath.startsWith("/") &&
                it.rawPath.length > 1
        }
    }

    /** Official OAuth authorization code flow. No launcher client ID or internal Epic redirect. */
    fun authorizationUrl(
        clientId: String,
        registeredRedirect: String,
        state: String
    ): String {
        require(epicClientId.matches(clientId)) { "Epic OAuth client ID is not configured" }
        val redirectUri = requireNotNull(registeredRedirectUri(registeredRedirect)) {
            "Epic OAuth redirect URI must be configured with HTTPS"
        }
        require(oauthState.matches(state)) { "Epic OAuth state must be random hexadecimal" }
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri.toString(),
            "response_type" to "code",
            "scope" to "basic_profile",
            "state" to state
        )
        return EPIC_AUTHORIZE_URL + "?" +
            params.joinToString("&") { (name, value) ->
                name + "=" + URLEncoder.encode(value, "UTF-8")
            }
    }

    fun matchesRegisteredRedirect(callbackUrl: String, registeredRedirect: String): Boolean {
        val expected = registeredRedirectUri(registeredRedirect) ?: return false
        val callback = runCatching { URI(callbackUrl) }.getOrNull() ?: return false
        return callback.scheme.equals(expected.scheme, ignoreCase = true) &&
            callback.host.equals(expected.host, ignoreCase = true) &&
            callback.port == expected.port &&
            callback.rawPath == expected.rawPath &&
            callback.userInfo == null &&
            callback.rawFragment == null
    }

    /**
     * Never read authorization codes from arbitrary web page bodies or cookies.
     * Reject ambiguous, repeated or unbound state/code parameters.
     */
    fun authorizationCode(
        callbackUrl: String,
        expectedState: String?,
        registeredRedirect: String
    ): String? {
        if (expectedState == null || !oauthState.matches(expectedState)) return null
        if (!matchesRegisteredRedirect(callbackUrl, registeredRedirect)) return null
        val uri = runCatching { URI(callbackUrl) }.getOrNull() ?: return null
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
