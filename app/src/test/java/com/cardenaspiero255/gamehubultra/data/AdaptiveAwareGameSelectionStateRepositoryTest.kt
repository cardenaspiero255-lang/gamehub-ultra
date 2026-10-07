package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveAwareGameSelectionStateRepositoryTest {
    @Test
    fun explicitGameProfileWritesNotifyAdaptiveOwnershipAfterPersistence() = runBlocking {
        val delegate = RecordingSelectionRepository()
        val observed = mutableListOf<Pair<String, PerformanceProfile>>()
        val repository = AdaptiveAwareGameSelectionStateRepository(
            delegate = delegate,
            onExplicitGameProfileSelection = { packageName, profile ->
                assertTrue(delegate.persistedProfiles[packageName] == profile)
                observed += packageName to profile
            }
        )

        repository.saveSelectedGameAndProfile("game.a", PerformanceProfile.BALANCED)
        repository.saveProfileForGame("game.b", PerformanceProfile.X4)
        repository.saveGameProfileConfig(
            "game.c",
            GameProfileConfig(performanceProfile = PerformanceProfile.FRAME_INTERPOLATION)
        )

        assertEquals(
            listOf(
                "game.a" to PerformanceProfile.BALANCED,
                "game.b" to PerformanceProfile.X4,
                "game.c" to PerformanceProfile.FRAME_INTERPOLATION
            ),
            observed
        )
    }

    @Test
    fun automaticAdaptiveWriteDoesNotClearItsOwnRecoveryOwnership() = runBlocking {
        val delegate = RecordingSelectionRepository()
        val observed = mutableListOf<Pair<String, PerformanceProfile>>()
        val repository = AdaptiveAwareGameSelectionStateRepository(
            delegate = delegate,
            onExplicitGameProfileSelection = { packageName, profile ->
                observed += packageName to profile
            }
        )

        repository.saveAdaptiveProfileForGame("game.a", PerformanceProfile.BALANCED)

        assertEquals(PerformanceProfile.BALANCED, delegate.persistedProfiles["game.a"])
        assertTrue(observed.isEmpty())
    }

    private class RecordingSelectionRepository : GameSelectionStateRepository {
        val persistedProfiles = mutableMapOf<String, PerformanceProfile>()

        override fun selectedProfileFlow(): Flow<PerformanceProfile> =
            flowOf(PerformanceProfile.BALANCED)

        override fun selectedGameFlow(): Flow<String?> = flowOf(null)

        override fun profileForGameFlow(packageName: String): Flow<PerformanceProfile?> =
            flowOf(persistedProfiles[packageName])

        override fun gameProfileConfigFlow(packageName: String): Flow<GameProfileConfig?> =
            flowOf(
                persistedProfiles[packageName]?.let {
                    GameProfileConfig(performanceProfile = it)
                }
            )

        override suspend fun saveSelectedProfile(profile: PerformanceProfile) = Unit
        override suspend fun saveSelectedGame(packageName: String) = Unit

        override suspend fun saveSelectedGameAndProfile(
            packageName: String,
            profile: PerformanceProfile
        ) {
            persistedProfiles[packageName] = profile
        }

        override suspend fun saveProfileForGame(
            packageName: String,
            profile: PerformanceProfile
        ) {
            persistedProfiles[packageName] = profile
        }

        override suspend fun saveGameProfileConfig(
            packageName: String,
            config: GameProfileConfig
        ) {
            persistedProfiles[packageName] = config.performanceProfile
        }

        override suspend fun saveAdaptiveProfileForGame(
            packageName: String,
            profile: PerformanceProfile
        ) {
            persistedProfiles[packageName] = profile
        }
    }
}
