package com.cardenaspiero255.gamehubultra.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/** CAR-58: explicit OAuth redirect boundaries and account-scoped Epic session refresh. */
class EpicIntegrationPolicyTest {
    private val state = "a".repeat(64)
    private val accountA = "0123456789abcdef0123456789abcdef"
    private val accountB = "fedcba9876543210fedcba9876543210"
    private val redirect = "https://app.gamehub-ultra.example/oauth/epic/callback"
    private val clientId = "0123456789abcdef0123456789abcdef"

    private fun code(
        callback: String,
        expected: String? = state,
        registeredRedirect: String = redirect
    ): String? = EpicIntegrationPolicy.authorizationCode(
        callback, expected, registeredRedirect
    )

    @Test fun exactHttpsRedirectAndMatchingStateAllowsAuthorizationCode() {
        assertEquals(
            "opaque-auth-code",
            code(
                "$redirect?state=$state&code=opaque-auth-code", state
            )
        )
    }

    @Test fun percentEncodedAuthorizationCodeIsDecodedOnce() {
        assertEquals(
            "safe/callback+code",
            code(
                "$redirect?code=safe%2Fcallback%2Bcode&state=$state", state
            )
        )
    }

    @Test fun missingOrInvalidExpectedStateFailsClosed() {
        val callback = "$redirect?state=$state&code=token"
        for (expected in listOf(null, "", "not-an-oauth-state", "b".repeat(64))) {
            assertNull(code(callback, expected))
        }
    }

    @Test fun mismatchedMissingOrDuplicateCallbackStateFailsClosed() {
        val urls = listOf(
            "$redirect?state=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&code=token",
            "$redirect?code=token",
            "$redirect?state=$state&state=$state&code=token",
            "$redirect?state=$state&state=other&code=token"
        )
        urls.forEach { assertNull(code(it, state)) }
    }

    @Test fun duplicatedBlankOrMissingAuthorizationCodeIsRejected() {
        val urls = listOf(
            "$redirect?state=$state",
            "$redirect?state=$state&code=",
            "$redirect?state=$state&code=first&code=second",
            "$redirect?state=$state&code=abc%0Adef"
        )
        urls.forEach { assertNull(code(it, state)) }
    }

    @Test fun wrongSchemeHostSubdomainPathOrPortCannotSupplyTokens() {
        val urls = listOf(
            "http://app.gamehub-ultra.example/oauth/epic/callback",
            "https://app.gamehub-ultra.example.evil.example/oauth/epic/callback",
            "https://evil.example/oauth/epic/callback",
            "https://app.gamehub-ultra.example:444/oauth/epic/callback",
            "https://app.gamehub-ultra.example/oauth/epic/other",
            "https://user@app.gamehub-ultra.example/oauth/epic/callback",
            "https://app.gamehub-ultra.example/oauth/epic/callback/"
        )
        urls.forEach {
            assertNull(code("$it?state=$state&code=token", state))
        }
    }

    @Test fun fragmentsInvalidEscapesAndAmbiguousQueriesFailClosed() {
        val urls = listOf(
            "$redirect?state=$state&code=token#fragment",
            "$redirect?state=$state&code=%GG",
            "$redirect?state=$state&code=token&code=ignored",
            "$redirect?state=$state&code=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        )
        urls.forEach { assertNull(code(it, state)) }
    }

    @Test fun explicitOAuthDenialCannotBeOverriddenByInjectedAuthorizationCode() {
        val deniedCallbacks = listOf(
            "$redirect?state=$state&error=access_denied",
            "$redirect?state=$state&code=untrusted&error=access_denied",
            "$redirect?state=$state&error=invalid_scope&code=untrusted"
        )
        deniedCallbacks.forEach { assertNull(code(it, state)) }
    }

    @Test fun unrelatedBrowserPageOrHtmlBodyNeverGrantsAuthorization() {
        assertNull(code(
            "https://www.epicgames.com/id/login?state=$state&code=token", state
        ))
        assertNull(code(
            "https://www.epicgames.com/id/api/redirect", state
        ))
    }


    @Test fun officialAuthorizeUrlUsesConfiguredClientRedirectAndState() {
        val url = EpicIntegrationPolicy.authorizationUrl(clientId, redirect, state)
        assertContains(url, "https://www.epicgames.com/id/authorize?")
        assertContains(url, "client_id=$clientId")
        assertContains(url, "response_type=code")
        assertContains(url, "scope=basic_profile")
        assertContains(url, "state=$state")
        assertContains(
            url, "redirect_uri=" + java.net.URLEncoder.encode(redirect, "UTF-8")
        )
    }

    @Test fun missingUnregisteredOrInsecureClientConfigurationIsBlocked() {
        assertFailsWith<IllegalArgumentException> {
            EpicIntegrationPolicy.authorizationUrl("", redirect, state)
        }
        assertFailsWith<IllegalArgumentException> {
            EpicIntegrationPolicy.authorizationUrl(clientId, "http://example.org/callback", state)
        }
        assertFailsWith<IllegalArgumentException> {
            EpicIntegrationPolicy.authorizationUrl(clientId, "$redirect#fragment", state)
        }
        assertFailsWith<IllegalArgumentException> {
            EpicIntegrationPolicy.authorizationUrl(clientId, redirect, "unbound-state")
        }
    }

    @Test fun callbackIsBoundToItsRegisteredRedirectNotAnUnrelatedUrl() {
        assertNull(code("$redirect?state=$state&code=token", state, "https://other.example/callback"))
        assertNull(code("$redirect?state=$state&code=token", state, ""))
    }

    @Test fun onlyTheRegisteredRedirectPathIsInterceptableByWebView() {
        assertEquals(true, EpicIntegrationPolicy.matchesRegisteredRedirect(
            "$redirect?state=$state&code=token", redirect
        ))
        assertEquals(false, EpicIntegrationPolicy.matchesRegisteredRedirect(
            "https://www.epicgames.com/id/login", redirect
        ))
    }

    @Test fun freshEpicConnectionCanBindOnlyCanonicalAuthenticatedAccountId() {
        assertEquals(
            EpicAccountAuthorization.NEW_CONNECTION,
            EpicIntegrationPolicy.authorizeAccount(null, accountA)
        )
    }

    @Test fun chosenEpicAccountAcceptsOnlyTheExactSameAuthenticatedAccountId() {
        assertEquals(
            EpicAccountAuthorization.MATCHED_ACCOUNT,
            EpicIntegrationPolicy.authorizeAccount(accountA, accountA)
        )
        assertEquals(
            EpicAccountAuthorization.ACCOUNT_MISMATCH,
            EpicIntegrationPolicy.authorizeAccount(accountA, accountB)
        )
    }

    @Test fun malformedSessionIdsNeverTriggerAccountMutation() {
        for (id in listOf("", "public-vanity", " $accountA ", accountA + ";token=x", "a".repeat(33))) {
            assertEquals(
                EpicAccountAuthorization.INVALID_SESSION,
                EpicIntegrationPolicy.authorizeAccount(accountA, id)
            )
        }
    }

    @Test fun unverifiableSelectedAccountIdMustBeReconnectedInsteadOfResynced() {
        for (id in listOf("", "vanity-name", " $accountA ", "a".repeat(31))) {
            assertEquals(
                EpicAccountAuthorization.UNVERIFIABLE_TARGET,
                EpicIntegrationPolicy.authorizeAccount(id, accountA)
            )
        }
    }
}
