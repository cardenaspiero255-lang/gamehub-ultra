package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceProfile(
    val title: String,
    val description: String,
    val sustainedPerformanceIntent: Boolean,
    val frameInterpolationIntent: Boolean,
    val acceptsHigherTemperature: Boolean
) {
    BALANCED(
        title = "FPS balanceado",
        description = "Prioriza un equilibrio entre fluidez, consumo y temperatura. No solicita Sustained Performance Mode.",
        sustainedPerformanceIntent = false,
        frameInterpolationIntent = false,
        acceptsHigherTemperature = false
    ),
    FRAME_INTERPOLATION(
        title = "Priorizar interpolación",
        description = "Prioriza las opciones relacionadas con interpolación cuando exista una API compatible. Acepta un mayor coste térmico para mantener esa prioridad, pero Android no expone una API pública para forzar interpolación en otros juegos.",
        sustainedPerformanceIntent = false,
        frameInterpolationIntent = true,
        acceptsHigherTemperature = true
    ),
    X4(
        title = "X4",
        description = "Preset de alto rendimiento para GameHub Ultra. Solicita Sustained Performance Mode solo cuando Android y el dispositivo lo soportan. No proporciona generación de frames por sí mismo.",
        sustainedPerformanceIntent = true,
        frameInterpolationIntent = false,
        acceptsHigherTemperature = false
    )
}
