package com.cardenaspiero255.gamehubultra

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Explicit opt-in Android SDK-to-Sentry delivery probe, not an app crash.
 *
 * Normal PR/CI runs never transmit telemetry: they have an empty DSN and this
 * test is skipped unless the instrumentation argument is explicitly supplied.
 * SDK capture/flush is NOT delivery proof. The event must be read back
 * using tools/sentry_e2e_verify.py from a separate, trusted context.
 */
class SentryDeliveryE2ETest {
    @Test
    fun optInCaptureAndFlushRequiresServerReadback() {
        val flags = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit sentry_e2e opt-in required", flags.getString("sentry_e2e") == "true")

        check(BuildConfig.SENTRY_DSN.isNotBlank()) { "Sentry DSN missing: cannot certify E2E" }
        val release = BuildConfig.SENTRY_RELEASE.trim()
        check(Regex("gamehub-ultra@[a-f0-9]{40}").matches(release)) {
            "Exact trusted Sentry release missing: cannot certify E2E"
        }

        // Benign synthetic message. No user, voice, token, or stacktrace data.
        val id = Sentry.captureMessage("GameHub Ultra controlled Sentry E2E probe", SentryLevel.INFO)
        val eventId = id.toString()
        assertTrue("SDK refused event", Regex("[a-f0-9]{32}").matches(eventId))
        assertTrue("SDK returned empty event ID", eventId != "0".repeat(32))
        Sentry.flush(15_000L)
        Log.i("GHU_SENTRY_E2E", "EVENT_ID=$eventId RELEASE=$release")
    }
}
