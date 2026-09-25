package com.cardenaspiero255.gamehubultra.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EpicAuthSecurityTest {
    @Test
    fun rejectsMissingBackendUrl() {
        assertFailsWith<IllegalArgumentException> {
            EpicAuthSecurity.requireSecureBackendUrl("")
        }
    }

    @Test
    fun rejectsPlainHttpBackendUrl() {
        assertFailsWith<IllegalArgumentException> {
            EpicAuthSecurity.requireSecureBackendUrl("http://example.com/epic/token")
        }
    }

    @Test
    fun rejectsBackendUrlWithEmbeddedCredentials() {
        assertFailsWith<IllegalArgumentException> {
            EpicAuthSecurity.requireSecureBackendUrl(
                "https://user:password@example.com/epic/token"
            )
        }
    }

    @Test
    fun acceptsHttpsBackendUrl() {
        assertEquals(
            "https://example.com/epic/token",
            EpicAuthSecurity.requireSecureBackendUrl(
                "https://example.com/epic/token"
            )
        )
    }
}
