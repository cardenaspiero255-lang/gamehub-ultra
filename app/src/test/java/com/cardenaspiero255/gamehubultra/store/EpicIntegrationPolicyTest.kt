package com.cardenaspiero255.gamehubultra.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CAR-58: explicit OAuth redirect boundaries and account-scoped Epic session refresh. */
class EpicIntegrationPolicyTest {
    private val state = "a".repeat(64)
    private val accountA = "0123456789abcdef0123456789abcdef"
    private val accountB = "fedcba9876543210fedcba9876543210"
    private val redirect = "https://www.epicgames.com/id/api/redirect"

    @Test fun exactHttpsRedirectAndMatchingStateAllowsAuthorizationCode() {
        assertEquals(
            "opaque-auth-code",
            EpicIntegrationPolicy.authorizationCode(
                "$redirect?state=$state&code=opaque-auth-code", state
            )
        )
    }

    @Test fun percentEncodedAuthorizationCodeIsDecodedOnce() {
        assertEquals(
            "safe/callback+code",
            EpicIntegrationPolicy.authorizationCode(
                "$redirect?code=safe%2Fcallback%2Bcode&state=$state", state
            )
        )
    }

    @Test fun missingOrInvalidExpectedStateFailsClosed() {
        val callback = "$redirect?state=$state&code=token"
        for (expected in listOf(null, "", "not-an-oauth-state", "b".repeat(64))) {
            assertNull(EpicIntegrationPolicy.authorizationCode(callback, expected))
        }
    }

    @Test fun mismatchedMissingOrDuplicateCallbackStateFailsClosed() {
        val urls = listOf(
            "$redirect?state=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&code=token",
            "$redirect?code=token",
            "$redirect?state=$state&state=$state&code=token",
            "$redirect?state=$state&state=other&code=token"
        )
        urls.forEach { assertNull(EpicIntegrationPolicy.authorizationCode(it, state)) }
    }

    @Test fun duplicatedBlankOrMissingAuthorizationCodeIsRejected() {
        val urls = listOf(
            "$redirect?state=$state",
            "$redirect?state=$state&code=",
            "$redirect?state=$state&code=first&code=second",
            "$redirect?state=$state&code=abc%0Adef"
        )
        urls.forEach { assertNull(EpicIntegrationPolicy.authorizationCode(it, state)) }
    }

    @Test fun wrongSchemeHostSubdomainPathOrPortCannotSupplyTokens() {
        val urls = listOf(
            "http://www.epicgames.com/id/api/redirect",
            "https://www.epicgames.com.evil.example/id/api/redirect",
            "https://evil.example/id/api/redirect",
            "https://www.epicgames.com:444/id/api/redirect",
            "https://www.epicgames.com/id/api/other",
            "https://user@www.epicgames.com/id/api/redirect",
            "https://www.epicgames.com/id/api/redirect/"
        )
        urls.forEach {
            assertNull(EpicIntegrationPolicy.authorizationCode("$it?state=$state&code=token", state))
        }
    }

    @Test fun fragmentsInvalidEscapesAndAmbiguousQueriesFailClosed() {
        val urls = listOf(
            "$redirect?state=$state&code=token#fragment",
            "$redirect?state=$state&code=%GG",
            "$redirect?state=$state&code=token&code=ignored",
            "$redirect?state=$state&code=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
        )
        urls.forEach { assertNull(EpicIntegrationPolicy.authorizationCode(it, state)) }
    }

    @Test fun unrelatedBrowserPageOrHtmlBodyNeverGrantsAuthorization() {
        assertNull(EpicIntegrationPolicy.authorizationCode(
            "https://www.epicgames.com/id/login?state=$state&code=token", state
        ))
        assertNull(EpicIntegrationPolicy.authorizationCode(
            "https://www.epicgames.com/id/api/redirect", state
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
