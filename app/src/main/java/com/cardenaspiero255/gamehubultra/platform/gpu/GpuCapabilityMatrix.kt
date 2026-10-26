package com.cardenaspiero255.gamehubultra.platform.gpu

/** CAR-53: report only graphics capabilities verified by public signals. */
enum class CapabilityEvidence { UNKNOWN, VERIFIED, UNAVAILABLE }

data class GpuCapabilityObservation(
    val vendor: String?,
    val renderer: String?,
    val openGlEsRawVersion: String?,
    val vulkanHardwareLevel: Int?,
    val vulkanFeatureAvailable: Boolean? = null,
    val verifiedDriverSelectionAvailable: Boolean = false
)

data class GpuCapabilityReport(
    val vendor: String?,
    val renderer: String?,
    val openGlEsVersion: String?,
    val openGlEs: CapabilityEvidence,
    val vulkan: CapabilityEvidence,
    val alternateDriverSelectable: Boolean,
    val fingerprint: String,
    val explanations: List<String>
) {
    /** Observation is read-only: actual driver changes belong to CAR-54. */
    val driverWasChanged: Boolean get() = false
}

object GpuCapabilityMatrix {
    private val glesVersion = Regex(
        """^\s*(?:OpenGL\s+ES\s+)?([123])\.([0-2])(?:\b|\s|$)""",
        RegexOption.IGNORE_CASE
    )

    fun from(observation: GpuCapabilityObservation): GpuCapabilityReport {
        val vendor = normalized(observation.vendor)
        val renderer = normalized(observation.renderer)
        val glMatch = observation.openGlEsRawVersion?.let { glesVersion.find(it) }
        val glVersion = glMatch?.let { it.groupValues[1] + "." + it.groupValues[2] }
        val glStatus = if (glVersion == null) {
            CapabilityEvidence.UNKNOWN
        } else {
            CapabilityEvidence.VERIFIED
        }

        val vulkan = when {
            observation.vulkanFeatureAvailable == false -> CapabilityEvidence.UNAVAILABLE
            observation.vulkanFeatureAvailable == true -> CapabilityEvidence.VERIFIED
            observation.vulkanHardwareLevel != null && observation.vulkanHardwareLevel >= 0 ->
                CapabilityEvidence.VERIFIED
            else -> CapabilityEvidence.UNKNOWN
        }
        val alternateDriverSelectable = observation.verifiedDriverSelectionAvailable
        val explanations = buildList {
            if (vendor == null || renderer == null) {
                add("El fabricante o el renderizador GPU no está disponible en este dispositivo.")
            }
            if (glStatus == CapabilityEvidence.UNKNOWN) {
                add("La versión OpenGL ES no pudo verificarse mediante una API expuesta.")
            }
            when (vulkan) {
                CapabilityEvidence.UNKNOWN ->
                    add("Soporte de Vulkan no verificado: no inferirlo del nombre de la GPU.")
                CapabilityEvidence.UNAVAILABLE ->
                    add("Vulkan no está anunciado como disponible por las capacidades consultadas.")
                CapabilityEvidence.VERIFIED -> Unit
            }
            if (!alternateDriverSelectable) {
                add("No hay una ruta verificada para seleccionar un driver alternativo.")
            }
        }
        val fingerprint = listOf(
            vendor ?: "unknown",
            renderer ?: "unknown",
            glVersion ?: "gles-unknown",
            when (vulkan) {
                CapabilityEvidence.VERIFIED -> "vulkan-supported"
                CapabilityEvidence.UNAVAILABLE -> "vulkan-absent"
                CapabilityEvidence.UNKNOWN -> "vulkan-unverified"
            }
        ).joinToString("|")
        return GpuCapabilityReport(
            vendor, renderer, glVersion, glStatus, vulkan,
            alternateDriverSelectable, fingerprint, explanations
        )
    }

    private fun normalized(text: String?): String? =
        text?.trim()?.replace(Regex("""\s+"""), " ")?.takeIf { it.isNotBlank() }
}
