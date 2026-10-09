# Ultra Sentinel — hoja maestra de mejoras (v1)

> **Fuente única de seguimiento del PR #167:** 20 capacidades con código en la rama (12 implementadas en su alcance actual, 8 parciales) + 15 ampliaciones pendientes = **35 elementos rastreables**. **No significa 35 ideas implementadas ni 100 % de OMEGA.** Este inventario es actualizable, no un límite absoluto de ideas futuras.

Repositorio: `cardenaspiero255-lang/gamehub-ultra` · Rama: `feature/ultra-sentinel-auto-review` · Base auditada: `f3f7696d37c4cbdff73979e13fc789504d19d255` · PR: https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/167

## Criterios de estado

- **IMPLEMENTADA_EN_PR (12):** hay código y pruebas o validaciones para la función **en el alcance concreto indicado**. Está en la rama del PR; su workflow de producción puede necesitar fusión para activarse.
- **PARCIAL_EN_PR (8):** existe implementación inicial real, pero la funcionalidad completa anunciada depende de activación, prueba de extremo a extremo, casos adicionales o integración.
- **PENDIENTE (15):** objetivo planificado o propuesto para la siguiente etapa, **sin implementación completa dentro de PR #167**. No reclamarlo como disponible.
- **FUSIONADA / DESPLEGADA:** hitos de distribución independientes del estado anterior. **Ninguno de los elementos nuevos de este PR está fusionado hasta que GitHub confirme la fusión.**
- **Condición de salida:** no dar un elemento por terminado sin pruebas relevantes, CI del último SHA, revisión de hallazgos vigentes y evidencia de límites/casos adversariales. El estado de GitHub se debe revisar de nuevo después de cada push.

## 01–20 · Capacidades que ya tienen código

