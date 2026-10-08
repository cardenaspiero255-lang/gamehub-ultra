package com.cardenaspiero255.gamehubultra.performance.framepacing

/**
 * Frontera explícita hacia CAR-47. CAR-50 no decide perfiles ni ejecuta acciones;
 * solo indica cuándo su señal tipada tiene evidencia suficiente para ser consumida.
 */
class FramePacingIntegration {
    fun shouldNotifyAdaptiveOptimizer(
        signal: FramePacingAdaptiveSignal
    ): Boolean = signal.shouldNotify
}
