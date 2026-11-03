package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform

/**
 * CAR-57: account-scoped cache visibility is unrelated to Steam WebView cookies.
 * Persisted library metadata is never proof of a live authenticated session.
 */
internal enum class SteamAccountSessionState {
    DISCONNECTED,
    UNVERIFIED,
    REAUTHENTICATION_REQUIRED
}

internal object SteamAccountVisibilityPolicy {
    private const val MAX_CACHE_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun visibleGames(
        games: List<StoreLibraryGame>,
        activeAccountId: String?
    ): List<StoreLibraryGame> = games.filter { game ->
        game.platform != GamePlatform.STEAM ||
            (!activeAccountId.isNullOrBlank() && activeAccountId == game.accountId)
    }

    fun sessionState(
        lastSyncedAtMillis: Long?,
        hasCachedGames: Boolean,
        nowMillis: Long
    ): SteamAccountSessionState = when {
        !hasCachedGames -> SteamAccountSessionState.DISCONNECTED
        lastSyncedAtMillis != null &&
            lastSyncedAtMillis > 0 &&
            lastSyncedAtMillis <= nowMillis &&
            nowMillis - lastSyncedAtMillis > MAX_CACHE_AGE_MILLIS ->
            SteamAccountSessionState.REAUTHENTICATION_REQUIRED
        else -> SteamAccountSessionState.UNVERIFIED
    }

    fun statusText(state: SteamAccountSessionState): String = when (state) {
        SteamAccountSessionState.DISCONNECTED ->
            "Sin biblioteca Steam local; inicia sesión para sincronizar."
        SteamAccountSessionState.UNVERIFIED ->
            "Biblioteca Steam en caché; sesión no verificada."
        SteamAccountSessionState.REAUTHENTICATION_REQUIRED ->
            "Biblioteca Steam antigua; vuelve a iniciar sesión para actualizarla."
    }
}