| ID | Mejora verificable | Estado | Evidencia en PR #167 | Qué falta / criterio de aceptación |
|---|---|---|---|---|
| US-001 | Analizador offline de diffs y reglas de riesgo | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_core.cjs` | Reglas con pruebas de casos positivos, negativos y diffs parciales. |
| US-002 | Severidad, confianza heurística y evidencia por hallazgo | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_core.cjs` | Separar señales de hechos; comprobar límites, falsos positivos y trazabilidad. |
| US-003 | Benchmark etiquetado de aceptación para reglas existentes | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_benchmark.cjs` | Conservar casos positivos/negativos; no confundirlo con benchmark independiente. |
| US-004 | Hook pre-commit local y modo estricto optativo | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_precommit.cjs`, `.github/scripts/install_sentinel_hook.sh` | Validar que no modifica archivos, no descarga modelos ni filtra secretos. |
| US-005 | Grafo causal de antecedentes (Meta AI) | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_causal.cjs` | Validar candidatos con reproducción y bisect; no declarar culpables por coincidencia. |
| US-006 | Memoria de evidencias y playbooks | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_memory.cjs` | Persistencia controlada, versión/procedencia y actualización solo tras verificación. |
| US-007 | Frontier: mapa offline HTML/JSON del historial Git | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_frontier_map.cjs` | Generación local comprobable; activación manual del workflow tras integración. |
| US-008 | Regression Investigator por Git Bisect supervisado | PARCIAL_EN_PR | `.github/workflows/ultra-sentinel-regression-investigator.yml` | Demostración extremo a extremo con regresión real y endpoints buenos/malos válidos. |
| US-009 | Hunter → Fixer → Judge con veto de seguridad | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_orchestrator.cjs` | Cubrir varias familias de fallos con pruebas y revisión sin permisos de autofusión. |
| US-010 | Propuestas limitadas de fix.patch no ejecutadas | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_orchestrator.cjs`, `.github/scripts/ultra_sentinel_remediation.cjs` | Expandir más allá de FORCED_GC solo con parches verificables y de bajo riesgo. |
| US-011 | Manifiesto de reproducción desde fallos de CI (Meta AI) | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_replay.cjs`, `.github/workflows/ultra-sentinel-reproduction.yml` | Verificar SHA, jobs y pasos; no presentar metadatos como snapshot reproducido. |
| US-012 | Feedback humano autorizado y asociado a SHA (Meta AI) | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_feedback.cjs` | Aceptar/rechazar con permisos comprobados; sin autoentrenamiento ni autocambios. |
| US-013 | Mutation Lab JavaScript aislado (Meta AI) | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_mutation.cjs`, `.github/workflows/ultra-sentinel-mutation.yml` | Detectar mutantes seleccionados; publicar supervivientes/inconclusos sin inflar métrica. |
| US-014 | Mutation Lab Kotlin térmico opt-in (Meta AI) | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_kotlin_mutation.py`, `.github/workflows/ultra-sentinel-kotlin-mutation.yml` | Completar ejecución Android aislada y ampliar el conjunto de mutantes útiles. |
| US-015 | OMEGA: verificador finito del contrato de voz | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_omega.cjs`, `.github/sentinel-contracts/voice-retry.omega.json` | Verificar estados/transiciones y contrastar trazas Kotlin; sin afirmar prueba del runtime. |
| US-016 | Sentinel vs. Sentinel con validador de rama confiable | PARCIAL_EN_PR | `.github/scripts/ultra_sentinel_selfreview.cjs`, `.github/workflows/ultra-sentinel-self-review.yml` | Integrar a main y verificar ejecución independiente pull_request_target. |
| US-017 | Informes portátiles GitHub/ChatGPT con SHA y esquema | IMPLEMENTADA_EN_PR | `.github/scripts/ultra_sentinel_report.cjs` | Serialización y lectura segura; publicación automática solo tras instalar workflows en main. |
| US-018 | Consenso de revisores externos y control de SHA | PARCIAL_EN_PR | `.github/workflows/ultra-sentinel-auto-review.yml`, `.github/scripts/ultra_sentinel_review_sha.cjs` | Verificar proveedores activos y equivalencia de SHA; degradación transparente si faltan. |
| US-019 | Gate de voz con cierre terminal CLOSED | IMPLEMENTADA_EN_PR | `app/src/main/java/com/cardenaspiero255/gamehubultra/voice/VoiceRecognitionRetryGate.kt`, `.github/sentinel-contracts/voice-retry.omega.json` | Bloquear reintentos tardíos; pruebas de trazas y concurrencia. |
| US-020 | release() de SpeechRecognizer/TTS idempotente y resistente | IMPLEMENTADA_EN_PR | `app/src/main/java/com/cardenaspiero255/gamehubultra/voice/VoiceAssistantController.kt`, `app/src/test/java/com/cardenaspiero255/gamehubultra/voice/VoiceAssistantControllerRobolectricTest.kt` | Ignorar callbacks tardíos; limpieza independiente aunque callbacks fallen. |

## 21–35 · Próximas ampliaciones, no implementadas aún

