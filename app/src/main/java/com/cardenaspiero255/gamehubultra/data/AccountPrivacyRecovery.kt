package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.first

/** CAR-60: cached metadata is not proof of a valid connected provider session. */
internal enum class AccountRecoveryState { DISCONNECTED, UNVERIFIED_CACHE, REAUTH_REQUIRED }

internal object AccountRecoveryPolicy {
    private const val MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L

    fun evaluate(
        lastSyncedAtMillis: Long?,
        hasCachedGames: Boolean,
        nowMillis: Long
    ): AccountRecoveryState {
        if (!hasCachedGames) return AccountRecoveryState.DISCONNECTED
        val timestamp = lastSyncedAtMillis ?: return AccountRecoveryState.UNVERIFIED_CACHE
        return if (timestamp <= 0L || timestamp > nowMillis ||
            nowMillis - timestamp > MAX_AGE_MILLIS
        ) {
            AccountRecoveryState.REAUTH_REQUIRED
        } else {
            AccountRecoveryState.UNVERIFIED_CACHE
        }
    }
}

/**
 * Best-effort two-store removal in privacy-first order.
 *
 * Purge cached games before deleting the account mapping. If cache deletion fails,
 * keep the account so the user can retry. Neither store holds login tokens here,
 * and this operation cannot revoke a provider-side session or existing WebView cookies.
 */
internal class AccountPrivacyRecovery(
    private val accounts: ConnectedGameAccountsStateRepository,
    private val library: StoreLibraryStateRepository
) {
    suspend fun disconnect(accountId: String): Boolean {
        if (accountId.isBlank()) return false
        if (accounts.accountsFlow().first().none { it.id == accountId }) return false
        library.removeForAccount(accountId)
        accounts.remove(accountId)
        return true
    }
}

/**
 * Explicit export of non-identifying diagnostic COUNTS only.
 * Never serialize full account objects, public IDs, labels, URLs or OAuth fields.
 * This report is not a restorable backup.
 */
internal object AccountPrivacyExport {
    fun create(
        accounts: List<ConnectedGameAccount>,
        cachedGames: List<StoreLibraryGame>
    ): String {
        val lines = mutableListOf(
            "GameHub Ultra · Informe local de cuentas v1",
            "Solo cantidades. No incluye IDs, credenciales ni datos personales.",
            "La caché local no demuestra sesión autenticada."
        )
        for (platform in GamePlatform.entries) {
            val relevant = accounts.filter { it.platform == platform }
            val accountIds = relevant.map { it.id }.toSet()
            val distinctGames = cachedGames.filter {
                it.platform == platform && it.accountId in accountIds &&
                    it.platformGameId.isNotBlank()
            }.distinctBy { it.accountId to it.platformGameId }
            val accountCount = relevant.size
            val gameCount = distinctGames.size
            lines += "${platform.title}: $accountCount " +
                (if (accountCount == 1) "cuenta" else "cuentas") +
                ", $gameCount " + (if (gameCount == 1) "título" else "títulos") +
                " en caché"
        }
        return lines.joinToString("\n")
    }
}
