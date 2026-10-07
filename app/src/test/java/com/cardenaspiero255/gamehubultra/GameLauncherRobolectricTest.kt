package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.session.SessionCoachMonitorService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameLauncherRobolectricTest {
    private val appContext: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearCoachStore() {
        appContext.getSharedPreferences(
            "gamehub_ultra_session_coach",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun launchStartsCoachAndThenStartsGameActivity() {
        val packageManager = mock(PackageManager::class.java)
        `when`(packageManager.getLaunchIntentForPackage("game.package"))
            .thenReturn(Intent(Intent.ACTION_MAIN))

        var activityStarted = false
        val context = object : ContextWrapper(appContext) {
            override fun getPackageManager(): PackageManager = packageManager

            override fun startActivity(intent: Intent) {
                activityStarted = true
            }
        }

        assertTrue(GameLauncher.launch(context, "game.package"))
        assertTrue(activityStarted)
        assertTrue(SessionCoachSessionStore(context).hasActiveSession())

        SessionCoachMonitorService.cancelLaunch(context)
    }

    @Test
    fun launchFailureCancelsCoachSession() {
        val packageManager = mock(PackageManager::class.java)
        `when`(packageManager.getLaunchIntentForPackage("game.package"))
            .thenReturn(Intent(Intent.ACTION_MAIN))

        val context = object : ContextWrapper(appContext) {
            override fun getPackageManager(): PackageManager = packageManager

            override fun startActivity(intent: Intent) {
                throw SecurityException("blocked")
            }
        }

        assertFalse(GameLauncher.launch(context, "game.package"))
        assertFalse(SessionCoachSessionStore(context).hasActiveSession())
    }
}
