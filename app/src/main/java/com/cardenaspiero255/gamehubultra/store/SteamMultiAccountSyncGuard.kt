package com.cardenaspiero255.gamehubultra.store

import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation

/**
 * CAR-57: WebView cookies are shared across Steam profiles.
 * Only an explicitly scoped Steam ID64 may receive a resync.
 * This gate executes before any account upsert or library replacement.
 */
internal enum class SteamSyncAuthorization {
    NEW_CONNECTION,
    MATCHED_ACCOUNT,
    ACCOUNT_MISMATCH,
    INVALID_SESSION,
    UNVERIFIABLE_TARGET
}

internal object SteamMultiAccountSyncGuard {
    fun authorize(
        expectedSteamId: String?,
        authenticatedSteamId: String
    ): SteamSyncAuthorization {
        if (!GameAccountValidation.isSteamId64(authenticatedSteamId)) {
            return SteamSyncAuthorization.INVALID_SESSION
        }
        if (expectedSteamId == null) return SteamSyncAuthorization.NEW_CONNECTION
        if (!GameAccountValidation.isSteamId64(expectedSteamId)) {
            return SteamSyncAuthorization.UNVERIFIABLE_TARGET
        }
        return if (expectedSteamId == authenticatedSteamId) {
            SteamSyncAuthorization.MATCHED_ACCOUNT
        } else {
            SteamSyncAuthorization.ACCOUNT_MISMATCH
        }
    }
}
