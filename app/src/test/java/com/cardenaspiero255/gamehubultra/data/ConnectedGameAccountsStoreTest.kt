package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.nio.charset.StandardCharsets
import java.util.Base64
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectedGameAccountsStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: ConnectedGameAccountsStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-accounts-", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        store = ConnectedGameAccountsStore(dataStore)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun supportsMultipleAccountsAndActiveSelection() = runBlocking {
        val steam = store.add(
            GamePlatform.STEAM,
            "Piero",
            "76561198000000001",
            alias = "PieroMain",
            avatarUrl = "https://example.com/piero.png"
        )
        val epic = store.add(
            GamePlatform.EPIC_GAMES,
            "Piero Epic",
            "piero-epic"
        )
        assertTrue(store.setActiveAccount(epic.id))
        assertEquals(epic.id, store.activeAccountIdFlow().first())
        assertEquals(2, store.accountsFlow().first().size)
        assertEquals("PieroMain", store.accountsFlow().first().first { it.id == steam.id }.alias)
    }

    @Test
    fun removePromotesAnotherAccountAndRejectsUnknownActiveId() = runBlocking {
        val first = store.add(GamePlatform.STEAM, "One", "76561198000000002")
        val second = store.add(GamePlatform.STEAM, "Two", "76561198000000003")
        assertFalse(store.setActiveAccount("missing"))
        assertTrue(store.setActiveAccount(first.id))
        store.remove(first.id)
        assertEquals(second.id, store.activeAccountIdFlow().first())
    }

    @Test
    fun publicAvatarRejectsNonHttpUrls() = runBlocking {
        val account = store.add(
            GamePlatform.STEAM,
            "Name",
            "76561198000000004",
            avatarUrl = "file:///secret"
        )
        assertEquals(null, account.avatarUrl)
    }

    @Test
    fun rejectsBlankPublicIdWithoutPersistingAccount() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            store.add(
                GamePlatform.STEAM,
                "Invalid account",
                "   "
            )
        }

        assertTrue(store.accountsFlow().first().isEmpty())
        assertEquals(null, store.activeAccountIdFlow().first())
    }


    @Test
    fun resyncClearsPublicMetadataNoLongerProvidedBySource() = runBlocking {
        val original = store.add(
            GamePlatform.STEAM,
            "Piero",
            "76561198000000005",
            alias = "Old alias",
            avatarUrl = "https://example.com/old.png"
        )

        val refreshed = store.upsert(
            GamePlatform.STEAM,
            "Piero",
            "76561198000000005",
            alias = null,
            avatarUrl = null
        )

        assertEquals(original.id, refreshed.id)
        assertEquals(null, refreshed.alias)
        assertEquals(null, refreshed.avatarUrl)
        assertEquals(null, store.accountsFlow().first().single().alias)
        assertEquals(null, store.accountsFlow().first().single().avatarUrl)
    }



    @Test fun malformedLegacyRecordsCannotBecomeVisibleOrActive() = runBlocking {
        fun encoded(vararg fields: String): String = fields.joinToString("|") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }
        val valid = encoded("legacy-id", "STEAM", "Legacy Steam", "76561198000000001")
        val blankId = encoded("", "STEAM", "Ghost", "76561198000000002")
        val emptyPublic = encoded("ghost-id", "EPIC_GAMES", "Ghost", "")
        val badPlatform = encoded("invalid-id", "UNKNOWN", "Ghost", "something")
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("accounts_v1")] = listOf(
                valid, blankId, emptyPublic, badPlatform, "not-base64"
            ).joinToString("\n")
            prefs[stringPreferencesKey("active_account_id_v1")] = "ghost-id"
        }
        assertEquals(listOf("legacy-id"), store.accountsFlow().first().map { it.id })
        assertEquals(null, store.activeAccountIdFlow().first())
        val current = store.add(GamePlatform.STEAM, "New Steam", "76561198000000003")
        assertEquals(2, store.accountsFlow().first().size)
        assertTrue(store.accountsFlow().first().any { it.id == current.id })
        assertEquals(null, dataStore.data.first()[stringPreferencesKey("accounts_v1")])
    }

    @Test fun publicAvatarCannotEmbedHttpCredentialsOrSignedSecretParameters() = runBlocking {
        val first = store.add(
            GamePlatform.STEAM, "One", "76561198000000006",
            avatarUrl = "https://user:password@example.test/avatar.png"
        )
        val second = store.add(
            GamePlatform.STEAM, "Two", "76561198000000007",
            avatarUrl = "https://example.test/avatar.png?access_token=SECRET"
        )
        val third = store.add(
            GamePlatform.STEAM, "Three", "76561198000000008",
            avatarUrl = "https://example.test/avatar.png#private-fragment"
        )
        assertEquals(null, first.avatarUrl)
        assertEquals(null, second.avatarUrl)
        assertEquals(null, third.avatarUrl)
    }


}