| ID | Mejora propuesta | Estado | Evidencia | Criterio mínimo para implementarla |
|---|---|---|---|---|
| US-021 | Ingesta autorizada de incidentes Sentry hacia Sentinel | PENDIENTE | — | Conectar webhooks/API en canal con autenticación, mínimos permisos y deduplicación. |
| US-022 | Correlación de crash con versión APK, SHA y modelo Android | PENDIENTE | — | Unir eventos a builds verificadas y mostrar nivel de certeza con evidencia. |
| US-023 | Breadcrumbs y telemetría de voz/red con privacidad | PENDIENTE | — | Consentimiento, minimización, redacción y retención; nunca subir audio ni secretos por defecto. |
| US-024 | Reproducción instrumentada de crashes Android | PENDIENTE | — | Caso RED repetible en emulador/dispositivo; adjuntar artefactos sanitizados. |
| US-025 | Diagnóstico de causa raíz de crashes con pruebas RED | PENDIENTE | — | Distinguir hipótesis de causa confirmada mediante test, bisect o stack trazable. |
| US-026 | Reparación TDD automatizada para más patrones Android/Kotlin | PENDIENTE | — | Proponer parches en rama aislada, RED→GREEN, sin ejecutar código no confiable con secretos. |
| US-027 | Ciclo cerrado proponer→probar→revisar→autorizar PR | PENDIENTE | — | Auditor independiente, umbrales y permisos; jamás autofusionar sin autorización. |
| US-028 | Fuzzing y chaos tests de ciclo de vida y concurrencia | PENDIENTE | — | Probar micrófono, TTS, red y ciclos rápidos de cancelación con semillas reproducibles. |
| US-029 | OMEGA ampliado a nuevos módulos críticos | PENDIENTE | — | Especificaciones acotadas de permisos/red/estado; pruebas contra implementación real. |
| US-030 | Predicción proactiva de regresiones calibrada con datos | PENDIENTE | — | Señales y umbrales calibrados por plataforma; medir aciertos, falsos positivos y drift. |
| US-031 | Memoria dinámica de reparaciones confirmadas | PENDIENTE | — | Registrar decisión humana, prueba RED/GREEN, versión, caducidad y posibilidad de revertir. |
| US-032 | Benchmark independiente contra CodeRabbit/Qodo y baseline | PENDIENTE | — | Corpus etiquetado no filtrado: precisión, recall, tiempo, coste y tasa de regresiones. |
| US-033 | Panel Ultra Sentinel dentro de GameHub Ultra | PENDIENTE | — | Interfaz de solo lectura para salud, incidentes, historial y consentimiento. |
| US-034 | Canary de releases y recomendaciones de rollback seguro | PENDIENTE | — | Relacionar incidentes con despliegues; recomendar reversión, nunca ejecutarla sin aprobar. |
| US-035 | Red Team de Sentinel, cadena de suministro y prompt injection | PENDIENTE | — | Simular entradas hostiles, secretos, acciones GitHub y bypass de revisión, con pruebas negativas. |

## Rastreo específico de las ideas de Meta AI

Las cuatro ideas identificadas explícitamente en la descripción del PR son **subconjuntos** de las 20 capacidades existentes, no cuatro mejoras extra:

1. **Mutation Lab:** `US-013` (JS implementado, 14 mutantes detectados en la ejecución auditada) + `US-014` (plan Kotlin disponible, ejecución Android real opt-in).
2. **Grafo de antecedentes:** `US-005` (parcial; las correlaciones no prueban causalidad).
3. **Reproducción de fallos:** `US-011` (manifiesto de CI implementado; no se congelan emuladores ni se demuestra reproducción real). Objetivo superior: `US-024`.
4. **Feedback humano:** `US-012` (etiquetas autorizadas por SHA; no fine-tuning). Evolución: `US-031`.

Frontier, OMEGA y Sentinel vs. Sentinel quedan rastreados en `US-007`, `US-015`, `US-016` y sus futuras ampliaciones. Las 15 ideas 21–35 son **propuestas de evolución**, no atribuidas a Meta AI sin una fuente específica. Este inventario recoge las ideas comprobables disponibles; si se recuperan nuevas ideas concretas de conversaciones anteriores, se añadirán con ID nuevo y sin sobrescribir el historial.

## Hechos y límites contrastados

- `ultra_sentinel_orchestrator.cjs` permite parche exacto solo para el patrón `FORCED_GC`, no reparación general automática.
- `ultra_sentinel_replay.cjs` registra metadatos de fallos CI y receta sugerida, no una máquina virtual o sesión de Android capturada.
- `ultra_sentinel_memory.cjs` contiene playbooks/evidencia verificada y búsqueda determinista; no es un modelo que aprende solo.
- `ultra_sentinel_omega.cjs` valida un **modelo finito de 3 estados y 12 transiciones**, contrastado con 16.384 trazas Kotlin. No prueba formalmente todo el runtime Android.
- Kotlin Mutation Lab tiene un planificador comprobado; el job `Android thermal unit mutation / explicitly approved` es opt-in, no equivale a ejecución completa si aparece `skipped`.
- `ultra-sentinel-self-review.yml` ejecuta pruebas del candidato; la revisión independiente basada en `main` no queda activa hasta que el workflow exista en `main`.
- GameHub Ultra ya usa SDK Sentry mediante `GameHubUltraApplication.kt`; **Sentinel aún no consume automáticamente sus eventos** (`US-021`).
- El último SHA de código auditado `f3f7696d37c4` pasó seis workflows, Android Build y Coverage. La cobertura del parche fue aprobada por Codecov; CodeRabbit revisaba aún el PR al preparar este inventario. **Los checks de un commit previo no validan automáticamente uno posterior.**
- Nunca ejecutar PRs ajenos con secretos, autopublicar APK, autofusionar `main` o prometer ausencia absoluta de crashes.

