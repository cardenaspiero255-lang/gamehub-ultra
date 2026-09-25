package com.cardenaspiero255.gamehubultra.store

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStore
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStore
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import android.util.Base64

class StoreConnectionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLATFORM = "platform"
        private const val POLL_MS = 1800L
        private const val MAX_WAIT_MS = 90_000L

        fun newIntent(context: android.content.Context, platform: GamePlatform) =
            android.content.Intent(context, StoreConnectionActivity::class.java)
                .putExtra(EXTRA_PLATFORM, platform.name)
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var steamStartedAt = 0L
    private var steamInFlight = false
    private var epicState: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = runCatching {
            GamePlatform.valueOf(intent.getStringExtra(EXTRA_PLATFORM).orEmpty())
        }.getOrNull() ?: return finishWithError("Plataforma no válida")

        val root = FrameLayout(this)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = StoreWebViewClient(platform)
        }
        progressBar = ProgressBar(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(72, 72).apply {
                gravity = android.view.Gravity.CENTER
            }
        )
        setContentView(root)

        when (platform) {
            GamePlatform.STEAM -> {
                steamStartedAt = System.currentTimeMillis()
                webView.loadUrl(
                    "https://store.steampowered.com/login/?redir=store.steampowered.com/"
                )
            }
            GamePlatform.EPIC_GAMES -> {
                val stateBytes = ByteArray(32).also {
                    java.security.SecureRandom().nextBytes(it)
                }
                val state = stateBytes.joinToString("") { "%02x".format(it) }
                epicState = state
                val redirect = Uri.encode(
                    EpicConstants.REDIRECT_URI +
                        "?clientId=" + EpicConstants.CLIENT_ID +
                        "&responseType=code"
                )
                webView.loadUrl(
                    EpicConstants.AUTH_BASE_URL + "/id/login" +
                        "?redirectUrl=" + redirect +
                        "&state=" + Uri.encode(state)
                )
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private inner class StoreWebViewClient(
        private val platform: GamePlatform
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (platform == GamePlatform.EPIC_GAMES) {
                handleEpicRedirect(request.url)
            }
            return false
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = android.view.View.GONE
            when (platform) {
                GamePlatform.STEAM -> scheduleSteamSync()
                GamePlatform.EPIC_GAMES -> handleEpicRedirect(Uri.parse(url))
            }
        }
    }

    private fun scheduleSteamSync() {
        if (finished || steamInFlight) return
        if (System.currentTimeMillis() - steamStartedAt > MAX_WAIT_MS) {
            return finishWithError("No se pudo detectar la sesión de Steam")
        }
        handler.postDelayed({
            if (finished || steamInFlight) return@postDelayed
            steamInFlight = true
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    SteamStoreClient.readAuthenticatedLibrary()
                }
                steamInFlight = false
                result.fold(
                    onSuccess = { syncSteam(it) },
                    onFailure = { scheduleSteamSync() }
                )
            }
        }, POLL_MS)
    }

    private suspend fun syncSteam(result: SteamSyncResult) {
        if (finished) return
        val account = ConnectedGameAccountsStore(this).upsert(
            GamePlatform.STEAM,
            result.displayName.ifBlank { "Steam" },
            result.steamId
        )
        val games = result.games.map {
            StoreLibraryGame(
                id = "steam:" + account.id + ":" + it.appId,
                accountId = account.id,
                platform = GamePlatform.STEAM,
                title = it.title,
                platformGameId = it.appId.toString(),
                artworkUrl = it.artworkUrl
            )
        }
        StoreLibraryStore(this).replaceForAccount(account.id, games)
        finishSuccess("Steam conectado · " + games.size + " juegos sincronizados")
    }

    private fun handleEpicRedirect(uri: Uri) {
        if (finished) return
        val expected = Uri.parse(EpicConstants.REDIRECT_URI)
        if (!uri.scheme.equals(expected.scheme, true) ||
            !uri.host.equals(expected.host, true) ||
            uri.path != expected.path
        ) return
        if (uri.getQueryParameter("state") != epicState) return

        val code = uri.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            authenticateEpic(code)
            return
        }

        webView.evaluateJavascript(
            "(function(){try{return JSON.parse(document.body&&document.body.innerText||'{}')" +
                ".authorizationCode||null}catch(e){return null}})();"
        ) { jsResult ->
            val bodyCode = unquoteJsonString(jsResult)
            if (!bodyCode.isNullOrBlank()) authenticateEpic(bodyCode)
        }
    }

    private fun authenticateEpic(code: String) {
        if (finished) return
        finished = true
        progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            runCatching {
                val credentials = withContext(Dispatchers.IO) {
                    EpicStoreClient.exchangeCode(code)
                }
                val account = withContext(Dispatchers.IO) {
                    ConnectedGameAccountsStore(this@StoreConnectionActivity).upsert(
                        GamePlatform.EPIC_GAMES,
                        credentials.displayName.ifBlank { "Epic Games" },
                        credentials.accountId
                    )
                }
                val games = withContext(Dispatchers.IO) {
                    EpicStoreClient.fetchLibrary(credentials.accessToken)
                }.map {
                    StoreLibraryGame(
                        id = "epic:" + account.id + ":" + it.appName,
                        accountId = account.id,
                        platform = GamePlatform.EPIC_GAMES,
                        title = it.title,
                        platformGameId = it.appName,
                        artworkUrl = it.artworkUrl
                    )
                }
                StoreLibraryStore(this@StoreConnectionActivity)
                    .replaceForAccount(account.id, games)
                finishSuccess(
                    "Epic Games conectado · " + games.size + " juegos sincronizados"
                )
            }.onFailure {
                finished = false
                finishWithError(
                    "Epic no pudo sincronizar la biblioteca: " +
                        it.message.orEmpty().take(180)
                )
            }
        }
    }

    private fun unquoteJsonString(value: String?): String? {
        if (value.isNullOrBlank() || value.trim() == "null") return null
        val raw = value.trim()
        return if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
            runCatching {
                org.json.JSONTokener(raw).nextValue() as? String
            }.getOrNull()
        } else {
            raw
        }
    }

    private fun finishSuccess(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun finishWithError(message: String) {
        if (isFinishing) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

private data class SteamGameResult(
    val appId: Int,
    val title: String,
    val artworkUrl: String
)

private data class SteamSyncResult(
    val steamId: String,
    val displayName: String,
    val games: List<SteamGameResult>
)

private data class EpicCredentials(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String,
    val displayName: String,
    val expiresAt: Long
) {
    fun asJson(): String = JSONObject()
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("accountId", accountId)
        .put("displayName", displayName)
        .put("expiresAt", expiresAt)
        .toString()
}

private data class EpicLibraryGame(
    val appName: String,
    val title: String,
    val artworkUrl: String
)

private fun HttpURLConnection.readBody(): String {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
}

private object SteamStoreClient {
    fun readAuthenticatedLibrary(): Result<SteamSyncResult> = runCatching {
        val cookies = CookieManager.getInstance()
        val storeCookies = cookies.getCookie("https://store.steampowered.com").orEmpty()
        val communityCookies = cookies.getCookie("https://steamcommunity.com").orEmpty()
        check(
            storeCookies.contains("steamLoginSecure=") ||
                communityCookies.contains("steamLoginSecure=")
        ) { "Steam todavía no está autenticado" }

        val profile = openGet(
            "https://steamcommunity.com/my/",
            communityCookies
        )
        val html = profile.readBody()
        val finalUrl = profile.url?.toString().orEmpty()
        val steamId =
            Regex("/profiles/(\\d{17})").find(finalUrl)?.groupValues?.get(1)
                ?: Regex("steamid\\D+(\\d{17})", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)
        check(!steamId.isNullOrBlank()) { "No se pudo obtener el SteamID64" }

        val displayName = Regex(
            """<title>\s*(.*?)\s*-\s*Steam Community\s*</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.get(1).orEmpty()
            .replace(Regex("<[^>]+>"), "")
            .trim()

        val userData = JSONObject(
            openGet(
                "https://store.steampowered.com/dynamicstore/userdata/?t=" +
                    System.currentTimeMillis(),
                storeCookies
            ).readBody()
        )
        val owned = userData.optJSONArray("rgOwnedApps") ?: JSONArray()
        val appIds = (0 until owned.length())
            .mapNotNull { owned.optInt(it, -1).takeIf { id -> id > 0 } }
            .distinct()

        val games = buildList {
            appIds.chunked(50).forEach { batch ->
                val details = JSONObject(
                    openGet(
                        "https://store.steampowered.com/api/appdetails?appids=" +
                            batch.joinToString(",") +
                            "&cc=CL&l=spanish",
                        ""
                    ).readBody()
                )
                batch.forEach { appId ->
                    val row = details.optJSONObject(appId.toString())
                        ?: return@forEach
                    if (!row.optBoolean("success")) return@forEach
                    val data = row.optJSONObject("data") ?: return@forEach
                    val type = data.optString("type")
                    if (type.isNotBlank() && type != "game") return@forEach
                    val title = data.optString("name").trim()
                    if (title.isBlank()) return@forEach
                    add(
                        SteamGameResult(
                            appId = appId,
                            title = title,
                            artworkUrl = data.optString("header_image")
                        )
                    )
                }
            }
        }

        SteamSyncResult(steamId, displayName, games)
    }

    private fun openGet(url: String, cookies: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            if (cookies.isNotBlank()) setRequestProperty("Cookie", cookies)
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140 Mobile Safari/537.36"
            )
        }
}

private object EpicConstants {
    const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
    const val CLIENT_SECRET = "daafbccc737745039dffe53d94fc76cf"
    const val AUTH_BASE_URL = "https://www.epicgames.com"
    const val OAUTH_HOST = "account-public-service-prod03.ol.epicgames.com"
    const val LIBRARY_HOST = "library-service.live.use1a.on.epicgames.com"
    const val REDIRECT_URI = "https://www.epicgames.com/id/api/redirect"
}

private object EpicStoreClient {
    fun exchangeCode(code: String): EpicCredentials {
        val basic = Base64.encodeToString(
            (EpicConstants.CLIENT_ID + ":" + EpicConstants.CLIENT_SECRET)
                .toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        val json = post(
            "https://" + EpicConstants.OAUTH_HOST + "/account/api/oauth/token",
            formBody(
                "grant_type" to "authorization_code",
                "code" to code,
                "token_type" to "eg1"
            ),
            mapOf(
                "Authorization" to "Basic " + basic,
                "User-Agent" to "UELauncher/11.0.1-14907503+++Portal+Release-Live " +
                    "Windows/10.0.19041.1.256.64bit"
            )
        )
        return EpicCredentials(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            accountId = json.getString("account_id"),
            displayName = json.optString("displayName"),
            expiresAt = parseExpiresAt(json)
        )
    }

    fun fetchLibrary(accessToken: String): List<EpicLibraryGame> {
        val out = mutableListOf<EpicLibraryGame>()
        var cursor: String? = null
        var previous: String? = null
        do {
            val suffix = cursor?.let {
                "&cursor=" + URLEncoder.encode(it, "UTF-8")
            }.orEmpty()
            val json = get(
                "https://" + EpicConstants.LIBRARY_HOST +
                    "/library/api/public/items?includeMetadata=true" + suffix,
                mapOf(
                    "Authorization" to "Bearer " + accessToken,
                    "User-Agent" to "Legendary/0.1.0 (GameHub Ultra)"
                )
            )
            val records = json.optJSONArray("records") ?: JSONArray()
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                val appName = record.optString("appName").trim()
                val namespace = record.optString("namespace").trim()
                if (appName.isBlank()) continue
                if (namespace == "ue" || appName == "1") continue
                if (record.optString("sandboxType") == "PRIVATE") continue

                val metadata = record.optJSONObject("metadata")
                val title = record.optString("title").trim().ifBlank {
                    metadata?.optString("title").orEmpty().trim()
                }.ifBlank { appName }

                val artwork = metadata?.optJSONArray("keyImages")
                    ?.let { images ->
                        (0 until images.length())
                            .mapNotNull { images.optJSONObject(it)?.optString("url") }
                            .firstOrNull { it.isNotBlank() }
                    }.orEmpty()

                out += EpicLibraryGame(appName, title, artwork)
            }
            previous = cursor
            cursor = json.optJSONObject("responseMetadata")
                ?.optString("nextCursor")
                ?.takeIf { it.isNotBlank() && it != "null" }
        } while (cursor != null && cursor != previous)

        return out.distinctBy { it.appName }
    }

    private fun parseExpiresAt(json: JSONObject): Long {
        json.optLong("expires_at", Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let { return it }

        val raw = json.optString("expires_at")
        if (raw.isNotBlank()) {
            runCatching { return Instant.parse(raw).toEpochMilli() }
        }
        return System.currentTimeMillis() +
            json.optLong("expires_in", 7200L) * 1000L
    }

    private fun formBody(vararg entries: Pair<String, String>): String =
        entries.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" +
                URLEncoder.encode(it.second, "UTF-8")
        }

    private fun post(
        url: String,
        body: String,
        headers: Map<String, String>
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty(
            "Content-Type",
            "application/x-www-form-urlencoded"
        )
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readJson(connection)
    }

    private fun get(
        url: String,
        headers: Map<String, String>
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.requestMethod = "GET"
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return readJson(connection)
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP " + code)
        return JSONObject(text)
    }
}
