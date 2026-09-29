package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GameSelectionStateRepositoryTest {
    @Test
    fun selectedGameProfileWinsOverGlobalProfile() = runBlocking {
        val repository = FakeSelectionRepository(
            globalProfile = PerformanceProfile.FRAME_INTERPOLATION,
            gameProfiles = mapOf("com.example.game" to PerformanceProfile.X4),
        )

        assertEquals(
            PerformanceProfile.X4,
            repository.effectiveProfileForSelection("com.example.game"),
        )
    }

    @Test
    fun missingGameProfileFallsBackToGlobalProfile() = runBlocking {
        val repository = FakeSelectionRepository(
            globalProfile = PerformanceProfile.FRAME_INTERPOLATION,
            gameProfiles = emptyMap(),
        )

        assertEquals(
            PerformanceProfile.FRAME_INTERPOLATION,
            repository.effectiveProfileForSelection("com.example.game"),
        )
    }

    @Test
    fun noSelectedGameUsesGlobalProfile() = runBlocking {
        val repository = FakeSelectionRepository(
            globalProfile = PerformanceProfile.BALANCED,
            gameProfiles = mapOf("com.example.game" to PerformanceProfile.X4),
        )

        assertEquals(
            PerformanceProfile.BALANCED,
            repository.effectiveProfileForSelection(null),
        )
    }

    private class FakeSelectionRepository(
        private val globalProfile: PerformanceProfile,
        private val gameProfiles: Map<String, PerformanceProfile>,
    ) : GameSelectionStateRepository {
        override fun selectedProfileFlow(): Flow<PerformanceProfile> = flowOf(globalProfile)
        override fun selectedGameFlow(): Flow<String?> = flowOf(null)

        override fun profileForGameFlow(packageName: String): Flow<PerformanceProfile?> =
            flowOf(gameProfiles[packageName])

        override fun gameProfileConfigFlow(packageName: String): Flow<GameProfileConfig?> =
            flowOf(gameProfiles[packageName]?.let { GameProfileConfig(performanceProfile = it) })

        override suspend fun saveSelectedProfile(profile: PerformanceProfile) = Unit
        override suspend fun saveSelectedGame(packageName: String) = Unit
        override suspend fun saveSelectedGameAndProfile(
            packageName: String,
            profile: PerformanceProfile,
        ) = Unit

        override suspend fun saveProfileForGame(
            packageName: String,
            profile: PerformanceProfile,
        ) = Unit

        override suspend fun saveGameProfileConfig(
            packageName: String,
            config: GameProfileConfig,
        ) = Unit
    }
}