## Prioridad de trabajo y actualizaciones

**P0, seguridad y conexión:** terminar validación del PR, fusionar solo tras criterios satisfechos; activar `US-016` y conectar `US-021` con permisos mínimos, consentimiento y redacción de datos.

**P1, diagnósticos reales:** avanzar `US-022`–`US-025` y consolidar las capacidades parciales `US-005`, `US-008`, `US-011` y `US-014`.

**P2, reparación y defensa:** `US-026`–`US-029`, `US-035`; exigir pruebas RED/GREEN, revisión independiente y autorización humana.

**P3, confiabilidad y producto:** `US-030`–`US-034`, con métricas independientes y respeto de privacidad.

**Regla de mantenimiento:** conservar IDs y evidencia, no pasar PARCIAL a IMPLEMENTADA solo porque CI esté verde; añadir nuevas filas para ideas nuevas y actualizar los totales con el mismo criterio. No eliminar controles ni bajar umbrales para conseguir un “100 %” aparente.

## Evolución posterior a PR #167 — PR #168 (en validación)

El PR #167 fue fusionado en `main` (commit `fe62f6b4`). Este nuevo desarrollo se trabaja
en un PR **independiente, inicialmente borrador**:
https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/168.

**No actualizar el inventario a 35/35 por código parcial.** Se requieren resultados del
SHA final, autorización humana y, para pruebas Android o incidentes Sentry, ejecución real.

### Avances concretos en el primer bloque del PR #168

| IDs | Implementación añadida | Estado real / límites |
|---|---|---|
| US-021 | `ultra_sentinel_sentry_ingest.cjs` y workflow manual read-only Sentry | **Parcial**: requiere `SENTRY_AUTH_TOKEN` de solo lectura, vars de organización/proyecto, consentimiento y prueba real de API. No hay webhooks aún. |
| US-022 | `correlateBuilds` enlaza huellas de incidentes con SHA y estado aportados por CI | **Parcial**: la función no consulta ni verifica de manera independiente la API GitHub; se marca `ci-input-matched`. |
| US-023 | `sanitizeBreadcrumbs` aplica consentimiento explícito y lista estricta de eventos voz/red/temperatura | **Parcial**: sanitizador en Sentinel, no integrado aún con instrumentación Android de producción. |
| US-025, US-030 | Evaluación conservadora de picos con umbrales centralizados y estado `INSUFFICIENT_EVIDENCE` | **Parcial**: no confirma causalidad, carece de calibración longitudinal y pruebas RED de crashes de dispositivos. |
| US-027 | `evaluateRepairGate` exige SHA, RED/GREEN, CI verde y revisión humana independiente | **Parcial**: bloqueador puro/offline; falta conectarlo a propuestas de PR, verificación API real y aprobación GitHub. Nunca fusiona automáticamente. |
| US-028, US-035 | 7.500 casos adversariales con semillas reproducibles, consentimiento, seguridad de entradas y fail-closed | **Parcial**: cubre JavaScript del motor; faltan chaos tests en emuladores, app lifecycle y más clases supply-chain. |
| US-029 | `ultra_sentinel_omega_access.cjs`: modelo finito de permisos/revocación (4 estados, 5 eventos) | **Parcial**: modelo abstracto, sin prueba de equivalencia con un componente Kotlin real. |
| US-031 | `recordVerifiedRepair` registra una declaración humana limitada por caducidad y SHA | **Parcial**: estado real `PROVENANCE_RECORDED`, no demuestra por sí mismo RED/GREEN con GitHub ni persiste entre runners. |
| US-032 | `ultra_sentinel_comparative.cjs`: precisión, recall, F1, latencia y coste solo con corpus y resultados suministrados | **Parcial**: falta corpus externo independiente y ejecuciones reales de CodeRabbit/Qodo; no inventar comparaciones. |
| US-034 | Recomendación informativa `INVESTIGATE_ROLLBACK` con umbral conservador y CI suministrado | **Parcial**: sin canary de Play Store ni autorización/despliegue o rollback automático. |

