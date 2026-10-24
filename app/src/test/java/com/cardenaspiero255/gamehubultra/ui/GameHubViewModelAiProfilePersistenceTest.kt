package com.cardenaspiero255.gamehubultra.ui

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.GameSessionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinator
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinatorFactory
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the actual ViewModel -> repository -> DataStore persistence boundary,
 * rather than invoking a fake save callback from the UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHubViewModelAiProfilePersistenceTest {
    private lateinit var backingFile: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: GameHubPreferencesRepository

    @Before
    fun setUp() {
        backingFile = File.createTempFile("gamehub-ai-vm-", ".preferences_pb")
        backingFile.delete()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { backingFile }
        )
        repository = GameHubPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        backingFile.delete()
    }

    private val sessions = object : GameSessionStateRepository {
        override fun sessionsFlow(): Flow<List<GameSessionRecord>> = flowOf(emptyList())
        override suspend fun startSession(record: GameSessionRecord) = Unit
        override suspend fun finishSession(
            sessionId: String, endedAtMillis: Long, endBatteryPercent: Int?,
            endThermalStatus: Int?, endRamUsedPercent: Int?
        ): Boolean = true
        override suspend fun finishActiveSessions(endedAtMillis: Long): Int = 0
        override suspend fun clearSessions() = Unit
    }

    private fun model(selection: GameSelectionStateRepository): GameHubViewModel =
        GameHubViewModel(
            RuntimeEnvironment.getApplication() as Application,
            GameHubViewModelDependencies(
                selectionRepository = selection,
                libraryRepository = repository,
                performanceHistoryRepository = repository,
                sessionRepository = sessions,
                sessionCoordinatorFactory = GameSessionLifecycleCoordinatorFactory { scope ->
                    GameSessionLifecycleCoordinator(sessions, scope, Dispatchers.Unconfined)
                }
            )
        )

    private fun proposal(version: Int) = AiProfileProposal(
        version = version,
        previousKnownGoodConfig = GameProfileConfig(performanceProfile = PerformanceProfile.BALANCED),
        proposedConfig = GameProfileConfig(performanceProfile = PerformanceProfile.X4),
        requiresExplicitApply = true,
        reasons = listOf("persist ViewModel"),
        disabledSettings = emptyList()
    )

    @Test
    fun approvalAndRollbackUseAwaitedRealViewModelDataStoreWrites() = runBlocking {
        val game = "com.gamehub.vm.${UUID.randomUUID()}"
        val viewModel = model(repository)
        val checkpoint = AiProfileProposalStore(RuntimeEnvironment.getApplication())
        val persist = viewModel::persistGameProfileConfig

        assertTrue(checkpoint.applyProposal(game, proposal(2), persist))
        assertEquals(PerformanceProfile.X4, repository.gameProfileConfigFlow(game).first()?.performanceProfile)
        assertEquals(2, checkpoint.rollbackState(game)?.version)

        assertTrue(checkpoint.rollbackIfCurrent(game, 2, persist))
        assertEquals(PerformanceProfile.BALANCED, repository.gameProfileConfigFlow(game).first()?.performanceProfile)
        assertNull(checkpoint.rollbackState(game))
        assertEquals(3, checkpoint.nextVersion(game))
    }

    @Test
    fun repositoryFailurePropagatesWithoutAdvancingCheckpoint() = runBlocking {
        val game = "com.gamehub.vm.fail.${UUID.randomUUID()}"
        val broken = object : GameSelectionStateRepository by repository {
            override suspend fun saveGameProfileConfig(packageName: String, config: GameProfileConfig) {
                throw IllegalStateException("DataStore unavailable")
            }
        }
        val viewModel = model(broken)
        val checkpoint = AiProfileProposalStore(RuntimeEnvironment.getApplication())
        val failure = runCatching {
            checkpoint.applyProposal(game, proposal(2), viewModel::persistGameProfileConfig)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertNull(repository.gameProfileConfigFlow(game).first())
        assertNull(checkpoint.rollbackState(game))
        assertEquals(1, checkpoint.nextVersion(game))
    }
}
