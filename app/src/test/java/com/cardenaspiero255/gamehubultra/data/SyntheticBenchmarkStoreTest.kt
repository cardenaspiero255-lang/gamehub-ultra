package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkContext
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkRun
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkScenario
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyntheticBenchmarkStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("gamehub_ultra_synthetic_benchmarks", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun run(timestamp: Long, profile: String = "BALANCED") = SyntheticBenchmarkRun(
        benchmarkContext = SyntheticBenchmarkContext(
            deviceModel = "test-device",
            sdkInt = 35,
            cpuCores = 8,
            appVersion = "1.0",
            scenario = SyntheticBenchmarkScenario.CPU_INTEGER_V1,
            workloadIterations = 20_000
        ),
        profileName = profile,
        sampleNanos = listOf(1000, 995, 1005, 1002, 998),
        capturedAtMillis = timestamp
    )

    @Test fun savesVersionedContextAndSampleRunsAcrossStoreRecreation() {
        val store = SyntheticBenchmarkStore(context)
        store.append(run(100, "BALANCED"))
        store.append(run(200, "X4"))
        val recovered = SyntheticBenchmarkStore(context).readAll()
        assertEquals(2, recovered.size)
        assertEquals(listOf("BALANCED", "X4"), recovered.map { it.profileName })
        assertEquals(20_000, recovered.single { it.profileName == "X4" }.benchmarkContext.workloadIterations)
        assertEquals(5, recovered.first().sampleNanos.size)
    }

    @Test fun retentionDropsOldestRunsAndKeepsLastTwelve() {
        val store = SyntheticBenchmarkStore(context)
        (1L..17L).forEach { store.append(run(it)) }
        val all = SyntheticBenchmarkStore(context).readAll()
        assertEquals(12, all.size)
        assertEquals(6L, all.first().capturedAtMillis)
        assertEquals(17L, all.last().capturedAtMillis)
    }

    @Test fun corruptedStoredJsonFailsClosedAndCanRecoverOnNextWrite() {
        context.getSharedPreferences("gamehub_ultra_synthetic_benchmarks", Context.MODE_PRIVATE)
            .edit().putString("history_v1", "{invalid json").commit()
        val store = SyntheticBenchmarkStore(context)
        assertTrue(store.readAll().isEmpty())
        store.append(run(42L))
        assertEquals(42L, SyntheticBenchmarkStore(context).readAll().single().capturedAtMillis)
    }

    @Test fun invalidProfileAndSamplesAreNeverStoredOrRestored() {
        val store = SyntheticBenchmarkStore(context)
        store.append(run(1L))
        store.append(run(2L).copy(profileName = "refresh_token=SECRET"))
        store.append(run(3L).copy(sampleNanos = listOf(-1, 1, 2, 3, 4)))
        assertEquals(listOf(1L), SyntheticBenchmarkStore(context).readAll().map { it.capturedAtMillis })
    }

    @Test fun clearingHistoryActuallyRemovesAllPreviousRuns() {
        val store = SyntheticBenchmarkStore(context)
        store.append(run(1L))
        store.clear()
        assertTrue(SyntheticBenchmarkStore(context).readAll().isEmpty())
    }
}
