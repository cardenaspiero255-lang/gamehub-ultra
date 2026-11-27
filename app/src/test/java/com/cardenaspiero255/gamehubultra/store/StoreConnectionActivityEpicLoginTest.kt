package com.cardenaspiero255.gamehubultra.store

import android.net.Uri
import android.webkit.WebView
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreConnectionActivityEpicLoginTest {
    @Test
    fun epicLoginLoadsPublicOriginWithBoundRedirectAndRandomState() {
        val intent = StoreConnectionActivity.newIntent(
            RuntimeEnvironment.getApplication(),
            GamePlatform.EPIC_GAMES
        )
        val controller = Robolectric.buildActivity(
            StoreConnectionActivity::class.java,
            intent
        ).create()

        try {
            val field = StoreConnectionActivity::class.java.getDeclaredField("webView")
                .apply { isAccessible = true }
            val webView = field.get(controller.get()) as WebView
            val login = Uri.parse(webView.url.orEmpty())

            assertEquals("https", login.scheme)
            assertEquals("www.epicgames.com", login.host)
            assertEquals("/id/login", login.path)

            val state = login.getQueryParameter("state")
                ?: error("Epic login URL must include OAuth state")
            assertEquals(64, state.length)
            assertTrue(state.all { it in "0123456789abcdef" })

            val redirect = Uri.parse(login.getQueryParameter("redirectUrl").orEmpty())
            assertEquals("https", redirect.scheme)
            assertEquals("www.epicgames.com", redirect.host)
            assertEquals("/id/api/redirect", redirect.path)
            assertEquals(
                "34a02cf8f4414e29b15921876da36f9a",
                redirect.getQueryParameter("clientId")
            )
            assertEquals("code", redirect.getQueryParameter("responseType"))
        } finally {
            controller.destroy()
        }
    }
}
