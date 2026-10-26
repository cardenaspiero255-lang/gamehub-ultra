package com.cardenaspiero255.gamehubultra.performance.pressure

/**
 * CAR-52: read-only pressure estimates from measured public Android telemetry.
 * A pressure estimate never claims to kill processes or free RAM.
 */
enum class PressureLevel { UNKNOWN, NORMAL, ELEVATED, CRITICAL }

enum class PressureTrend { INSUFFICIENT_HISTORY, STABLE, INCREASING, RECOVERING }

data class ResourcePressureSample(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val androidLowMemory: Boolean? = null
)

data class ResourcePressureReport(
    val ramPressure: PressureLevel,
    val storagePressure: PressureLevel,
    val availableRamPercent: Int?,
    val freeStoragePercent: Int?,
    val ramTrend: PressureTrend,
    val notes: List<String>
) {
    /** This module is diagnostic only and does not reclaim memory or control other apps. */
    val claimsMemoryReclaimed: Boolean get() = false
}

object ResourcePressureIntelligence {
    fun assess(
        current: ResourcePressureSample,
        history: List<ResourcePressureSample> = emptyList()
    ): ResourcePressureReport {
        val ramAvailable = headroomPercent(current.availableRamMb, current.totalRamMb)
        val storageAvailable = headroomPercent(current.freeStorageBytes, current.totalStorageBytes)
        val observedRam = classify(ramAvailable, elevatedBelow = 20, criticalBelow = 10)
        val ram = if (current.androidLowMemory == true && observedRam == PressureLevel.NORMAL) {
            PressureLevel.ELEVATED
        } else if (current.androidLowMemory == true && observedRam == PressureLevel.UNKNOWN) {
            PressureLevel.ELEVATED
        } else {
            observedRam
        }
        val storage = classify(storageAvailable, elevatedBelow = 15, criticalBelow = 5)
        val trend = detectRamTrend(current, history)

        val notes = buildList {
            if (current.androidLowMemory == true) {
                add("Android informó presión de memoria; la gestión de RAM pertenece al sistema.")
            }
            if (ram == PressureLevel.CRITICAL) {
                add("Queda poca RAM disponible. Cierra manualmente tareas innecesarias si observas cierres.")
            } else if (ram == PressureLevel.ELEVATED && current.androidLowMemory != true) {
                add("RAM disponible reducida; Android administra la liberación de memoria.")
            }
            if (storage == PressureLevel.CRITICAL) {
                add("Almacenamiento casi lleno. Libera espacio voluntariamente antes de descargar juegos.")
            } else if (storage == PressureLevel.ELEVATED) {
                add("Almacenamiento disponible limitado; revisa el espacio antes de instalar juegos.")
            }
            if (trend == PressureTrend.INCREASING && ram != PressureLevel.UNKNOWN) {
                add("La RAM disponible está disminuyendo según lecturas recientes.")
            }
        }
        return ResourcePressureReport(ram, storage, ramAvailable, storageAvailable, trend, notes)
    }

    private fun headroomPercent(available: Long, total: Long): Int? {
        if (total <= 0L || available < 0L || available > total) return null
        return ((available.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun classify(percent: Int?, elevatedBelow: Int, criticalBelow: Int): PressureLevel =
        when {
            percent == null -> PressureLevel.UNKNOWN
            percent < criticalBelow -> PressureLevel.CRITICAL
            percent < elevatedBelow -> PressureLevel.ELEVATED
            else -> PressureLevel.NORMAL
        }

    private fun detectRamTrend(
        current: ResourcePressureSample,
        history: List<ResourcePressureSample>
    ): PressureTrend {
        val validTotal = current.totalRamMb.takeIf { it > 0L }
            ?: return PressureTrend.INSUFFICIENT_HISTORY
        val samples = (history.takeLast(4) + current)
            .filter { it.totalRamMb == validTotal && headroomPercent(it.availableRamMb, validTotal) != null }
        if (samples.size < 3) return PressureTrend.INSUFFICIENT_HISTORY
        val previous = headroomPercent(samples.first().availableRamMb, validTotal)
            ?: return PressureTrend.INSUFFICIENT_HISTORY
        val latest = headroomPercent(samples.last().availableRamMb, validTotal)
            ?: return PressureTrend.INSUFFICIENT_HISTORY
        return when {
            latest <= previous - 6 -> PressureTrend.INCREASING
            latest >= previous + 6 -> PressureTrend.RECOVERING
            else -> PressureTrend.STABLE
        }
    }
}
