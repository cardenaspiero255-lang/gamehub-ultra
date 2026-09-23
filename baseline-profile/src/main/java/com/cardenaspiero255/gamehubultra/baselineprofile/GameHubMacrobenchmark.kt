package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHubMacrobenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun prepareAppForNavigation() {
        // Release builds can surface runtime-permission/system dialogs before the
        // Compose hierarchy is exposed to UiAutomator. Grant the permissions used
        // by the app in the benchmark emulator so navigation measures the app UI,
        // not a first-run permission flow.
        device.executeShellCommand(
            "pm grant com.cardenaspiero255.gamehubultra android.permission.RECORD_AUDIO"
        )
        device.executeShellCommand(
            "pm grant com.cardenaspiero255.gamehubultra android.permission.POST_NOTIFICATIONS"
        )
        device.pressBack()
        device.waitForIdle()
    }

    private fun requireNavigationTarget(
        description: String,
        visibleTexts: List<String>
    ): androidx.test.uiautomator.UiObject2 {
        device.waitForIdle()
        val selectors = mutableListOf(
            By.res("com.cardenaspiero255.gamehubultra:id/$description"),
            By.desc(description)
        )
        visibleTexts.forEach { text ->
            selectors += By.text(text)
        }

        repeat(5) {
            selectors.forEach { selector ->
                device.wait(Until.findObject(selector), 2_000)?.let { return it }
            }
            device.waitForIdle()
        }

        error(
            "Navigation target not found after 10s: " +
                description + " / " + visibleTexts.joinToString()
        )
    }

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.cardenaspiero255.gamehubultra",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() }
    )

    @Test
    fun navigationToLibrary() {
        val targetDescription = "nav_biblioteca"
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                prepareAppForNavigation()
                startActivityAndWait()
                device.waitForIdle()
                requireNavigationTarget(targetDescription, listOf("BIBLIOTECA", "LIBRARY"))
            },
            measureBlock = {
                requireNavigationTarget(targetDescription, listOf("BIBLIOTECA", "LIBRARY")).click()
                device.waitForIdle()
            }
        )
    }

    @Test
    fun navigationToSettings() {
        val targetDescription = "nav_ajustes"
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                prepareAppForNavigation()
                startActivityAndWait()
                device.waitForIdle()
                requireNavigationTarget(targetDescription, listOf("⚙"))
            },
            measureBlock = {
                requireNavigationTarget(targetDescription, listOf("⚙")).click()
                device.waitForIdle()
            }
        )
    }
}
