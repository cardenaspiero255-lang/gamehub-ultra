package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

private val Context.connectedAccountsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gamehub_ultra_accounts",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class ConnectedGameAccount(
    val id: String,
    val platform: GamePlatform,
    val displayName: String,
    val publicId: String,
    val alias: String? = null,
    val avatarUrl: String? = null,
    val lastSyncedAtMillis: Long? = null
)

class ConnectedGameAccountsStore(context: Context) {
    private val dataStore = context.applicationContext.connectedAccountsDataStore
    private val accountsKey = stringPreferencesKey("accounts_v2")
    private val legacyAccountsKey = stringPreferencesKey("accounts_v1")
    private val activeAccountKey = stringPreferencesKey("active_account_id_v1")

    fun accountsFlow(): Flow<List<ConnectedGameAccount>> =
        dataStore.data.map { preferences ->
            readAccounts(preferences)
        }

    fun activeAccountIdFlow(): Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[activeAccountKey]
                ?.takeIf { id -> readAccounts(preferences).any { it.id == id } }
        }

    suspend fun setActiveAccount(accountId: String?): Boolean {
        var accepted = false
        dataStore.edit { preferences ->
            val accounts = readAccounts(preferences)
            when {
                accountId == null -> {
                    preferences.remove(activeAccountKey)
                    accepted = true
                }
                accounts.any { it.id == accountId } -> {
                    preferences[activeAccountKey] = accountId
                    accepted = true
                }
                else -> Unit
            }
        }
        return accepted
    }

    suspend fun upsert(
        platform: GamePlatform,
        displayName: String,
        publicId: String,
        alias: String? = null,
        avatarUrl: String? = null
    ): ConnectedGameAccount {
        val normalizedName = displayName.trim().ifBlank { publicId.trim() }
        val normalizedId = publicId.trim()
        var result: ConnectedGameAccount? = null
        val syncTimestamp = System.currentTimeMillis()

        dataStore.edit { preferences ->
            val current = readAccounts(preferences).toMutableList()
            val index = current.indexOfFirst {
                it.platform == platform &&
                    it.publicId.equals(normalizedId, ignoreCase = true)
            }
            val previous = index.takeIf { it >= 0 }?.let(current::get)
            val account = previous?.copy(
                displayName = normalizedName,
                publicId = normalizedId,
                alias = alias?.trim()?.takeIf(String::isNotBlank) ?: previous.alias,
                avatarUrl = sanitizePublicUrl(avatarUrl) ?: previous.avatarUrl,
                lastSyncedAtMillis = syncTimestamp
            ) ?: ConnectedGameAccount(
                id = UUID.randomUUID().toString(),
                platform = platform,
                displayName = normalizedName,
                publicId = normalizedId,
                alias = alias?.trim()?.takeIf(String::isNotBlank),
                avatarUrl = sanitizePublicUrl(avatarUrl),
                lastSyncedAtMillis = syncTimestamp
            )
            if (index >= 0) current[index] = account else current += account
            result = account
            preferences[accountsKey] = current.joinToString("\n", transform = ::encode)
            if (preferences[activeAccountKey] == null) {
                preferences[activeAccountKey] = account.id
            }
            preferences.remove(legacyAccountsKey)
        }
        return requireNotNull(result)
    }

    suspend fun add(
        platform: GamePlatform,
        displayName: String,
        publicId: String,
        alias: String? = null,
        avatarUrl: String? = null
    ): ConnectedGameAccount =
        upsert(platform, displayName, publicId, alias, avatarUrl)

    suspend fun updatePublicMetadata(
        accountId: String,
        alias: String?,
        avatarUrl: String?
    ): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            val current = readAccounts(preferences).map { account ->
                if (account.id == accountId) {
                    updated = true
                    account.copy(
                        alias = alias?.trim()?.takeIf(String::isNotBlank),
                        avatarUrl = sanitizePublicUrl(avatarUrl)
                    )
                } else account
            }
            if (updated) {
                preferences[accountsKey] = current.joinToString("\n", transform = ::encode)
            }
        }
        return updated
    }

    suspend fun remove(accountId: String) {
        dataStore.edit { preferences ->
            val current = readAccounts(preferences).filterNot { it.id == accountId }
            preferences[accountsKey] = current.joinToString("\n", transform = ::encode)
            if (preferences[activeAccountKey] == accountId) {
                val replacement = current.firstOrNull()?.id
                if (replacement != null) preferences[activeAccountKey] = replacement
                else preferences.remove(activeAccountKey)
            }
        }
    }

    private fun readAccounts(preferences: Preferences): List<ConnectedGameAccount> {
        val source = preferences[accountsKey]
            ?: preferences[legacyAccountsKey]
            ?: return emptyList()
        return source
            .lineSequence()
            .mapNotNull(::decode)
            .distinctBy(ConnectedGameAccount::id)
            .toList()
    }

    private fun encode(account: ConnectedGameAccount): String =
        listOf(
            account.id,
            account.platform.name,
            account.displayName,
            account.publicId,
            account.alias.orEmpty(),
            account.avatarUrl.orEmpty(),
            account.lastSyncedAtMillis?.toString().orEmpty()
        ).joinToString("|") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }

    private fun decode(line: String): ConnectedGameAccount? = runCatching {
        val fields = line.split("|")
        if (fields.size != 4 && fields.size != 7) return null
        ConnectedGameAccount(
            id = decodeField(fields[0]),
            platform = GamePlatform.valueOf(decodeField(fields[1])),
            displayName = decodeField(fields[2]),
            publicId = decodeField(fields[3]),
            alias = if (fields.size == 7) decodeField(fields[4]).takeIf(String::isNotBlank) else null,
            avatarUrl = if (fields.size == 7) sanitizePublicUrl(decodeField(fields[5])) else null,
            lastSyncedAtMillis = if (fields.size == 7) {
                decodeField(fields[6]).toLongOrNull()
            } else null
        )
    }.getOrNull()

    private fun decodeField(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)

    private fun sanitizePublicUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
    }
}
