package com.cardenaspiero255.gamehubultra.domain

data class AdaptiveGameKey(val packageName: String, val version: String)
data class AdaptiveTrendSample(val thermalStatus: Int?, val batteryPercent: Int?, val refreshRateHz: Float?, val memoryUsedPercent: Int?, val latencyMs: Int?)
data class PerGameAdaptiveDecision(val profile: PerformanceProfile, val changed: Boolean, val reason: String)

class PerGameAdaptiveOptimizer(private val confirmationsRequired: Int = 2, private val cooldownMillis: Long = 30000L) {
 private data class State(var profile: PerformanceProfile, var candidate: PerformanceProfile? = null, var confirmations: Int = 0, var lastChange: Long? = null)
 private val states = mutableMapOf<AdaptiveGameKey, State>()
 init { require(confirmationsRequired >= 1); require(cooldownMillis >= 0) }
 fun evaluate(key: AdaptiveGameKey, activeProfile: PerformanceProfile, samples: List<AdaptiveTrendSample>, nowMillis: Long): PerGameAdaptiveDecision {
  val state = states.getOrPut(key) { State(activeProfile) }
  val target = target(samples)
  if (target == state.profile) { state.candidate = null; state.confirmations = 0; return PerGameAdaptiveDecision(state.profile, false, reason(samples, false)) }
  if (state.lastChange?.let { nowMillis - it < cooldownMillis } == true) return PerGameAdaptiveDecision(state.profile, false, "Periodo de enfriamiento activo: se evita una oscilación rápida de perfil.")
  if (state.candidate != target) { state.candidate = target; state.confirmations = 1 } else state.confirmations++
  if (state.confirmations < confirmationsRequired) return PerGameAdaptiveDecision(state.profile, false, "La tendencia requiere confirmación antes de cambiar automáticamente el perfil.")
  state.profile = target; state.candidate = null; state.confirmations = 0; state.lastChange = nowMillis
  return PerGameAdaptiveDecision(target, true, reason(samples, true))
 }
 private fun target(samples: List<AdaptiveTrendSample>): PerformanceProfile {
  if (samples.isEmpty()) return PerformanceProfile.BALANCED
  val x = samples.last()
  val pressure = x.thermalStatus?.let { it >= 3 } == true || x.batteryPercent?.let { it <= 45 } == true || x.memoryUsedPercent?.let { it >= 88 } == true || x.latencyMs?.let { it >= 120 } == true || trend(samples.mapNotNull { it.refreshRateHz }) < -15f || trend(samples.mapNotNull { it.latencyMs?.toFloat() }) > 50f
  return if (pressure) PerformanceProfile.BALANCED else PerformanceProfile.X4
 }
 private fun trend(values: List<Float>) = if (values.size < 2) 0f else values.last() - values.first()
 private fun reason(samples: List<AdaptiveTrendSample>, changed: Boolean): String {
  if (samples.isEmpty()) return "Sin muestras suficientes: se mantiene una decisión conservadora."
  val x = samples.last(); val signals = mutableListOf<String>()
  if (x.thermalStatus?.let { it >= 3 } == true) signals += "térmica"; if (x.batteryPercent?.let { it <= 45 } == true) signals += "batería"; if (trend(samples.mapNotNull { it.refreshRateHz }) < -15f) signals += "refresco"; if (x.memoryUsedPercent?.let { it >= 88 } == true) signals += "memoria"; if (x.latencyMs?.let { it >= 120 } == true || trend(samples.mapNotNull { it.latencyMs?.toFloat() }) > 50f) signals += "latencia"
  return if (signals.isEmpty()) "Tendencia estable: se mantiene la histéresis adaptativa." else "La tendencia de ${signals.joinToString(", ")} justifica ${if (changed) "el cambio automático" else "mantener el perfil"}."
 }
}
