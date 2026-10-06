package com.cardenaspiero255.gamehubultra.domain

import java.util.UUID

enum class OptimizationFeedbackDecision { NONE, ACCEPTED, REJECTED, REVERTED }

data class OptimizationObservation(
    val id: String = UUID.randomUUID().toString(),
    val contextKey: String,
    val profile: PerformanceProfile,
    val measuredFps: Float? = null,
    val stable: Boolean = false,
    val failed: Boolean = false,
    val highTemperature: Boolean = false,
    val thermalStatus: Int? = null,
    val batteryPercent: Int? = null,
    val errorReason: String? = null,
    val timestampMillis: Long,
    val feedbackDecision: OptimizationFeedbackDecision = OptimizationFeedbackDecision.NONE
)
