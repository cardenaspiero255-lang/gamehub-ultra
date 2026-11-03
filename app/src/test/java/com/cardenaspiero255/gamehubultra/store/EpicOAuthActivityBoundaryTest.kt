package com.cardenaspiero255.gamehubultra.store

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercise the Android integration boundary, not only the pure OAuth parser.
 * A foreign redirect must never be consumed or yield an authorization code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpicOAuthActivityBoundaryTest {
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
