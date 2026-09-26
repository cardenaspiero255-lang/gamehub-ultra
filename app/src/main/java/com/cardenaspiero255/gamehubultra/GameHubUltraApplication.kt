package com.cardenaspiero255.gamehubultra

import android.app.Application
import io.sentry.android.core.SentryAndroid

class GameHubUltraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) return

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            BuildConfig.SENTRY_RELEASE
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { options.release = it }
            options.tracesSampleRate = 0.10
            options.isEnableAutoSessionTracking = true
        }
    }
}
