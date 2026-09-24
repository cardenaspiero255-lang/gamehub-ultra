package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: GameSessionStore

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-session-", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        store = GameSessionStore(dataStore, maxSessions = 3)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun createsClosesAndRestoresSession() = runBlocking {
        val started = GameSessionRecord(
            id = "s1",
            packageName = "com.example.game",
            profileName = "X4",
            startedAtMillis = 1_000L,
            startBatteryPercent = 80
        )
        store.startSession(started)
        store.finishSession("s1", 6_000L, 75, 2, 63)

        val session = store.sessionsFlow().first().single()
        assertFalse(session.isActive)
        assertEquals(5_000L, session.durationMillis)
        assertEquals(75, session.endBatteryPercent)
        assertEquals(2, session.endThermalStatus)
        assertEquals(63, session.endRamUsedPercent)
    }

    @Test
    fun truncatesToConfiguredLimitAndKeepsNewest() = runBlocking {
        repeat(5) { index ->
            store.startSession(
                GameSessionRecord(
                    id = "s$index",
                    packageName = "game.$index",
                    profileName = "BALANCED",
                    startedAtMillis = index.toLong()
                )
            )
        }
        val sessions = store.sessionsFlow().first()
        assertEquals(3, sessions.size)
        assertEquals("s4", sessions[0].id)
        assertEquals("s2", sessions[2].id)
    }

    @Test
    fun invalidPercentagesAndUnknownSessionAreSafe() = runBlocking {
        store.startSession(
            GameSessionRecord(
                id = "s1",
                packageName = "com.example.game",
                profileName = "BALANCED",
                startedAtMillis = 100L,
                startBatteryPercent = 300
            )
        )
        assertFalse(store.finishSession("missing", 200L, -1, null, 101))
        val session = store.sessionsFlow().first().single()
        assertNull(session.startBatteryPercent)
        assertTrue(store.finishSession("s1", 200L, -1, null, 101))
        val finished = store.sessionsFlow().first().single()
        assertNull(finished.endBatteryPercent)
        assertNull(finished.endRamUsedPercent)
    }
}
