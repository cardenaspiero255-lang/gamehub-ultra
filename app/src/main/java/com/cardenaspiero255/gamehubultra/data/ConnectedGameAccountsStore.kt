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
    val publicId: String
)

class ConnectedGameAccountsStore(context: Context) {
    private val dataStore = context.applicationContext.connectedAccountsDataStore
    private val accountsKey = stringPreferencesKey("accounts_v1")

    fun accountsFlow(): Flow<List<ConnectedGameAccount>> =
        dataStore.data.map { preferences ->
            preferences[accountsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .toList()
        }

    suspend fun add(
        platform: GamePlatform,
        displayName: String,
        publicId: String
    ): ConnectedGameAccount {
        val account = ConnectedGameAccount(
            id = UUID.randomUUID().toString(),
            platform = platform,
            displayName = displayName.trim(),
            publicId = publicId.trim()
        )
        dataStore.edit { preferences ->
            val current = preferences[accountsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .toMutableList()
            current += account
            preferences[accountsKey] = current.joinToString("\n", transform = ::encode)
        }
        return account
    }

    suspend fun remove(accountId: String) {
        dataStore.edit { preferences ->
            val current = preferences[accountsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filterNot { it.id == accountId }
            preferences[accountsKey] = current.joinToString("\n", transform = ::encode)
        }
    }

    private fun encode(account: ConnectedGameAccount): String =
        listOf(
            account.id,
            account.platform.name,
            account.displayName,
            account.publicId
        ).joinToString("|") { Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8)) }

    private fun decode(line: String): ConnectedGameAccount? = runCatching {
        val fields = line.split("|")
        if (fields.size != 4) return null
        ConnectedGameAccount(
            id = decodeField(fields[0]),
            platform = GamePlatform.valueOf(decodeField(fields[1])),
            displayName = decodeField(fields[2]),
            publicId = decodeField(fields[3])
        )
    }.getOrNull()

    private fun decodeField(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}
