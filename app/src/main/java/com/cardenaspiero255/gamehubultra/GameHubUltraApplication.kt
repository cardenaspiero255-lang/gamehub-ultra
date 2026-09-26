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
            options.tracesSampleRate = 0.10
            options.isEnableAutoSessionTracking = true
        }
    }
}
