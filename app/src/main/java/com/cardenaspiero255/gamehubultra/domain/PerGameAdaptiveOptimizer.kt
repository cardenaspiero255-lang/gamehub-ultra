package com.cardenaspiero255.gamehubultra.domain

data class AdaptiveGameKey(
    val packageName: String,
    val version: String
)

data class AdaptiveTrendSample(
    val thermalStatus: Int?,
    val batteryPercent: Int?,
    val refreshRateHz: Float?,
    val memoryUsedPercent: Int?,
    val latencyMs: Int?,
    val thermalPrediction: ThermalPrediction? = null,
    val batteryConstrained: Boolean = false,
    val batteryConstraintReason: String? = null,
    val batteryCharging: Boolean? = null
)

data class PerGameAdaptiveDecision(
    val profile: PerformanceProfile,
    val changed: Boolean,
    val reason: String
)

data class PerGameAdaptivePersistedState(
    val profile: PerformanceProfile,
    val candidate: PerformanceProfile?,
    val confirmations: Int,
    val lastChangeMillis: Long?,
    val recoveryProfile: PerformanceProfile?
)

data class PerGameAdaptivePendingDecision(
    val sessionId: String,
    val key: AdaptiveGameKey,
    val previousProfile: PerformanceProfile,
    val targetProfile: PerformanceProfile,
    val targetState: PerGameAdaptivePersistedState,
    val eventTimestampMillis: Long,
    val reason: String
)

interface PerGameAdaptiveStateStore {
    fun read(key: AdaptiveGameKey): PerGameAdaptivePersistedState?
    fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState)
    fun delete(key: AdaptiveGameKey) = Unit
    fun ownedRecoveryProfileForPackage(
        packageName: String,
        excludingVersion: String,
        activeProfile: PerformanceProfile
    ): PerformanceProfile? = null
    fun readPendingDecision(sessionId: String): PerGameAdaptivePendingDecision? = null
    fun writePendingDecision(decision: PerGameAdaptivePendingDecision) = Unit
    fun clearPendingDecision(sessionId: String) = Unit
}

