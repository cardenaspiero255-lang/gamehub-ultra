package com.cardenaspiero255.gamehubultra.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionLifecycleCoordinatorTest {
    private lateinit var file: File
    private lateinit var dispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher
    private lateinit var scope: CoroutineScope
    private lateinit var store: GameSessionStore
    private lateinit var coordinator: GameSessionLifecycleCoordinator

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-lifecycle-", ".preferences_pb")
        file.delete()
        dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        store = GameSessionStore(dataStore, maxSessions = 5)
        coordinator = GameSessionLifecycleCoordinator(
            store = store,
            scope = scope,
            dispatcher = dispatcher
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dispatcher.close()
        file.delete()
    }

    @Test
    fun publishesRuntimeSessionOnlyAfterStartIsPersisted() = runBlocking {
        val gate = CountDownLatch(1)
        scope.launch {
            gate.await()
        }

        coordinator.startSession(
            GameSessionRecord(
                id = "s1",
                packageName = "game.one",
                profileName = "X4",
                startedAtMillis = 1_000L
            )
        )

        assertNull(coordinator.runtimeSession.value)

        gate.countDown()
        coordinator.awaitIdle()

        assertEquals("s1", coordinator.runtimeSession.value?.id)
        assertEquals("s1", store.sessionsFlow().first().single().id)
    }

    @Test
    fun rapidFinishAndReplacementCannotLeaveOldSessionActive() = runBlocking {
        coordinator.startSession(
            GameSessionRecord(
                id = "s1",
                packageName = "game.one",
                profileName = "X4",
                startedAtMillis = 1_000L
            )
        )
        val finish = coordinator.finishCurrent(
            SessionEndMetrics(endedAtMillis = 2_000L)
        )
        assertEquals("s1", finish?.session?.id)
        coordinator.startSession(
            GameSessionRecord(
                id = "s2",
                packageName = "game.two",
                profileName = "BALANCED",
                startedAtMillis = 3_000L
            )
        )

        coordinator.awaitIdle()

        val sessions = store.sessionsFlow().first().associateBy { it.id }
        assertFalse(sessions.getValue("s1").isActive)
        assertTrue(sessions.getValue("s2").isActive)
        assertEquals("s2", coordinator.runtimeSession.value?.id)
    }

    @Test
    fun duplicateFinishIsRejectedWhilePersistenceIsPending() = runBlocking {
        coordinator.startSession(
            GameSessionRecord(
                id = "s1",
                packageName = "game.one",
                profileName = "X4",
                startedAtMillis = 1_000L
            )
        )
        coordinator.awaitIdle()
        assertEquals("s1", coordinator.runtimeSession.value?.id)

        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        scope.launch {
            blockerStarted.countDown()
            releaseBlocker.await()
        }
        blockerStarted.await()

        val first = coordinator.finishCurrent(
            SessionEndMetrics(endedAtMillis = 2_000L)
        )
        val duplicate = coordinator.finishCurrent(
            SessionEndMetrics(endedAtMillis = 2_100L)
        )

        assertEquals("s1", first?.session?.id)
        assertNull(duplicate)

        releaseBlocker.countDown()
        coordinator.awaitIdle()
        assertNull(coordinator.runtimeSession.value)
    }

}
