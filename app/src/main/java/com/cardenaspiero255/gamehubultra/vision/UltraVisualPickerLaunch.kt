package com.cardenaspiero255.gamehubultra.vision

/**
 * One Android photo-picker launcher has no callback-supplied request ID.
 * Never start a second launch while an earlier result is outstanding. This
 * prevents an old result from being silently accepted as a newer selection.
 */
internal class UltraVisualPickerLaunch {
    private var issuedTicket: Long? = null
    val awaitingCallback: Boolean get() = issuedTicket != null

    @Synchronized
    fun begin(ticket: Long): Boolean {
        if (ticket <= 0L || issuedTicket != null) return false
        issuedTicket = ticket
        return true
    }

    /**
     * Invalidate consent without discarding the pending Android callback:
     * another picker must remain blocked until it finishes.
     */
    @Synchronized
    fun revoke() = Unit

    @Synchronized
    fun complete(currentTicket: Long?): Long? {
        val launchedTicket = issuedTicket
        issuedTicket = null
        return launchedTicket?.takeIf { it == currentTicket }
    }
}
