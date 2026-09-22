package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceProfile(
    val title: String,
    val description: String
) {
    BALANCED("FPS balanceado", "Equilibra fluidez, consumo y temperatura."),
    FRAME_INTERPOLATION("Priorizar interpolación", "Prioriza la experiencia de frames cuando el dispositivo y la app lo permiten."),
    X4("X4", "Perfil experimental para futuras integraciones compatibles.")
}
