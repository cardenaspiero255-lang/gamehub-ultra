package com.cardenaspiero255.gamehubultra.store

import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Exercise real OAuth URL construction without requiring a provider account or live secrets. */
class EpicAuthorizationRequestFactoryTest {
    private val clientId = "abcdef0123456789abcdef0123456789"
    private val redirect = "https://accounts.example.test/oauth/epic/callback"
    private val backend = "https://api.example.test/epic/oauth/exchange"

    @Test fun configuredClientProducesBoundRandomStateAndDocumentedOAuthParameters() {
        val first = EpicAuthorizationRequestFactory.create(backend, clientId, redirect)
        val second = EpicAuthorizationRequestFactory.create(backend, clientId, redirect)
        val url = URI(first.authorizationUrl)
        val params = url.rawQuery.split("&").associate { part ->
            val pair = part.split("=", limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }

        assertEquals("https", url.scheme)
        assertEquals("www.epicgames.com", url.host)
        assertEquals("/id/authorize", url.path)
        assertEquals(clientId, params["client_id"])
        assertEquals(redirect, params["redirect_uri"])
        assertEquals("code", params["response_type"])
        assertEquals("basic_profile", params["scope"])
        assertEquals(first.state, params["state"])
        assertTrue(first.state.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(first.state, second.state)
    }

    @Test fun refusesIncompleteOrInsecureClientConfiguration() {
        assertFailsWith<IllegalArgumentException> {
            EpicAuthorizationRequestFactory.create("", clientId, redirect)
        }
        assertFailsWith<IllegalArgumentException> {
            EpicAuthorizationRequestFactory.create(
                "http://api.example.test/epic/oauth/exchange", clientId, redirect
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EpicAuthorizationRequestFactory.create(backend, "", redirect)
        }
        assertFailsWith<IllegalArgumentException> {
            EpicAuthorizationRequestFactory.create(backend, clientId, "http://evil.example/callback")
        }
    }
}
