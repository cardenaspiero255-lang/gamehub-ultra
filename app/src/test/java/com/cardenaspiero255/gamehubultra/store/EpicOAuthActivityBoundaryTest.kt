package com.cardenaspiero255.gamehubultra.store

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.net.URI
import java.net.URLDecoder
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercise the Android integration boundary, not only the pure OAuth parser.
 * A foreign redirect must never be consumed or yield an authorization code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpicOAuthActivityBoundaryTest {
    @Test fun configuredEpicStartupLoadsOnlyTheOwnedAuthorizationEndpoint() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Steam initializes the same WebView without touching Epic's unconfigured BuildConfig.
        val intent = StoreConnectionActivity.newIntent(context, GamePlatform.STEAM)
        val controller = Robolectric.buildActivity(StoreConnectionActivity::class.java, intent)
        try {
            val activity = controller.create().get()
            val config = EpicOAuthConfiguration(
                backendUrl = "https://api.example.test/epic/oauth/exchange",
                clientId = "abcdef0123456789abcdef0123456789",
                redirectUri = "https://accounts.example.test/oauth/epic/callback"
            )
            assertTrue(activity.startEpicAuthorization(config))
            val outer = activity.findViewById<FrameLayout>(android.R.id.content)
            val content = outer.getChildAt(0) as FrameLayout
            val webView = content.getChildAt(0) as WebView
            val loadedUrl = checkNotNull(Shadows.shadowOf(webView).lastLoadedUrl)
            val uri = URI(loadedUrl)
            val params = uri.rawQuery.split("&").associate { raw ->
                val pair = raw.split("=", limit = 2)
                pair[0] to URLDecoder.decode(pair[1], "UTF-8")
            }
            assertEquals("www.epicgames.com", uri.host)
            assertEquals("/id/authorize", uri.path)
            assertEquals(config.clientId, params["client_id"])
            assertEquals(config.redirectUri, params["redirect_uri"])
            assertTrue(params["state"].orEmpty().matches(Regex("[0-9a-f]{64}")))
            assertNull(intent.getStringExtra("access_token"))
            assertNull(intent.getStringExtra("refresh_token"))
        } finally {
            controller.destroy()
        }
    }

    @Test fun foreignCallbackIsNotConsumedAndCannotCarryCredentials() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = StoreConnectionActivity.newIntent(context, GamePlatform.EPIC_GAMES)
        val controller = Robolectric.buildActivity(StoreConnectionActivity::class.java, intent)
        try {
            val activity = controller.create().get()
            val outer = activity.findViewById<FrameLayout>(android.R.id.content)
            val content = outer.getChildAt(0) as FrameLayout
            val webView = content.getChildAt(0) as WebView
            val client = checkNotNull(webView.webViewClient)
            val foreign = Uri.parse(
                "https://untrusted.example/oauth/epic/callback?state=" +
                    "a".repeat(64) + "&code=attacker-code"
            )
            val request = Mockito.mock(WebResourceRequest::class.java)
            Mockito.`when`(request.url).thenReturn(foreign)

            assertFalse(client.shouldOverrideUrlLoading(webView, request))
            client.onPageFinished(webView, foreign.toString())
            assertNull(intent.getStringExtra("access_token"))
            assertNull(intent.getStringExtra("refresh_token"))
            assertNull(intent.getStringExtra("authorizationCode"))
        } finally {
            controller.destroy()
        }
    }
}
