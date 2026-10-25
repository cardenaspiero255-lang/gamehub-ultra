# EVO Frontier V3: operativo de cierre de fases 8 y 9

**Auditoría iniciada:** 2026-10-25. **Fuente canónica:** https://github.com/cardenaspiero255-lang/gamehub-ultra/issues/207
**Estado:** ninguna de las dos fases está certificada como cerrada en este documento. Fase 8 en cierre condicionado; fase 9 pendiente de validaciones de lanzamiento y dispositivo.

## Regla de verdad y secuencia

- Completar primero la fase 8 con evidencias actuales. No alterar CAR-29, quitar gates, reescribir historia, tratar SKIPPED como éxito ni sustituir la revisión autenticada.
- Solo abrir el gate de cierre de fase 9 cuando fase 8 tenga recibos válidos, mismo SHA o correspondencia autenticada, PR necesarios fusionados mediante procedimiento protegido y main revalidada.
- El hecho de que un PR esté fusionado NO constituye por sí solo certificación funcional ni física. Mantener las casillas del issue #207 sin marcar hasta observar pruebas apropiadas.
- Registrar la relación exacta entre base, SHA de PR, SHAs de workflows protegidos, blobs, suites, cobertura, resultados y firmas/autorización auténtica.

## Fase 8 — Ultra Sentinel, incidentes, TDD, rollback, Sentry y revisión protegida

| Bloque | Evidencia ya disponible | Estado verificable al 25/10 | Próximo gate bloqueante |
|---|---|---|---|
| Rollback persistente Android | PR #222 fusionado, documentación de E2E de rollback | PARCIAL: existe E2E en emulador y driver de cold restart, pero no hay recibo de reinicio real verificado | Ejecutar script de cold restart en emulador aislado; comprobar JUnit, PID distintos y hashes |
| Sentry release integrity | PR #223 fusionado | CÓDIGO INTEGRADO, delivery real no probado con esto | Comprobar evento UUID y release por API del proyecto correcto |
| Captura Sentry Android SDK | PR #224 fusionado | CÓDIGO INTEGRADO, prueba opt-in no implica delivery | Completar PR #228 y lectura exacta post-envío |
| Intake autenticado CI | PR #225, #226, #227 fusionados | INFRAESTRUCTURA INTEGRADA | Revalidar sobre SHA actual del PR sensible |
| Reautenticación post-upload de revisores | PR #229 fusionado | CORRECCIÓN INTEGRADA; reviews existentes reportan recibos SUCCESS de #221 | Reconciliar receipts con SHA exacto, sin reciclar aprobaciones de otro SHA |
| Redacción secrets CI protected | PR #221 draft, HEAD 3c328b6d2f40e1c9e10e361a843cd25ed2036b3f | BLOQUEADO: revisión técnica favorable es COMMENTED, no autoriza merge | Autorización personal autenticada del propietario, cumplimiento real de branch protection y full CAR-29 |
| Prueba entrega Sentry SDK main-only | PR #228 draft, HEAD 21d3a4c52cad47c6a33b2c85ec199298f71560c2 | BLOQUEADO/NO CERTIFICADO | Review y CI de seguridad; token lectura de eventos, secreto válido y E2E real desde trusted main |
| Ultra Sentinel triage y autorreparación controlada | Issue #207/Ultra Sentinel; diseño de fases | SIN ATESTACIÓN GLOBAL | Demostrar triage sobre fallo real, RED->GREEN, rollback autorizado y no autofusión |

Referencias directas: #221 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/221 ; #222 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/222 ; #223 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/223 ; #224 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/224 ; #228 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/228 ; #229 https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/229 .

### F8: ejecuciones concretas a exigir

1. Desde GitHub, autorizar conscientemente el delta protected de #221 como propietario real, respetando las restricciones vigentes de branch protection; un comentario realizado por el asistente no equivale a una aprobación humana autenticada.
2. Verificar que el revisor protegido y su recibo exact-blob correspondan al HEAD 3c328b6d... en el momento de la validación y que ninguna base confiable haya cambiado fuera de lo acreditado. Si SHA cambia, repetir revisión.
3. Revisión del workflow main-only de #228: sin disparadores PR con secretos; token con alcance mínimo para consulta y SHA de release asociado a main; aprobar solo si el modelo de confianza permanece cerrado.
4. Ejecutar evento sintético en emulador aislado API 35 con el SDK realmente inicializado; capturar UUID, leer el mismo UUID desde Sentry por API autorizada, comprobar proyecto y release exactos, tiempos y resultado.
5. Ejecutar en un emulador dedicado el driver de rollback ya existente: bash scripts/car29-verify-android-e2e.sh . Comprobar que hubo test JUnit ejecutado, dos PIDs genuinos diferentes y recibos con SHA/hashes. Nunca ejecutarlo en teléfono personal.
6. Validar desde main actualizado los jobs obligatorios Android, coverage, Supabase/research, reviewers y AppSec; mantener los runs históricos fallidos para auditoría.
7. Cierre F8 solo después de que todos los bloqueos de arriba tengan evidencia consultable, sin hallazgos P0/P1/P2 confirmados que afecten lanzamiento. Actualizar issue #207 con enlaces y aceptación limitada.

