# Ultra Sentinel — plan de aceptación SSS (ideas, SIN implementación)

> **Estado del documento:** propuesta / backlog de aceptación, no especificación de funciones ya completadas. Incorporado a partir de 12 sugerencias de Meta AI y contrastado contra el inventario de **35 capacidades US-001–US-035**. **Cero IDs US nuevos; cero código implementado por este plan.**

**Contexto comprobado al redactarlo:** PR [#168](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/168) fusionado; PR [#169](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/169) abierto y draft. El estado de referencia del trabajo es el SHA `98d5b99751961b9b54734bf23bfb7dbd18e106b0`. No usar `ebaa074` como SHA final de una futura APK ni reutilizar checks de un SHA anterior.

**Regla de deduplicación:** cada propuesta siguiente es una *ampliación de requisitos o demostración* de US ya existentes; si afecta varias áreas, se señala un **US propietario** y los US relacionados. El resumen «12/12 contempladas» significa rastreadas, **NO** implementadas, probadas o aprobadas.

## Matriz de los 12 puntos de Meta AI — un único propietario por objetivo

| # | Propuesta | US propietario (relacionados) | Situación al crear esta lista | Criterio de aceptación futuro, no realizado |
|---|---|---|---|---|
| 1 | Parser YAML semántico, alias y AST seguro | **US-035** (US-001, US-016) | Auditoría heurística reforzada en #169; casos puntuales de alias/flow corregidos con mecanismo fail-closed. **No hay un parser YAML semántico completo acreditado.** | Parser estructurado y versionado que interpreta YAML soportado por GitHub Actions; alias, merge keys, quoting Unicode, flow/block, expresiones, comentarios y contextos. Rechazar de manera explícita construcciones ambiguas o no soportadas. Corpus adversarial **independiente** y tests RED/GREEN que incluyan falsos positivos. |
| 2 | Cerrar observaciones de Codex | **US-016** (US-001, US-035) | Los 3 hilos Codex del SHA anterior fueron respondidos/resueltos en #169; falta revisión final **del SHA que se fusione**. | Cero hallazgos confirmados abiertos en el último SHA; cada observación reproducida RED/GREEN, independiente y con límites documentados. **Resolver un hilo no demuestra por sí solo la ausencia de errores.** |
| 3 | Validar re-evaluación Post-CI en main | **US-016** (US-017) | Existe `.github/workflows/ultra-sentinel-sss-post-ci.yml` en #169; no hay prueba operativa concluyente del disparador `workflow_run` desde `main`. | **Después de fusionar**, generar PR controlado y retrasar deliberadamente un CI de prueba; verificar re-evaluación sobre SHA correcto tras finalización, resumen/artefacto generado, permisos solo de lectura y nunca aprobación con CI parcial. |
| 4 | APK QA real → Sentry → Sentinel E2E | **US-021** (US-022, US-024, US-025) | SDK e ingesta sanitizada tienen código y mocks; falta corrida completa con API/proyecto real autorizado. | En entorno QA y con consentimiento, firmar APK de prueba desde SHA verificado **vigente**; activar crash **controlado solo en QA, nunca forzar crasheos en usuarios de producción**; confirmar recepción en Sentry, release/SHA, build firmado, API intake, correlación y artifact sanitizado. Un `UNKNOWN` nunca equivale a crash verificado. |
| 5 | Breadcrumbs y redacción de PII | **US-023** (US-021) | Sanitizador existe; falta auditoría de telemetría real Android y artefactos E2E. | Sembrar e-mails/tokens/cadenas privadas **ficticias**; verificar redacción en Sentry y en todo resumen/artifact/log público, control de consentimiento, retención mínima, kill switch y test de fuga negativa. |
| 6 | Extender Fixer a cuatro familias Kotlin | **US-026** (US-009, US-010) | Reparación verificable limitada de `System.gc()`; ampliar sigue pendiente. | Matriz de **4 familias**: GC forzado, coroutines fuera del lifecycle, bloqueo de hilo UI, logging privado. Para cada una: detección, parche solo de propuesta, RED/GREEN, reversibilidad, prueba de comportamiento y veto del Judge. **No hacer reemplazos literales inseguros:** `GlobalScope.launch`→`lifecycleScope.launch` solo si existe `LifecycleOwner` apropiado; `runBlocking`→`withContext(IO)` exige contexto `suspend`/reestructuración, no es sustitución universal; cambiar `Log` por `Timber` no protege PII sin redacción/eliminación. |
| 7 | Gate de autorización Hunter→Fixer→Judge | **US-027** (US-009, US-012) | Gate offline/conservador parcial, sin flujo de reparación end-to-end acreditado. | Detección → hipótesis → propuesta acotada en rama aislada → tests RED/GREEN → verificación del SHA y CI → revisión independiente → autorización humana explícita → PR supervisado. Probar rechazo, revocación, carreras y **cero autofusión**. |
| 8 | Mutation Lab Kotlin efectivo | **US-014** (US-013) | Infraestructura Kotlin opt-in/parcial; Mutation Lab JS no equivale a Kotlin. | Mutantes compilados sobre módulos Kotlin seleccionados y tests Android relevantes ejecutados; publicar killed/survived/inconclusive, timeouts, presupuesto y logs reproducibles. No contar `skipped` como aprobado. |
| 9 | Fuzz/chaos de concurrencia de voz | **US-028** (US-019, US-020) | Existe base de pruebas de estado y lifecycle; faltan escenarios hostiles completos en dispositivos/emuladores. | Semillas reproducibles de TTS y STT simultáneos, cancelar/reanudar micrófono, callbacks tardíos, rotación, permisos revocados, red intermitente, procesos muertos. Medir bloqueos, reentrancia y fugas; probar Android real cuando corresponda. |
| 10 | Benchmark independiente vs CodeRabbit/Qodo | **US-032** (US-003, US-035) | Herramientas de métricas parciales, pero **sin comparativa externa independiente verificada**. | Crear corpus inicial de **≥50 PR etiquetados** con positivos y negativos; combinar sintéticos y **bugs reales no vistos**, validación ciega, resultados en mismas condiciones. Publicar TP/FP/FN/TN, precisión, recall, F1, falsos positivos, latencia, coste y falsos negativos críticos; cuando CodeRabbit/Qodo no estén disponibles, marcar `NO_COMPARABLE`, no inventar ranking. |
| 11 | Replay de crash real en Android | **US-024** (US-025, US-011) | Existen manifiestos de reproducción y tests de lifecycle; falta demostrar replay de un crash Sentry auténtico. | Con consentimiento y datos sanitizados, reconstruir escenario en emulador/dispositivo, repetir error original, test RED que falle antes del arreglo, GREEN después, comparar stack y release; declarar `INCONCLUSIVE` si el fabricante/condiciones impiden reproducir. |
| 12 | Roadmap auditado 35/35 con evidencia | **US-017** (gobernanza de todos los US) | Roadmap v1 está anclado a PR #167 y contiene anexos de #168; **no es prueba de 35/35 DONE**. | Migrar a tabla `DONE/PARTIAL/PENDING` con US único, commit y tests del último SHA, trazabilidad, fecha y propietario. Conservar histórico; no ascender por verde global si falta evidencia específica. |

## Orden de ejecución sugerido (NO se ha programado nada)

- **Gate de seguridad pre-merge de #169:** #1 y #2, más Android Build, Coverage, CodeRabbit/Codex para el **SHA final**; mantener fail-closed, secretos aislados y revisión humana. #3 **no puede acreditarse antes de estar activo en main**.
- **Verificación post-merge del motor:** #3; si falla, abrir reparación inmediata como PR separado, no declarar la automatización operativa.
- **Sentry controlado y privacidad:** #4, #5, #11. Estas pruebas se hacen en QA, no forzando NPE ni alterando dispositivos de usuarios.
- **Capacidad real de corregir:** #6, #7; cada patrón exige pruebas y veto de seguridad.
- **Calibración externa para SSS:** #8, #9, #10, #12.

No se asignan plazos rígidos de “semana 1–4”: los gates deben depender de evidencia, permisos y disponibilidad de entorno.

## Criterios adicionales de excelencia SIN duplicar IDs

1. **Seguridad de cadena de suministro (US-035):** corpus no filtrado de alias/anclas, Unicode, datos hostiles y expresiones compuestas; si el parser no comprende la semántica, el resultado debe ser `INCOMPLETE`, nunca verde.
2. **Rendimiento y fiabilidad del revisor (US-032):** medir p50/p95 de revisión, estabilidad por reintentos/reruns, consumo de recursos y correlación con SHA; no confundir tiempo total de runner con tiempo de análisis.
3. **Calidad de reparación (US-026/027):** comparar parches sugeridos con tests de contrato y regresiones, no solo patrones de sustitución; registrar candidatos rechazados y su razón.
4. **Privacidad de extremo a extremo (US-023/035):** separar fixture sensible, entrada del PR y artefactos públicos; pruebas de que ningún secreto/token llega a comentarios, resúmenes ni logs.
5. **Evidencia pública verificable (US-017/032):** cada supuesto avance S/SS/SSS requiere SHA, workflow, artefacto, corpus, versión, tamaño, tasa de errores y límites. **No hay clasificación SSS oficial ni benchmark comparativo aún.**

## Estado y política de actualización

- Esta página es un **plan de ideas/criterios de aceptación**, no cambios ejecutables ni implementación.
- **No se agregan US-036 en adelante:** los doce puntos tienen correspondencia con capacidades existentes.
- **No se cambian estados previos** a `DONE` por escribir la propuesta.
- La activación de un workflow en `main`, la llegada de un crash a Sentry y una comparación externa son hitos que exigen pruebas reales posteriores.
- Si Meta AI aporta una idea **realmente nueva** sin equivalencia US, investigar y asignar ID nuevo solo después de descartar duplicados.

Referencia histórica: [hoja maestra US-001–US-035](ULTRA_SENTINEL_MASTER_ROADMAP.md).
