package com.cardenaspiero255.gamehubultra.vision

import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch

/**
 * Ephemeral single-use user image selection. Image data is not read here.
 * The caller must separately request confirmation before consuming a URI.
 */
internal class UltraVoluntaryVisualSession(
    private val currentPrivacyEpoch: () -> Long = UltraPrivacyEpoch::current,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class Selection(
        val id: Long,
        val game: String?,
        val privacyEpoch: Long,
        val started: Long,
        val uri: String? = null,
        val selected: Long = 0L
    )

    private var counter = 0L
    private var selection: Selection? = null

    @Synchronized
    fun beginUserSelection(gamePackage: String?): Long {
        counter = Math.addExact(counter, 1L)
        selection = Selection(counter, gamePackage, currentPrivacyEpoch(), monotonicMillis())
        return counter
    }

    @Synchronized
    fun acceptPickedUri(ticket: Long, uri: String?, gamePackage: String?): Boolean {
        val item = selection ?: return false
        if (item.id != ticket) return false
        val now = monotonicMillis()
        val accepted = valid(item, gamePackage, now, 60_000L) && validUri(uri)
        selection = if (accepted) item.copy(uri = uri, selected = now) else null
        return accepted
    }

    @Synchronized
    fun awaitingConfirmation(ticket: Long, gamePackage: String?): Boolean {
        val item = selection ?: return false
        val now = monotonicMillis()
        val accepted = item.id == ticket && item.uri != null &&
            valid(item, gamePackage, now, 180_000L) &&
            elapsed(item.selected, now, 120_000L)
        if (!accepted) selection = null
        return accepted
    }

    @Synchronized
    fun consumeAfterUserConfirmation(ticket: Long, gamePackage: String?): String? {
        val item = selection ?: return null
        selection = null
        val now = monotonicMillis()
        return item.uri.takeIf {
            item.id == ticket &&
                valid(item, gamePackage, now, 180_000L) &&
                elapsed(item.selected, now, 120_000L)
        }
    }

    @Synchronized
    fun revoke() {
        selection = null
    }

    private fun valid(item: Selection, game: String?, now: Long, limit: Long) =
        item.game == game && item.privacyEpoch == currentPrivacyEpoch() &&
            elapsed(item.started, now, limit)

    private fun elapsed(start: Long, now: Long, limit: Long) =
        now >= start && now - start <= limit

    private fun validUri(uri: String?): Boolean {
        if (uri == null || uri.length !in 12..2048 ||
            !uri.startsWith("content://") || uri.any { it.isWhitespace() || it.isISOControl() }
        ) return false
        val slash = uri.indexOf('/', "content://".length)
        if (slash <= "content://".length || slash == uri.lastIndex) return false
        val host = uri.substring("content://".length, slash)
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    }
}
