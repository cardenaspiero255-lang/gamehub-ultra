package com.cardenaspiero255.gamehubultra.vision

/** Recheck private OCR provenance at send-time, not just at initial display. */
internal object UltraVisualDraftPolicy {
    data class Origin(val privacyEpoch: Long, val gamePackage: String?)

    fun canSend(origin: Origin?, currentEpoch: Long, gamePackage: String?): Boolean =
        origin == null ||
            (origin.privacyEpoch == currentEpoch && origin.gamePackage == gamePackage)
}
