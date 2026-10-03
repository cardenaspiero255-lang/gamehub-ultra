package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cardenaspiero255.gamehubultra.domain.DEFAULT_ULTRA_PLAYER_NAME
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerIdentityPersistenceTest {
    @Test
    fun playerNameDefaultsAndPersistsNormalizedValue() = runBlocking {
        val file = File.createTempFile("gamehub-player-", ".preferences_pb").apply { delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )

        try {
            val repository = GameHubPreferencesRepository(store)

            assertEquals(DEFAULT_ULTRA_PLAYER_NAME, repository.playerNameFlow().first())

            repository.savePlayerName("   Piero     Ultra   ")

            assertEquals("Piero Ultra", repository.playerNameFlow().first())
        } finally {
            scope.cancel()
            file.delete()
            File(file.absolutePath + ".corrupt").delete()
            File(file.absolutePath + ".bak").delete()
        }
    }
}
