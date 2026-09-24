package com.cardenaspiero255.gamehubultra.domain

/**
 * CAR-31 keeps GameHub Ultra visually aligned with the original GameHub shell while
 * applying the approved Canva red-neon/black identity.
 */
object GameHubOriginalUiContract {
    val topNavigation = listOf("Inicio", "Biblioteca", "Perfil")

    val primaryActions = listOf(
        "Importar juegos",
        "Orientación horizontal",
        "Orientación vertical"
    )

    val canvaIdentity = listOf(
        "Negro base",
        "Rojo neón",
        "Rojo brillante",
        "Alto contraste gamer"
    )

    fun keepsOriginalShell(): Boolean =
        topNavigation == listOf("Inicio", "Biblioteca", "Perfil") &&
            "Importar juegos" in primaryActions &&
            primaryActions.any { it.contains("horizontal", ignoreCase = true) } &&
            primaryActions.any { it.contains("vertical", ignoreCase = true) }

    fun appliesCanvaNeonIdentity(): Boolean =
        canvaIdentity.any { it.contains("Rojo neón", ignoreCase = true) } &&
            canvaIdentity.any { it.contains("Negro", ignoreCase = true) }
}
