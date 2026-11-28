package com.cardenaspiero255.gamehubultra.store

import java.security.SecureRandom

/** Public, nonsecret app configuration. The OAuth secret stays on the HTTPS backend. */
internal data class EpicOAuthConfiguration(
    val backendUrl: String,
    val clientId: String,
    val redirectUri: String
)

internal data class EpicAuthorizationRequest(
    val state: String,
    val authorizationUrl: String
)

/** Validates the trusted configuration before opening the browser. */
internal object EpicAuthorizationRequestFactory {
    fun create(
        backendUrl: String,
        clientId: String,
        redirectUri: String
    ): EpicAuthorizationRequest {
        EpicAuthSecurity.requireSecureBackendUrl(backendUrl)
        val state = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return EpicAuthorizationRequest(
            state = state,
            authorizationUrl = EpicIntegrationPolicy.authorizationUrl(
                clientId, redirectUri, state
            )
        )
    }
}
