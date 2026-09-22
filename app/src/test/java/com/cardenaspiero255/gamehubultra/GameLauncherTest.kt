package com.cardenaspiero255.gamehubultra

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameLauncherTest {
    @Test
    fun nullIntentDoesNotLaunch() {
        val context = RecordingContext()

        assertFalse(GameLauncher.launchIntent(context, null))
        assertFalse(context.started)
    }

    @Test
    fun successfulLaunchReturnsTrue() {
        val context = RecordingContext()

        assertTrue(GameLauncher.launchIntent(context, Intent(Intent.ACTION_MAIN)))
        assertTrue(context.started)
    }

    @Test
    fun activityNotFoundDoesNotCrash() {
        val context = RecordingContext(throwOnStart = ActivityNotFoundException())

        assertFalse(GameLauncher.launchIntent(context, Intent(Intent.ACTION_MAIN)))
        assertFalse(context.started)
    }

    @Test
    fun permissionFailureDoesNotCrash() {
        val context = RecordingContext(throwOnStart = SecurityException("blocked"))

        assertFalse(GameLauncher.launchIntent(context, Intent(Intent.ACTION_MAIN)))
        assertFalse(context.started)
    }

    private class RecordingContext(
        private val throwOnStart: RuntimeException? = null
    ) : ContextWrapper(null) {
        var started: Boolean = false

        override fun startActivity(intent: Intent) {
            if (throwOnStart != null) throw throwOnStart
            started = true
        }
    }
}
