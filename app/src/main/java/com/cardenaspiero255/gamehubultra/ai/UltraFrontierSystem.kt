package com.cardenaspiero255.gamehubultra.ai

import java.util.Locale

/**
 * Policy-driven orchestration layer that coordinates Ultra's existing local intelligence,
 * verified research, memory, telemetry and typed tool boundaries.
 *
 * The Frontier layer does not replace those subsystems. It decides which capabilities are
 * necessary, how much work may be spent on the request, which fallbacks are safe and what
 * evidence must exist before an answer or state-changing action can be accepted.
 */
enum class UltraFrontierLane {
    LOCAL_FAST,
    LOCAL_DELIBERATE,
    VERIFIED_RESEARCH,
    DEEP_RESEARCH,
    TOOL_ACTION,
    BLOCKED
}

enum class UltraFrontierSpecialist {
    ROUTER,
    LOCAL_REASONER,
    MEMORY,
    TELEMETRY,
    RESEARCH,
    SYNTHESIZER,
    MULTIMODAL,
    TOOL_GATEWAY,
    SAFETY_GATE,
    VERIFIER,
    CRITIC
}

enum class UltraFrontierFallback {
    LOCAL_SAFE,
    ABSTAIN,
    NONE
}

enum class UltraFrontierAttachmentKind {
    SCREENSHOT,
    IMAGE,
    LOG,
    TELEMETRY,
    DOCUMENT
}

data class UltraFrontierAttachment(
    val kind: UltraFrontierAttachmentKind,
    val label: String? = null
)

data class UltraFrontierActionRequest(
    val toolId: String,
    val mutatesState: Boolean
) {
    init {
        require(toolId.isNotBlank()) { "Frontier action tool id must not be blank." }
    }
}

data class UltraFrontierRequest(
    val message: String,
    val query: UltraGeneralQueryRequest,
    val networkAvailable: Boolean = true,
    val memoryAvailable: Boolean = true,
    val telemetryAvailable: Boolean = false,
    val attachments: List<UltraFrontierAttachment> = emptyList(),
    val action: UltraFrontierActionRequest? = null
) {
    init {
        require(message.isNotBlank()) { "Frontier request message must not be blank." }
    }
}

data class UltraFrontierStep(
    val specialist: UltraFrontierSpecialist,
    val purpose: String,
    val mandatory: Boolean = true,
    val requiresNetwork: Boolean = false
)

data class UltraFrontierPolicy(
    val fastSourceBudget: Int = 4,
    val verifiedSourceBudget: Int = 6,
    val deepSourceBudget: Int = 10,
    val verifiedResearchPassBudget: Int = 2,
    val deepResearchPassBudget: Int = 3,
    val researchRetrySourceBudgetStep: Int = 2,
    val minimumVerifiedSources: Int = 1,
    val minimumFreshSources: Int = 2,
    val minimumDeepSources: Int = 2,
    val maximumPlanSteps: Int = 12,
    val alwaysCritiqueFinalAnswer: Boolean = true,
    val requireConfirmationForMutations: Boolean = true
) {
    init {
        require(fastSourceBudget >= 0)
        require(verifiedSourceBudget >= 2)
        require(deepSourceBudget >= verifiedSourceBudget)
        require(verifiedResearchPassBudget >= 1)
        require(deepResearchPassBudget >= verifiedResearchPassBudget)
        require(researchRetrySourceBudgetStep >= 0)
        require(minimumVerifiedSources >= 1)
        require(minimumFreshSources >= minimumVerifiedSources)
        require(minimumDeepSources >= minimumVerifiedSources)
        require(minimumVerifiedSources <= verifiedSourceBudget)
        require(minimumFreshSources <= verifiedSourceBudget)
        require(minimumDeepSources <= deepSourceBudget)
        require(maximumPlanSteps >= 4)
    }
}

data class UltraFrontierPlan(
    val lane: UltraFrontierLane,
    val steps: List<UltraFrontierStep>,
    val fallback: UltraFrontierFallback,
    val sourceBudget: Int,
    val maxSourceBudget: Int,
    val sourceBudgetStep: Int,
    val minimumDistinctSources: Int,
    val researchPassBudget: Int,
    val requiresFreshResearch: Boolean,
    val requiresUserConfirmation: Boolean,
    val autoExecuteMutation: Boolean,
    val blockedReason: String? = null
) {
    init {
        require(steps.isNotEmpty()) { "Frontier plan must contain at least one step." }
        require(sourceBudget >= 0)
        require(maxSourceBudget >= sourceBudget)
        require(sourceBudgetStep >= 0)
        require(minimumDistinctSources >= 0)
        require(researchPassBudget >= 0)
        if (lane == UltraFrontierLane.BLOCKED) {
            require(!blockedReason.isNullOrBlank()) {
                "Blocked Frontier plans must explain why execution cannot proceed."
            }
        }
        if (autoExecuteMutation) {
            require(!requiresUserConfirmation) {
                "A mutation cannot both auto-execute and require confirmation."
            }
        }
    }
}

