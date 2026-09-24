package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectedGameAccountsStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: ConnectedGameAccountsStore

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-accounts-", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
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
}
