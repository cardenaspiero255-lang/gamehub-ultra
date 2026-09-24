package com.cardenaspiero255.gamehubultra.data

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OrientationPreference
import com.cardenaspiero255.gamehubultra.domain.ResolutionTarget
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import java.io.File
import java.lang.reflect.Proxy
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
import kotlin.test.assertNull

class GameHubPreferencesRepositoryTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: GameHubPreferencesRepository

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("gamehub-ultra-test-", ".preferences_pb")
        file.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        repository = GameHubPreferencesRepository(dataStore)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        deleteDataStoreFiles(file)
    }

    @Test
    fun writesAndReadsGlobalAndPerGameState() = runBlocking {
        repository.saveSelectedProfile(PerformanceProfile.FRAME_INTERPOLATION)
        repository.saveSelectedGameAndProfile("com.example.game", PerformanceProfile.X4)
        repository.saveGameProfileConfig(
            "com.example.game",
            GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                thermalPreference = ThermalPreference.PERFORMANCE,
                refreshRateTargetHz = 120,
                resolutionTarget = ResolutionTarget(1920, 1080),
                orientationPreference = OrientationPreference.LANDSCAPE
            )
        )

        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, repository.selectedProfileFlow().first())
        assertEquals("com.example.game", repository.selectedGameFlow().first())
        assertEquals(
            GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                thermalPreference = ThermalPreference.PERFORMANCE,
                refreshRateTargetHz = 120
            ),
            repository.gameProfileConfigFlow("com.example.game").first()
        )
    }

    @Test
    fun perGameProfilesStayIsolated() = runBlocking {
        repository.saveGameProfileConfig(
            "com.example.alpha",
            GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                thermalPreference = ThermalPreference.COOLER,
                refreshRateTargetHz = 144,
                resolutionTarget = ResolutionTarget(2560, 1440),
                orientationPreference = OrientationPreference.LANDSCAPE
            )
        )
        repository.saveGameProfileConfig(
            "com.example.beta",
            GameProfileConfig(
                performanceProfile = PerformanceProfile.BALANCED,
                thermalPreference = ThermalPreference.ADAPTIVE,
                refreshRateTargetHz = null
            )
        )

        assertEquals(
            PerformanceProfile.X4,
            repository.gameProfileConfigFlow("com.example.alpha").first()?.performanceProfile
        )
        assertEquals(
            ThermalPreference.ADAPTIVE,
            repository.gameProfileConfigFlow("com.example.beta").first()?.thermalPreference
        )
        assertNull(repository.gameProfileConfigFlow("com.example.unknown").first())
    }

    @Test
    fun invalidStoredProfileFallsBackToBalanced() = runBlocking {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("selected_profile")] = "INVALID_PROFILE"
        }

        assertEquals(PerformanceProfile.BALANCED, repository.selectedProfileFlow().first())
    }

    @Test
    fun invalidPerGameValuesUseSafeFallbacks() = runBlocking {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("game_profile_com.example.invalid")] = "INVALID_PROFILE"
            preferences[stringPreferencesKey("game_thermal_com.example.invalid")] = "INVALID_THERMAL"
            preferences[stringPreferencesKey("game_refresh_com.example.invalid")] = "not-a-number"
        }

        assertNull(repository.gameProfileConfigFlow("com.example.invalid").first())
    }

    @Test
    fun unsupportedResolutionAndRefreshTargetsFallBackToAuto() {
        val config = GameProfileConfig(refreshRateTargetHz = 165)

        assertEquals(165, config.resolveRefreshRateTarget(setOf(60, 120, 165)))
        assertNull(config.resolveRefreshRateTarget(setOf(60, 90, 120)))
        assertNull(config.resolveRefreshRateTarget(emptySet()))

        val resolution = ResolutionTarget(1920, 1080)
        val resolutionConfig = GameProfileConfig(resolutionTarget = resolution)
        assertEquals(resolution, resolutionConfig.resolveResolutionTarget(setOf(resolution)))
        assertNull(resolutionConfig.resolveResolutionTarget(setOf(1280.let { ResolutionTarget(it, 720) })))
    }

    @Test
    fun writesSurviveDataStoreRestart() = runBlocking {
        repository.saveSelectedProfile(PerformanceProfile.FRAME_INTERPOLATION)
        repository.saveSelectedGameAndProfile("com.example.restart", PerformanceProfile.X4)
        repository.saveGameProfileConfig(
            "com.example.restart",
            GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                thermalPreference = ThermalPreference.COOLER,
                refreshRateTargetHz = 144
            )
        )

        scope.cancel()

        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restartedStore = PreferenceDataStoreFactory.create(
                scope = restartedScope,
                produceFile = { file }
            )
            val restartedRepository = GameHubPreferencesRepository(restartedStore)

            assertEquals(
                PerformanceProfile.FRAME_INTERPOLATION,
                restartedRepository.selectedProfileFlow().first()
            )
            assertEquals("com.example.restart", restartedRepository.selectedGameFlow().first())
            assertEquals(
                GameProfileConfig(
                    performanceProfile = PerformanceProfile.X4,
                    thermalPreference = ThermalPreference.COOLER,
                    refreshRateTargetHz = 144
                ),
                restartedRepository.gameProfileConfigFlow("com.example.restart").first()
            )
        } finally {
            restartedScope.cancel()
        }
    }

    @Test
    fun performanceHistoryPersistsAndKeepsLatestEvents() = runBlocking {
        repeat(55) { index ->
            repository.appendPerformanceEvent(
                PerformanceEvent(
                    timestampMillis = index.toLong(),
                    type = PerformanceEventType.POLICY_CHANGED,
                    sessionId = "session-test",
                    profile = if (index % 2 == 0) {
                        PerformanceProfile.BALANCED
                    } else {
                        PerformanceProfile.X4
                    },
                    score = index.coerceIn(0, 100),
                    detail = "event-$index"
                )
            )
        }

        val history = repository.performanceHistoryFlow(limit = 50).first()

        assertEquals(50, history.size)
        assertEquals(5L, history.first().timestampMillis)
        assertEquals(54L, history.last().timestampMillis)
    }

    @Test
    fun legacySharedPreferencesMigrationKeepsSelections() = runBlocking {
        val legacy = proxySharedPreferences(
            mapOf(
                "selected_profile" to PerformanceProfile.FRAME_INTERPOLATION.name,
                "selected_game_package" to "com.example.legacygame"
            )
        )
        val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val migrationFile = File.createTempFile("gamehub-ultra-migration-", ".preferences_pb")
        migrationFile.delete()

        try {
            val migratedStore = PreferenceDataStoreFactory.create(
                migrations = listOf(
                    SharedPreferencesMigration(produceSharedPreferences = { legacy })
                ),
                scope = migrationScope,
                produceFile = { migrationFile }
            )
            val migratedRepository = GameHubPreferencesRepository(migratedStore)

            assertEquals(
                PerformanceProfile.FRAME_INTERPOLATION,
                migratedRepository.selectedProfileFlow().first()
            )
            assertEquals("com.example.legacygame", migratedRepository.selectedGameFlow().first())
        } finally {
            migrationScope.cancel()
            deleteDataStoreFiles(migrationFile)
        }
    }

    private fun proxySharedPreferences(values: Map<String, Any>): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAll" -> values
                "contains" -> values.containsKey(args?.firstOrNull())
                "getString" -> values[args?.getOrNull(0)] as? String
                "getStringSet" -> values[args?.getOrNull(0)] as? Set<*>
                "getBoolean" -> values[args?.getOrNull(0)] as? Boolean ?: false
                "getInt" -> values[args?.getOrNull(0)] as? Int ?: 0
                "getLong" -> values[args?.getOrNull(0)] as? Long ?: 0L
                "getFloat" -> values[args?.getOrNull(0)] as? Float ?: 0f
                "edit" -> proxySharedPreferencesEditor()
                "toString" -> "FakeSharedPreferences"
                else -> defaultValue(method.returnType)
            }
        } as SharedPreferences

    private fun proxySharedPreferencesEditor(): SharedPreferences.Editor {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "remove", "putString", "putStringSet", "putBoolean", "putInt",
                "putLong", "putFloat", "clear" -> editor
                "commit" -> true
                "apply" -> Unit
                "toString" -> "FakeSharedPreferences.Editor"
                else -> defaultValue(method.returnType)
            }
        } as SharedPreferences.Editor
        return editor
    }

    private fun defaultValue(type: Class<*>): Any? =
        when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

    private fun deleteDataStoreFiles(base: File) {
        base.delete()
        File(base.absolutePath + ".corrupt").delete()
        File(base.absolutePath + ".bak").delete()
    }
}
