package com.cardenaspiero255.gamehubultra.integration

/**
 * Stable v1 contract for an inert, read-only local Android status channel.
 * This does NOT delegate to the Ultra tool executor or claim remote control.
 */
object JarvisStatusContract {
    const val AUTHORITY = "com.cardenaspiero255.gamehubultra.jarvis.status"
    const val PERMISSION = "com.cardenaspiero255.gamehubultra.permission.JARVIS_READ_STATUS"
    const val JARVIS_PACKAGE = "com.cardenaspiero255.jarvis"

    data class Status(
        val version: Int = 1,
        val packageName: String = "com.cardenaspiero255.gamehubultra",
        val status: String = "READ_ONLY",
        val remoteExecutionEnabled: Boolean = false,
        val toolExecutionEnabled: Boolean = false,
    )

    fun authorize(
        callingPackages: Set<String>,
        signaturePermissionGranted: Boolean,
        path: String,
    ): Boolean = signaturePermissionGranted &&
        callingPackages.contains(JARVIS_PACKAGE) &&
        path == "v1/status"

    fun reply(): Status = Status()
}