class UltraFrontierOrchestrator(
    val policy: UltraFrontierPolicy = UltraFrontierPolicy()
) {
    fun plan(request: UltraFrontierRequest): UltraFrontierPlan {
        val query = request.query
        val action = request.action

        if (action != null) {
            return actionPlan(request)
        }

        val researchRequired =
            query.verificationMode == UltraVerificationMode.REQUIRED ||
                query.kind == UltraGeneralQueryKind.CURRENT_DATA ||
                query.kind == UltraGeneralQueryKind.COMPARISON_RESEARCH
        if (researchRequired && !request.networkAvailable) {
            return blockedResearchPlan(request)
        }

        val lane = when {
            query.kind == UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                UltraFrontierLane.DEEP_RESEARCH

            researchRequired ->
                UltraFrontierLane.VERIFIED_RESEARCH

            shouldDeliberateLocally(request.message, request.attachments) ->
                UltraFrontierLane.LOCAL_DELIBERATE

            else ->
                UltraFrontierLane.LOCAL_FAST
        }

        val steps = buildList {
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.ROUTER,
                    purpose = "Clasificar intención, frescura, coste y riesgo."
                )
            )

            if (request.memoryAvailable && shouldUseMemory(request)) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.MEMORY,
                        purpose = "Recuperar contexto útil sin convertir recuerdos en hechos verificados.",
                        mandatory = false
                    )
                )
            }

            if (request.telemetryAvailable || request.attachments.any {
                    it.kind == UltraFrontierAttachmentKind.TELEMETRY
                }
            ) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.TELEMETRY,
                        purpose = "Incorporar señales actuales del dispositivo y la sesión.",
                        mandatory = false
                    )
                )
            }

            if (request.attachments.isNotEmpty()) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.MULTIMODAL,
                        purpose = "Interpretar capturas, imágenes, logs o documentos como evidencia adicional."
                    )
                )
            }

            when (lane) {
                UltraFrontierLane.LOCAL_FAST,
                UltraFrontierLane.LOCAL_DELIBERATE -> {
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.LOCAL_REASONER,
                            purpose = if (lane == UltraFrontierLane.LOCAL_FAST) {
                                "Responder conocimiento estable por la ruta local de mínima latencia."
                            } else {
                                "Razonar localmente antes de escalar a servicios externos."
                            }
                        )
                    )
                }

                UltraFrontierLane.VERIFIED_RESEARCH -> {
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.RESEARCH,
                            purpose = "Consultar investigación verificada con datos suficientemente recientes.",
                            requiresNetwork = true
                        )
                    )
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.VERIFIER,
                            purpose = "Comprobar evidencia, fuentes, confianza y frescura."
                        )
                    )
                }

                UltraFrontierLane.DEEP_RESEARCH -> {
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.RESEARCH,
                            purpose = "Recoger evidencia diversa para comparación o investigación compleja.",
                            requiresNetwork = true
                        )
                    )
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.SYNTHESIZER,
                            purpose = "Resolver contradicciones y sintetizar hallazgos entre fuentes/modelos."
                        )
                    )
                    add(
                        UltraFrontierStep(
                            specialist = UltraFrontierSpecialist.VERIFIER,
                            purpose = "Validar que la síntesis conserve evidencia y no exceda su confianza."
                        )
                    )
                }

                UltraFrontierLane.TOOL_ACTION,
                UltraFrontierLane.BLOCKED -> Unit
            }

            if (policy.alwaysCritiqueFinalAnswer) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.CRITIC,
                        purpose = "Revisar respuesta final, detectar abstenciones genéricas y evitar afirmaciones no sustentadas."
                    )
                )
            }
        }.take(policy.maximumPlanSteps)

        val sourceBudget = when (lane) {
            UltraFrontierLane.DEEP_RESEARCH -> policy.deepSourceBudget
            UltraFrontierLane.VERIFIED_RESEARCH -> policy.verifiedSourceBudget
            else -> policy.fastSourceBudget
        }
        val researchPassBudget = when (lane) {
            UltraFrontierLane.DEEP_RESEARCH -> policy.deepResearchPassBudget
            UltraFrontierLane.VERIFIED_RESEARCH -> policy.verifiedResearchPassBudget
            else -> 0
        }
        val maxSourceBudget = when (lane) {
            UltraFrontierLane.VERIFIED_RESEARCH,
            UltraFrontierLane.DEEP_RESEARCH -> policy.deepSourceBudget
            else -> sourceBudget
        }
        val sourceBudgetStep = when (lane) {
            UltraFrontierLane.VERIFIED_RESEARCH,
            UltraFrontierLane.DEEP_RESEARCH -> policy.researchRetrySourceBudgetStep
            else -> 0
        }
        val minimumDistinctSources = when (lane) {
            UltraFrontierLane.VERIFIED_RESEARCH -> if (query.requiresFreshData) {
                maxOf(
                    policy.minimumVerifiedSources,
                    policy.minimumFreshSources
                )
            } else {
                policy.minimumVerifiedSources
            }
            UltraFrontierLane.DEEP_RESEARCH -> policy.minimumDeepSources
            else -> 0
        }
        val fallback = when {
            lane == UltraFrontierLane.VERIFIED_RESEARCH ||
                lane == UltraFrontierLane.DEEP_RESEARCH ->
                UltraFrontierFallback.ABSTAIN

            query.verificationMode == UltraVerificationMode.OPTIONAL ->
                UltraFrontierFallback.LOCAL_SAFE

            else ->
                UltraFrontierFallback.LOCAL_SAFE
        }

        return UltraFrontierPlan(
            lane = lane,
            steps = steps,
            fallback = fallback,
            sourceBudget = sourceBudget,
            maxSourceBudget = maxSourceBudget,
            sourceBudgetStep = sourceBudgetStep,
            minimumDistinctSources = minimumDistinctSources,
            researchPassBudget = researchPassBudget,
            requiresFreshResearch = query.requiresFreshData,
            requiresUserConfirmation = false,
            autoExecuteMutation = false
        )
    }

    private fun actionPlan(request: UltraFrontierRequest): UltraFrontierPlan {
        val action = requireNotNull(request.action)
        val mutating = action.mutatesState
        val confirmationRequired =
            mutating && policy.requireConfirmationForMutations
        val steps = buildList {
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.ROUTER,
                    purpose = "Separar una petición de herramienta de una consulta conversacional."
                )
            )
            if (request.memoryAvailable && shouldUseMemory(request)) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.MEMORY,
                        purpose = "Usar contexto sólo como ayuda de planificación.",
                        mandatory = false
                    )
                )
            }
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.SAFETY_GATE,
                    purpose = if (mutating) {
                        "Validar allowlist, permisos y confirmación antes de cualquier cambio de estado."
                    } else {
                        "Validar que la herramienta solicitada sea segura y compatible."
                    }
                )
            )
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.TOOL_GATEWAY,
                    purpose = "Ejecutar únicamente mediante contratos tipados y resultados sanitizados."
                )
            )
            if (policy.alwaysCritiqueFinalAnswer) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.CRITIC,
                        purpose = "Comprobar que el resultado describa sólo acciones realmente ejecutadas."
                    )
                )
            }
        }.take(policy.maximumPlanSteps)

        return UltraFrontierPlan(
            lane = UltraFrontierLane.TOOL_ACTION,
            steps = steps,
            fallback = UltraFrontierFallback.NONE,
            sourceBudget = 0,
            maxSourceBudget = 0,
            sourceBudgetStep = 0,
            minimumDistinctSources = 0,
            researchPassBudget = 0,
            requiresFreshResearch = false,
            requiresUserConfirmation = confirmationRequired,
            // Frontier never grants itself permission to mutate state. The caller must
            // explicitly authorize and then invoke the existing typed tool boundary.
            autoExecuteMutation = false
        )
    }

    private fun blockedResearchPlan(
        request: UltraFrontierRequest
    ): UltraFrontierPlan {
        val steps = buildList {
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.ROUTER,
                    purpose = "Detectar que la solicitud exige información actual o verificada."
                )
            )
            add(
                UltraFrontierStep(
                    specialist = UltraFrontierSpecialist.SAFETY_GATE,
                    purpose = "Impedir que conocimiento local potencialmente obsoleto se presente como dato actual."
                )
            )
            if (policy.alwaysCritiqueFinalAnswer) {
                add(
                    UltraFrontierStep(
                        specialist = UltraFrontierSpecialist.CRITIC,
                        purpose = "Emitir una abstención específica y recuperable."
                    )
                )
            }
        }

        return UltraFrontierPlan(
            lane = UltraFrontierLane.BLOCKED,
            steps = steps,
            fallback = UltraFrontierFallback.ABSTAIN,
            sourceBudget = policy.verifiedSourceBudget,
            maxSourceBudget = policy.deepSourceBudget,
            sourceBudgetStep = 0,
            minimumDistinctSources = if (request.query.requiresFreshData) {
                maxOf(
                    policy.minimumVerifiedSources,
                    policy.minimumFreshSources
                )
            } else {
                policy.minimumVerifiedSources
            },
            researchPassBudget = 0,
            requiresFreshResearch = request.query.requiresFreshData,
            requiresUserConfirmation = false,
            autoExecuteMutation = false,
            blockedReason =
                "La consulta requiere conexión para verificar información actual y no es seguro inventar ni usar datos obsoletos."
        )
    }

    private fun shouldUseMemory(request: UltraFrontierRequest): Boolean {
        val normalized = request.message.lowercase()
        return normalized.length >= 24 ||
            normalized.contains("mi ") ||
            normalized.contains("antes") ||
            normalized.contains("recuerda") ||
            normalized.contains("otra vez") ||
            normalized.contains("y ") ||
            request.attachments.isNotEmpty()
    }

    private fun shouldDeliberateLocally(
        message: String,
        attachments: List<UltraFrontierAttachment>
    ): Boolean {
        if (attachments.isNotEmpty()) return true
        val normalized = message.lowercase()
        return normalized.length >= 180 ||
            normalized.contains("por que") ||
            normalized.contains("por qué") ||
            normalized.contains("explica paso") ||
            normalized.contains("analiza") ||
            normalized.contains("razona")
    }
}