class PerGameAdaptiveOptimizer(
    private val confirmationsRequired: Int = 2,
    private val cooldownMillis: Long = 30_000L,
    private val stateStore: PerGameAdaptiveStateStore? = null
) {
    private data class State(
        var profile: PerformanceProfile,
        var candidate: PerformanceProfile? = null,
        var confirmations: Int = 0,
        var lastChange: Long? = null,
        var recoveryProfile: PerformanceProfile? = null
    )

    private val states = mutableMapOf<AdaptiveGameKey, State>()

    init {
        require(confirmationsRequired >= 1)
        require(cooldownMillis >= 0)
    }

    fun recordExplicitProfileSelection(
        key: AdaptiveGameKey,
        profile: PerformanceProfile
    ) {
        val state = states.getOrPut(key) {
            initialState(key, profile)
        }
        state.profile = profile
        state.candidate = null
        state.confirmations = 0
        state.lastChange = null
        state.recoveryProfile = null
        stateStore?.write(key, state.toPersistedState())
    }

    internal fun snapshotState(
        key: AdaptiveGameKey
    ): PerGameAdaptivePersistedState? =
        states[key]?.toPersistedState() ?: stateStore?.read(key)

    internal fun restoreState(
        key: AdaptiveGameKey,
        snapshot: PerGameAdaptivePersistedState?
    ) {
        if (snapshot == null) {
            states.remove(key)
            stateStore?.delete(key)
        } else {
            states[key] = snapshot.toRuntimeState()
            stateStore?.write(key, snapshot)
        }
    }

    internal fun commitState(key: AdaptiveGameKey) {
        states[key]?.let { state ->
            stateStore?.write(key, state.toPersistedState())
        }
    }

    internal fun readPendingDecision(sessionId: String): PerGameAdaptivePendingDecision? =
        stateStore?.readPendingDecision(sessionId)

    internal fun writePendingDecision(decision: PerGameAdaptivePendingDecision) {
        stateStore?.writePendingDecision(decision)
    }

    internal fun clearPendingDecision(sessionId: String) {
        stateStore?.clearPendingDecision(sessionId)
    }

    fun evaluate(
        key: AdaptiveGameKey,
        activeProfile: PerformanceProfile,
        samples: List<AdaptiveTrendSample>,
        nowMillis: Long
    ): PerGameAdaptiveDecision =
        evaluateInternal(
            key = key,
            activeProfile = activeProfile,
            samples = samples,
            nowMillis = nowMillis,
            persistState = true
        )

    internal fun evaluateUncommitted(
        key: AdaptiveGameKey,
        activeProfile: PerformanceProfile,
        samples: List<AdaptiveTrendSample>,
        nowMillis: Long
    ): PerGameAdaptiveDecision =
        evaluateInternal(
            key = key,
            activeProfile = activeProfile,
            samples = samples,
            nowMillis = nowMillis,
            persistState = false
        )

    private fun evaluateInternal(
        key: AdaptiveGameKey,
        activeProfile: PerformanceProfile,
        samples: List<AdaptiveTrendSample>,
        nowMillis: Long,
        persistState: Boolean
    ): PerGameAdaptiveDecision {
        val persisted = stateStore?.read(key)
        val state = when {
            persisted != null &&
                states[key]?.toPersistedState() != persisted -> {
                persisted.toRuntimeState().also { states[key] = it }
            }
            else -> states.getOrPut(key) {
                initialState(key, activeProfile)
            }
        }

        if (state.profile != activeProfile) {
            state.profile = activeProfile
            state.candidate = null
            state.confirmations = 0
            state.lastChange = null
            state.recoveryProfile = null
            return decision(
                key = key,
                state = state,
                persistState = persistState,
                profile = activeProfile,
                changed = false,
                reason = "Se detectó un cambio externo de perfil; se respeta y se reinicia la adaptación automática."
            )
        }

        if (samples.isEmpty()) {
            state.candidate = null
            state.confirmations = 0
            return decision(
                key = key,
                state = state,
                persistState = persistState,
                profile = activeProfile,
                changed = false,
                reason = "Sin muestras suficientes: se mantiene el perfil activo."
            )
        }

        val target = target(state, samples)
        if (target == state.profile) {
            state.candidate = null
            state.confirmations = 0
            return decision(
                key = key,
                state = state,
                persistState = persistState,
                profile = state.profile,
                changed = false,
                reason = reason(samples, changed = false)
            )
        }

        if (state.lastChange?.let { nowMillis - it < cooldownMillis } == true) {
            return decision(
                key = key,
                state = state,
                persistState = persistState,
                profile = state.profile,
                changed = false,
                reason = "Periodo de enfriamiento activo: se evita una oscilación rápida de perfil."
            )
        }

        if (state.candidate != target) {
            state.candidate = target
            state.confirmations = 1
        } else {
            state.confirmations++
        }

        if (state.confirmations < confirmationsRequired) {
            return decision(
                key = key,
                state = state,
                persistState = persistState,
                profile = state.profile,
                changed = false,
                reason = "La tendencia requiere confirmación antes de cambiar automáticamente el perfil."
            )
        }

        val previous = state.profile
        state.profile = target
        state.candidate = null
        state.confirmations = 0
        state.lastChange = nowMillis
        state.recoveryProfile = when {
            target == PerformanceProfile.BALANCED &&
                previous != PerformanceProfile.BALANCED -> previous
            state.recoveryProfile != null &&
                target == state.recoveryProfile -> null
            else -> state.recoveryProfile
        }

        return decision(
            key = key,
            state = state,
            persistState = persistState,
            profile = target,
            changed = true,
            reason = reason(samples, changed = true)
        )
    }

    private fun initialState(
        key: AdaptiveGameKey,
        activeProfile: PerformanceProfile
    ): State {
        stateStore?.read(key)?.let { return it.toRuntimeState() }
        val migratedRecovery = stateStore?.ownedRecoveryProfileForPackage(
            packageName = key.packageName,
            excludingVersion = key.version,
            activeProfile = activeProfile
        )
        return State(
            profile = activeProfile,
            recoveryProfile = migratedRecovery
        )
    }

    private fun target(
        state: State,
        samples: List<AdaptiveTrendSample>
    ): PerformanceProfile {
        val latest = samples.last()
        val refreshTrend = trend(samples.mapNotNull { it.refreshRateHz })
        val latencyTrend = trend(samples.mapNotNull { it.latencyMs?.toFloat() })

        val pressure =
            latest.thermalPrediction?.allowPreventiveSignal == true ||
                latest.thermalStatus?.let { it >= 3 } == true ||
                latest.batteryConstrained ||
                (
                    latest.batteryCharging != true &&
                        latest.batteryPercent?.let { it <= 15 } == true
                    ) ||
                latest.memoryUsedPercent?.let { it >= 88 } == true ||
                latest.latencyMs?.let { it >= 120 } == true ||
                refreshTrend < -15f ||
                latencyTrend > 50f

        if (pressure) return PerformanceProfile.BALANCED

        val knownSignals = listOf(
            latest.thermalStatus,
            latest.batteryPercent,
            latest.refreshRateHz,
            latest.memoryUsedPercent,
            latest.latencyMs
        ).count { it != null }

        val stableRecovery =
            state.recoveryProfile != null &&
                samples.size >= 3 &&
                knownSignals >= 3 &&
                latest.thermalStatus?.let { it <= 1 } != false &&
                !latest.batteryConstrained &&
                (
                    latest.batteryCharging == true ||
                        latest.batteryPercent?.let { it >= 55 } != false
                    ) &&
                latest.memoryUsedPercent?.let { it <= 80 } != false &&
                latest.latencyMs?.let { it <= 80 } != false &&
                refreshTrend >= -10f &&
                latencyTrend <= 30f

        return if (stableRecovery) checkNotNull(state.recoveryProfile) else state.profile
    }

    private fun decision(
        key: AdaptiveGameKey,
        state: State,
        persistState: Boolean,
        profile: PerformanceProfile,
        changed: Boolean,
        reason: String
    ): PerGameAdaptiveDecision {
        if (persistState) {
            stateStore?.write(key, state.toPersistedState())
        }
        return PerGameAdaptiveDecision(
            profile = profile,
            changed = changed,
            reason = reason
        )
    }

    private fun State.toPersistedState() =
        PerGameAdaptivePersistedState(
            profile = profile,
            candidate = candidate,
            confirmations = confirmations,
            lastChangeMillis = lastChange,
            recoveryProfile = recoveryProfile
        )

    private fun PerGameAdaptivePersistedState.toRuntimeState() =
        State(
            profile = profile,
            candidate = candidate,
            confirmations = confirmations.coerceAtLeast(0),
            lastChange = lastChangeMillis,
            recoveryProfile = recoveryProfile
        )

    private fun trend(values: List<Float>): Float =
        if (values.size < 2) 0f else values.last() - values.first()

    private fun reason(
        samples: List<AdaptiveTrendSample>,
        changed: Boolean
    ): String {
        if (samples.isEmpty()) {
            return "Sin muestras suficientes: se mantiene el perfil activo."
        }

        val latest = samples.last()
        val signals = mutableListOf<String>()
        if (latest.thermalPrediction?.allowPreventiveSignal == true) {
            signals += "predicción térmica"
        }
        if (latest.thermalStatus?.let { it >= 3 } == true) signals += "térmica"
        if (latest.batteryConstrained) {
            signals += latest.batteryConstraintReason
                ?.takeIf(String::isNotBlank)
                ?: "restricción de batería"
        } else if (
            latest.batteryCharging != true &&
            latest.batteryPercent?.let { it <= 15 } == true
        ) {
            signals += "batería crítica"
        }
        if (trend(samples.mapNotNull { it.refreshRateHz }) < -15f) signals += "refresco"
        if (latest.memoryUsedPercent?.let { it >= 88 } == true) signals += "memoria"
        if (
            latest.latencyMs?.let { it >= 120 } == true ||
            trend(samples.mapNotNull { it.latencyMs?.toFloat() }) > 50f
        ) {
            signals += "latencia"
        }

        return if (signals.isEmpty()) {
            if (changed) {
                "La tendencia volvió a ser estable y permite recuperar el perfil gestionado por Ultra."
            } else {
                "Tendencia estable: se mantiene la histéresis adaptativa."
            }
        } else {
            "La tendencia de ${signals.joinToString(", ")} justifica ${if (changed) "el cambio automático" else "mantener el perfil"}."
        }
    }
}