### Elementos no cerrados

- US-005, 006, 008, 009, 010, 014, 018: requieren ampliaciones y pruebas
  independientes específicas, no pasar de **PARCIAL** a **IMPLEMENTADA** por el PR #168.
- US-016: workflow confiable incorporado a `main`; validar el job de revisión
  independiente en PR #168 y no confundir ejecución con ausencia de vulnerabilidades.
- US-024 y US-026: reproducción real en emulador y reparación TDD Android no implementadas.
- US-033: panel Android de Ultra Sentinel no implementado.
- US-021, 022, 023, 025, 027–032 y 034–035 siguen sujetos a las limitaciones de tabla.

### Contratos de seguridad innegociables

1. El token Sentry solo se lee en trabajo manual sobre `main` y requiere consentimiento;
   no hay datos personales, pila de crash ni audio en artefactos; retención máxima 7 días.
2. Nada de PR auto-merge, parcheo en producción, rollback automático o ejecutar código no confiable con secretos.
3. Los SHAs y estados presentados por un cliente no equivalen a verificación
   criptográfica o lectura independiente de GitHub.
4. Código local y tests pasando no certifican una ingesta Sentry auténtica ni crashes
   Android reproducidos. Mantener el PR borrador si falta CI final.

### Avances adicionales integrados en PR #168

| Mejora | Evidencia añadida | Estado de validación real |
|---|---|---|
| US-005 / US-008 | `ultra_sentinel_bisect_guard.cjs` y Regression Investigator | Distingue fallos de tests de Maven 429, timeout o compilación; exige GOOD verde y BAD rojo. **No se ha ejecutado aún un bisect completo de una regresión real.** |
| US-021 / US-022 | `ultra_sentinel_evidence.cjs`, pruebas negativas y exportador Sentry | Verifica resultados directamente en GitHub API para asociar SHAs completos. Requiere token Sentry autorizado y ejecución manual real tras su fusión a `main`. |
| US-024 / US-028 | `MainActivitySentinelResilienceTest.kt` | Prueba instrumentada de arranque, segundo plano, reanudación y recreación. No demuestra replay de cualquier crash ni de SpeechRecognizer del fabricante. |
| US-033 | `SentinelStatusCard.kt` y test Compose integrados en Ajustes | Panel Android de **solo lectura** enlazado a GitHub; aún no muestra feed autenticado de incidentes ni resultados en vivo dentro de la app. |
| US-035 | 7.500 pruebas adversariales con semillas y pruebas de red, secretos, SHA y consentimiento | Cobertura JS del motor; falta red team end-to-end de proveedores y cadena de suministro. |

**Servicios externos y datos:** no utilizar token Sentry de escritura. Configurar
`SENTRY_ORG_SLUG`, `SENTRY_PROJECT_SLUG` y `SENTRY_AUTH_TOKEN` con permisos mínimos
en GitHub Actions y autorización explícita antes de consultar incidentes.
Las pruebas simuladas no confirman la existencia de esas credenciales.

**No declarar 35/35 completas** por superar un build o por disponer de código
inicial. La aceptación requiere pruebas de integración reales, trazas RED/GREEN
de bugs reproducibles, seguridad, revisión independiente y aprobación humana.
