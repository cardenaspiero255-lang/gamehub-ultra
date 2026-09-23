package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import android.os.SystemClock
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

    private fun findNavigationTarget(
        description: String,
        visibleTexts: List<String>
    ): androidx.test.uiautomator.UiObject2? {
        val selectors = buildList {
            add(By.res("com.cardenaspiero255.gamehubultra:id/$description"))
            add(By.desc(description))
            visibleTexts.forEach { text ->
                add(By.text(text))
                add(By.textContains(text))
            }
        }

        val timeoutMs = 8_000L
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            device.waitForIdle()

            for (selector in selectors) {
                val remainingMs = deadline - SystemClock.uptimeMillis()
                if (remainingMs <= 0L) break
                device.wait(Until.findObject(selector), minOf(750L, remainingMs))?.let { return it }
            }

            SystemClock.sleep(250L)
        }
        return null
    }

    private fun clickNavigationTarget(
        description: String,
        visibleTexts: List<String>
    ) {
        findNavigationTarget(description, visibleTexts)?.let {
            check(it.click()) { "Navigation target could not be clicked: $description" }
            device.waitForIdle()
            return
        }

        // Some release builds expose a reduced accessibility tree to UiAutomator.
        // Keep the benchmark independent of that implementation detail by using
        // deterministic, orientation-aware coordinates as a last-resort fallback.
        val width = device.displayWidth
        val height = device.displayHeight
        val landscape = width >= height

        val x = when {
            description == "nav_ajustes" -> (width * 0.94f).toInt()
            landscape -> (width * 0.75f).toInt()
            else -> (width * 0.75f).toInt()
        }
        val y = when {
            description == "nav_ajustes" -> (height * 0.06f).toInt()
            landscape -> (height * 0.13f).toInt()
            else -> (height * 0.13f).toInt()
        }

        check(device.click(x, y)) {
            "Navigation coordinate fallback failed: $description at ($x,$y) on $width x $height"
        }
        device.waitForIdle()
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
            },
            measureBlock = {
                clickNavigationTarget(
                    targetDescription,
                    listOf("BIBLIOTECA", "LIBRARY")
                )
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
            },
            measureBlock = {
                clickNavigationTarget(
                    targetDescription,
                    listOf("⚙", "Ajustes", "Settings")
                )
            }
        )
    }
}
