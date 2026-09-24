package com.cardenaspiero255.gamehubultra.domain

/**
 * Aggregates the CAR-30 acceptance surfaces into one release-facing status.
 *
 * The gate is intentionally conservative: one missing surface means Ultra Final
 * remains in review, and the UI must explain what is missing instead of hiding
 * the gap behind a green badge.
 */
object UltraFinalExperienceGate {
    private val requiredSurfaces = listOf(
        "Dashboard",
        "Perfiles",
        "Diagnósticos",
        "Biblioteca",
        "Historial",
        "Accesibilidad",
        "Rendimiento",
        "Sin claims falsos"
    )

    fun evaluate(
        dashboardReady: Boolean,
        profileReady: Boolean,
        diagnosticsReady: Boolean,
        libraryReady: Boolean,
        sessionHistoryReady: Boolean,
        accessibilityReady: Boolean,
        performanceReady: Boolean,
        unsupportedClaimsAvoided: Boolean
    ): UltraFinalExperienceSummary {
        val states = listOf(
            "Dashboard" to dashboardReady,
            "Perfiles" to profileReady,
            "Diagnósticos" to diagnosticsReady,
            "Biblioteca" to libraryReady,
            "Historial" to sessionHistoryReady,
            "Accesibilidad" to accessibilityReady,
            "Rendimiento" to performanceReady,
            "Sin claims falsos" to unsupportedClaimsAvoided
        )
        val readyCount = states.count { it.second }
        val missing = states.filterNot { it.second }.map { it.first }
        val ready = missing.isEmpty()
        return UltraFinalExperienceSummary(
            readyForRelease = ready,
            readyCount = readyCount,
            totalCount = requiredSurfaces.size,
            missingSurfaces = missing,
            statusLabel = if (ready) "Ultra final listo" else "Revisión pendiente"
        )
    }
}

data class UltraFinalExperienceSummary(
    val readyForRelease: Boolean,
    val readyCount: Int,
    val totalCount: Int,
    val missingSurfaces: List<String>,
    val statusLabel: String
) {
    val readinessRatio: String = "$readyCount/$totalCount"

    val missingSummary: String =
        if (missingSurfaces.isEmpty()) {
            "Todas las superficies principales están conectadas y validadas."
        } else {
            missingSurfaces.joinToString(separator = " · ")
        }
}