## Fase 9 — Release, AppSec, stress, latencia, factualidad y crash-free

### Matriz mínima propuesta (los umbrales de aceptación requieren acuerdo explícito; NO son mediciones)

| Evidencia requerida | Dispositivo/entorno | Resultado mínimo verificable |
|---|---|---|
| Pipeline full en main final | GitHub Actions protegido | Todos los jobs obligatorios en SUCCESS sobre mismo SHA, sin tests saltados indebidamente |
| Release artifact / instalación | API 35 y Android 13/14/15+ según disponibilidad | APK/AAB generado, instalado, abierto, actualizado/reinstalado, desinstalado y artefacto íntegro |
| AppSec | Static + tests Android + OWASP MASVS/MASTG | Sin hallazgos críticos abiertos; permisos, consentimiento, secretos y almacenamiento revisados |
| Latencias | Vivo V25 Pro y emulador separado | p50 y p95 reales de ASR a respuesta audible, muestras, método y condiciones documentadas |
| Interrupción | Android físico durante juego | Ultra para detiene TTS/consulta sin callback tardío y sin crash; negativa/hipótesis X4 no ejecuta acción |
| Voz 4.000+ caracteres | Android físico, TTS real | Audio completo o límites reportados; audio por parte no es certificación |
| Sin conexión y datos actuales | Android físico/simulación red | Conocimiento estable sin inventar, actualidad sin red abstiene, provenance por afirmación |
| FPS y pacing | SurfaceFlinger y juego abierto | medición de frame timings de capa atribuible o null; nunca estimar FPS desde Hz |
| Estrés y duración | juego real, pantalla encendida/apagada si se permite | cantidad de sesiones, fallos y condiciones medidas, no adjetivos sin números |
| Crash-free / ANR | Sentry eventos y métricas propias, o Android vitals si aplicación publicada | numerador/denominador reales y periodo identificable; ausencia de reporte no equivale a 100% |

### Pruebas de aceptación funcional ya registradas en #207

- Comparación RedMagic 11S Pro vs 12 Pro con fuentes independientes, precios/fecha/variante sin fusionar entidades.
- Mensaje mayor a 4000 caracteres sin TTS truncado; 'Ultra para' corta voz y tareas concurrentes.
- 'No desactives X4' y '¿Qué pasaría si activara X4?' jamás deben ejecutar un cambio de perfil.
- Motores ocupados, permiso micrófono denegado, pérdida de audio focus y cambio de juego no bloquean UI.
- Detectar contradicción 5000mAh vs 6000mAh; no inventar ping de juego ni FPS cuando hay solo datos de display.

### Protocolos existentes del repositorio

- Política general release: docs/CAR-29-RELEASE-QUALITY.md .
- E2E rollback: docs/quality/CAR29-ADAPTIVE-ROLLBACK-ANDROID-E2E.md .
- Benchmark SurfaceFlinger físico: docs/EVO-V3-PHASE6-FAST-BENCHMARK.md .
- Unit test benchmark: python -m unittest discover -s scripts/tests -p 'test_quick_game_benchmark.py' -v .
- Driver E2E desde emulador (NO físico): bash scripts/car29-verify-android-e2e.sh .

## Plantilla de recibo para cada prueba

Guardar registros en artefactos CI o carpeta privada (evitar subir PII/device IDs): fecha UTC, git HEAD, base sha, identidad del workflow, run URL + attempt, suite/caso, versión de Android, dispositivo autorizado o emulador, evento esperado, evento observado, resultado PASS/FAIL/NOT_RUN, logs y hashes, hallazgos, autor de aprobación y límites de validez.

## Reglas de decisión

- PASS = ejecución real sobre código vigente + recibo verificable + autorización aplicable.
- NOT_RUN = plan escrito, test disponible sin ejecutar, transcripción de YouTube, simulación sin dispositivo o token ausente.
- FAIL = excepción, evidencia incompleta, evento no recibido, 401/403, SHA divergente, permiso/contrato violado o error funcional reproducido.
- BLOCKED = falta autorización humana/documental real, branch protection, secretos verificados o dispositivo.

**Resultado de esta intervención:** investigación y runbook guardados en una rama de documentación; este acto no modifica código de producción ni declara cerradas F8/F9. Los cierres requieren evidencias posteriores auténticas.
