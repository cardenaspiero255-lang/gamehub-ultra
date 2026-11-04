package com.cardenaspiero255.gamehubultra.store

/**
 * Epic authentication is not persisted. A cache timestamp only indicates when
 * metadata was last downloaded; it cannot establish a currently valid login.
 */
internal object EpicConnectionHealth {
    private const val OLD_CACHE_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun statusText(
        lastSyncedAtMillis: Long?,
        hasCachedGames: Boolean,
        nowMillis: Long
    ): String = when {
        !hasCachedGames ->
            "Epic: sin biblioteca local; inicia sesión para sincronizar."
        lastSyncedAtMillis != null &&
            lastSyncedAtMillis > 0L &&
            lastSyncedAtMillis <= nowMillis &&
            nowMillis - lastSyncedAtMillis > OLD_CACHE_MILLIS ->
            "Epic: caché local antigua; vuelve a iniciar sesión para actualizar."
        else -> "Epic: biblioteca local en caché; sesión no verificada."
    }
}
