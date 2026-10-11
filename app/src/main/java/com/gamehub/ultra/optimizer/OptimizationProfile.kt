package com.gamehub.ultra.optimizer

enum class OptimizationProfile(
    val title: String,
    val description: String
) {
    BALANCED(
        "Balanced FPS",
        "Equilibra FPS, temperatura y consumo."
    ),

    COOL(
        "Cool",
        "Reduce la carga para priorizar temperaturas más bajas."
    ),

    PERFORMANCE(
        "Performance",
        "Prioriza el rendimiento y los FPS."
    ),

    INTERPOLATION_X2(
        "Frame Interpolation X2",
        "Prioriza la interpolación X2 con mayor carga y latencia."
    ),

    INTERPOLATION_X4(
        "Frame Interpolation X4",
        "Máxima interpolación disponible. Mayor carga, consumo y latencia."
    )
}
