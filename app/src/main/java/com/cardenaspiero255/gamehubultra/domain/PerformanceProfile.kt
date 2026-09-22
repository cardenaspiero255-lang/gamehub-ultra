package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceProfile(
    val title: String,
    val description: String
) {
    BALANCED(
        "FPS balanceado",
        "Mantiene el modo sostenido desactivado y prioriza un equilibrio entre fluidez, consumo y temperatura."
    ),
    FRAME_INTERPOLATION(
        "Priorizar interpolación",
        "No puede forzar interpolación de frames en otros juegos. El perfil solo informa de la limitación y conserva el control seguro de Android."
    ),
    X4(
        "X4",
        "Perfil de alto rendimiento para la propia aplicación: usa Sustained Performance Mode cuando Android y el dispositivo lo exponen."
    )
}
