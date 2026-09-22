package com.cardenaspiero255.gamehubultra.domain

enum class PerformanceProfile(
    val title: String,
    val description: String,
    val sustainedPerformanceIntent: Boolean,
    val frameInterpolationIntent: Boolean
) {
    BALANCED(
        title = "FPS balanceado",
        description = "Prioriza un equilibrio entre fluidez, consumo y temperatura. No solicita Sustained Performance Mode.",
        sustainedPerformanceIntent = false,
        frameInterpolationIntent = false
    ),
    FRAME_INTERPOLATION(
        title = "Priorizar interpolación",
        description = "Prioriza las opciones relacionadas con interpolación cuando exista una API compatible. Android no expone una API pública para forzar interpolación en otros juegos.",
        sustainedPerformanceIntent = false,
        frameInterpolationIntent = true
    ),
    X4(
        title = "X4",
        description = "Preset de alto rendimiento para GameHub Ultra. Solicita Sustained Performance Mode solo cuando Android y el dispositivo lo soportan; no simula un multiplicador de frames.",
        sustainedPerformanceIntent = true,
        frameInterpolationIntent = false
    )
)