data class UltraFrontierCandidate(
    val message: String,
    val verified: Boolean,
    val confidence: UltraAnswerConfidence?,
    val sources: List<String>,
    val independentSourceCount: Int = 0,
    val abstained: Boolean,
    val retryable: Boolean = false,
    val attempt: Int = 1
) {
    init {
        require(attempt >= 1)
    }
}

enum class UltraFrontierVerdict {
    ACCEPT,
    RETRY_RESEARCH,
    FALLBACK_LOCAL,
    ABSTAIN
}

/**
 * Small deterministic quality gate. It deliberately judges observable answer properties
 * rather than hidden chain-of-thought. That keeps the decision testable and auditable.
 */
class UltraFrontierCritic {
    fun review(
        plan: UltraFrontierPlan,
        candidate: UltraFrontierCandidate
    ): UltraFrontierVerdict {
        val weak = candidate.abstained ||
            candidate.message.isBlank() ||
            isGenericFailure(candidate.message)

        if (plan.lane == UltraFrontierLane.BLOCKED) {
            return UltraFrontierVerdict.ABSTAIN
        }

        val verifiedResearchLane =
            plan.lane == UltraFrontierLane.VERIFIED_RESEARCH ||
                plan.lane == UltraFrontierLane.DEEP_RESEARCH

        if (verifiedResearchLane) {
            val distinctSources = candidate.sources
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { it.lowercase(Locale.ROOT) }
                .distinct()
                .count()
            val independentSources =
                candidate.independentSourceCount
                    .takeIf { it > 0 }
                    ?: distinctSources
            val evidenceInsufficient =
                !candidate.verified ||
                    candidate.confidence == UltraAnswerConfidence.LOW ||
                    independentSources < plan.minimumDistinctSources
            if (weak || evidenceInsufficient) {
                return if (
                    candidate.retryable &&
                    candidate.attempt < plan.researchPassBudget &&
                    plan.researchPassBudget > 1
                ) {
                    UltraFrontierVerdict.RETRY_RESEARCH
                } else {
                    UltraFrontierVerdict.ABSTAIN
                }
            }
            return UltraFrontierVerdict.ACCEPT
        }

        if (weak) {
            return when (plan.fallback) {
                UltraFrontierFallback.LOCAL_SAFE ->
                    UltraFrontierVerdict.FALLBACK_LOCAL
                UltraFrontierFallback.ABSTAIN ->
                    UltraFrontierVerdict.ABSTAIN
                UltraFrontierFallback.NONE ->
                    UltraFrontierVerdict.ABSTAIN
            }
        }

        return UltraFrontierVerdict.ACCEPT
    }

    fun isUsefulLocalAnswer(message: String): Boolean =
        message.isNotBlank() && !isGenericFailure(message)

    private fun isGenericFailure(message: String): Boolean {
        val normalized = message.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        return GENERIC_FAILURE_MARKERS.any(normalized::contains)
    }

    private companion object {
        val GENERIC_FAILURE_MARKERS = listOf(
            "no pude verificarlo con suficiente confianza",
            "no pudo verificarlo con suficiente confianza",
            "no estoy seguro de esa respuesta",
            "servicio de consulta no está disponible",
            "servicio de consulta no esta disponible",
            "asistente general online no está disponible",
            "asistente general online no esta disponible",
            "hola, soy ultra",
            "hola soy ultra",
            "puedo ayudarte con juegos y optimización",
            "puedo ayudarte con juegos y optimizacion"
        )
    }
}
