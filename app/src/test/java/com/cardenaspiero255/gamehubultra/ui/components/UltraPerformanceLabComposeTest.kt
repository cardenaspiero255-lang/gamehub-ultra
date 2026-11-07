package com.cardenaspiero255.gamehubultra.ui.components

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkRunner
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkContext
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkRun
import com.cardenaspiero255.gamehubultra.domain.SyntheticBenchmarkScenario
import com.cardenaspiero255.gamehubultra.data.SyntheticBenchmarkStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UltraPerformanceLabComposeTest {
    @get:Rule val rule = createComposeRule()

    @Before fun clearStore() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("gamehub_ultra_synthetic_benchmarks", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun measuredSyntheticScenarioShowsMedianAndNeverPromisesRealGameFps() {
        var tick = 0L
        val runner = SyntheticBenchmarkRunner(
            clockNanos = { tick += 20_000L; tick },
            workload = {}
        )
        rule.setContent {
            UltraPerformanceLabCard(
                selectedProfileName = "BALANCED",
                runnerFactory = { runner }
            )
        }
        rule.onNodeWithText("EJECUTAR PRUEBA").performClick()
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText("Mediana", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Prueba sintética: no mide FPS de juegos.", substring = true).assertExists()
        rule.onNodeWithText("LIMPIAR RESULTADOS").performClick()
        rule.waitUntil(timeoutMillis = 8_000) {
            rule.onAllNodesWithText("Mediana", substring = true).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Sin mediciones locales todavía.").assertExists()
    }

    @Test fun withoutAnyMeasurementsProvidesNoFabricatedBeforeAfterComparison() {
        rule.setContent {
            UltraPerformanceLabCard(selectedProfileName = "BALANCED")
        }
        rule.onNodeWithText("Sin mediciones locales todavía.").assertExists()
        rule.onNodeWithText("Comparación real: sin datos suficientes.", substring = true).assertExists()
    }

    private fun seededRun(profile: String, sample: Long, captured: Long) =
        SyntheticBenchmarkRun(
            benchmarkContext = SyntheticBenchmarkContext(
                deviceModel = "ui-test",
                sdkInt = 35,
                cpuCores = 8,
                appVersion = "0.3.0",
                scenario = SyntheticBenchmarkScenario.CPU_INTEGER_V1,
                workloadIterations = 250_000
            ),
            profileName = profile,
            sampleNanos = listOf(sample - 2, sample - 1, sample, sample + 1, sample + 2),
            capturedAtMillis = captured
        )

    @Test fun persistedStableFasterScenarioDoesNotClaimGameFps() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SyntheticBenchmarkStore(context)
        store.append(seededRun("BALANCED", 200, 100))
        store.append(seededRun("X4", 100, 200))
        rule.setContent { UltraPerformanceLabCard(selectedProfileName = "X4") }
        rule.onNodeWithText("Carga sintética más rápida:", substring = true).assertExists()
        rule.onNodeWithText("Prueba sintética: no mide FPS de juegos.", substring = true).assertExists()
    }

    @Test fun persistedStableSlowerScenarioShowsSyntheticWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SyntheticBenchmarkStore(context)
        store.append(seededRun("BALANCED", 100, 100))
        store.append(seededRun("X4", 200, 200))
        rule.setContent { UltraPerformanceLabCard(selectedProfileName = "X4") }
        rule.onNodeWithText("Carga sintética más lenta:", substring = true).assertExists()
    }

}
