# CAR-29 · manual de programación confiable para cerrar #234 y #235

**Edición:** 2026-10-25. **Clase:** protocolo de ingeniería basado en investigación, no prueba de ejecución. **Ámbito:** PR #234 (reinicio real/rollback), #235 (Sentry emulador), CI de main. Archivo complementario: `docs/research/EVO-V3-RELIABLE-PROGRAMMING-YOUTUBE-20261025.md`. Mantener los gates actuales de Ultra Sentinel/CAR-29.

## Principio rector

Una CI más verde no demuestra calidad si no ejecuta el comportamiento que queremos verificar. Para cada hipótesis documentar `síntoma`, `primera causa observada`, `oráculo`, `cambio mínimo`, `caso negativo`, `SHA`, `run URL`, `resultado` y `limitación`. Distinguir `PASS`, `FAIL`, `NOT_RUN`, `BLOCKED`, `INFRA_FAILURE` sin traducir ninguno a otro.

## 0. Congelar estado y prevenir fusiones erróneas

1. Obtener PR HEAD SHA, main SHA, merge base y `compare main...head`. Registrar si el PR contiene nuevas modificaciones de workflow y qué reviewer exige revisión acotada.
2. Comprobar la diferencia de archivos y cada `blob SHA` protegido. Exigir que las pruebas y aprobaciones correspondan al HEAD **actual**.
3. Para cambios `.github/workflows/`, inspectar fuente, acciones pinneadas, condiciones `if`, permisos efectivos, checkout ref, acceso a secretos, artefactos y suministros externos.
4. Un revisor `INCOMPLETE` o `BLOCKER` impide avanzar. NO silenciar warnings, cambiar `continue-on-error`, tratar un `skipped` como éxito, aprobar para el bot, saltar required checks ni usar una fusión directa por API como evasión.
5. Al tocar HEAD invalidar todos los recibos y revisiones SHA-scoped antiguos; esperar pruebas nuevas; después de fusionar validar `main` nuevo. Si la rama está divergente, rebase/merge protegido y volver a probar (sin force push oculto).

## 1. Máquina de estados del #235 (Sentry)

| Paso | Invariante verificable | Evidencia correcta | Si falla... |
|---|---|---|---|
| SDK | SDK_ROOT, sdkmanager, emulator ejecutables; API35 x86_64 system.img/ramdisk | hashes/paths/versiones **sin** secretos | INFRA_FAILURE antes de tocar SDK Sentry |
| Host acelerado | `/dev/kvm` existe y tiene permisos mínimos del runner | verificación KVM y log del emulador | INFRA_FAILURE / no inventar soporte |
| AVD | AVD reconocido por `emulator -list-avds` | ID y configuración temporales | INFRA_FAILURE |
| Proceso emulador | PID hijo vivo, log de arranque, salida controlada | `kill -0` y log redactado al fallo | INFRA_FAILURE |
| ADB | exactamente un dispositivo `device`, `sys.boot_completed=1`, API=35 | `adb get-state`, propiedad del SO y no emuladores externos | INFRA_FAILURE, no basta un timeout grande |
| Instalación | APK y APK de test del mismo SHA, versión correcta | `pm path` y salida install | INSTRUMENTATION_BLOCKED |
| Test Sentry | instrumentación completa `OK (1 test)` y sin error | test XML/log no omitido | FAIL del SDK/test |
| Captura | UUID **generado por la prueba** ligado a release `gamehub-ultra@SHA` | solo UUID/release sintéticos | FAIL/captura |
| Ingestión | GET API Sentry mismo UUID en proyecto + release exactos | respuesta autenticada, scope mínimo `project:read` u otro válido | FAIL para 401/403/404/timeout/release cambiado |

**Oráculo rojo real:** trusted [run 40164135930](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40164135930) acabó exit 124 en boot ADB, por lo que **no** alcanzó captura ni verificación de Sentry. Ver el paso inicial fallido es más informativo que cambiar a ciegas tokens, Python o el SDK.

**Experimentos #235, en orden, sin bajar validación:** (1) comprobar que el workflow reconoce el host/SDK y crea AVD; (2) comparar boot contra la rutina funcional de `.github/workflows/android.yml`; (3) run en trusted main autorizado; (4) lectura exacta por API del evento real; (5) registrar fallo por primera etapa. Si no pasa (3), detener (4) como NOT_RUN. No reinterpretar un error de infraestructura como pérdida real de evento.

## 2. Máquina de estados del #234 (rollback Android)

| Paso | Condición falsable | Evidencia |
|---|---|---|
| Precondición | un único emulador API35, `ro.kernel.qemu=1`, ningún teléfono personal | propiedades Android medidas |
| Fixture | `GameHubRollbackPersistenceE2ETest#sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart` ejecutado fase prepare, no skipped | test JUnit EXACTO y log `OK (1 test)` |
| Antes STOP | PID válido y > 0 | `pidof package` |
| Force stop | proceso realmente desaparece | `am force-stop` + ausencia PID |
| Cold start | actividad vuelve y PID **distinto** | `am start -W` + segundo PID |
| Persistencia | fase verify recupera fixture de mismo test sin fallos | JUnit EXACTO + assertion rollback |
| Provenance | JSON receipt `gitSha` coincide con checkout immutable, hashes de cuatro logs + JUnit | JSON y artefactos hash 64 hex |
| Atestación | run protegido sobre main + validación independiente; nunca test autoafirmado | GitHub run + artifact y SHA |

**Tests RED previos recomendados:** PID igual; PID vacío; `adb get-state=offline`; `ro.kernel.qemu=0`; rollback verifica desde memoria efímera sin persistencia; JUnit ausente, duplicado o skipped; JSON alterado; SHA errado; log faltante; fichero ZIP extraño. Cada caso debe FALLAR; no limitarse a escenarios felices.

**Problema de revisión:** el primer #234 ejecutó reviewer con YAML `INCOMPLETE`. Corrección correcta = reducir expresividad no verificable del YAML o ampliar detector con pruebas independientes/autorización, **no** permitir `INCOMPLETE` como success. Confirmar por run del SHA actual que reviewers y AST verifican el contenido realmente integrado.

## 3. Main después de #221 y merge final de F8

El main que resulta de fusionar #221 fue `eefcfe985cf0d3dec136a2ba1db2d89e3ec6b402`. Checks por `push` para ese commit: [Android 40165888741](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40165888741), [Coverage 40165888709](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40165888709), [Sonar 40165888692](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40165888692). Estado a la consulta: Coverage **SUCCESS**, Sonar **SUCCESS**, Android **IN_PROGRESS** (puede cambiar; volver a consultar). Las suites antiguas de PR #221 no certifican `main` después de merge.

### Lista de decisión F8

- [ ] #235 autorizado, reviewers completos SUCCESS y fusionado por procedimiento normal.
- [ ] Sentry evento E2E real desde trusted main con UUID+release+proyecto autenticados y recibo de run.
- [ ] #234 autorizado, reviewers completos SUCCESS y fusionado por procedimiento normal.
- [ ] Cold restart real en emulador, fixture persistente y PID nuevo probados con receipt SHA-bound.
- [ ] Android, Coverage, Sonar/otros requeridos SUCCESS en mismo SHA final de main; ramas divergentes revalidadas.
- [ ] No P0/P1/P2 relevantes confirmados sin resolución; inventario de blockers y regresiones cerrado bajo CAR-29.
- [ ] Issue #207 actualizado con enlaces y decisión exacta (F8 cerrado solo si TODOS anteriores se cumplen).

## 4. Puntos donde no debemos repetir errores

| Error habitual | Detección que lo previene | Acción segura |
|---|---|---|
| Ajustar timeout de ADB sin demostrar proceso | boot_completed + PID emulador + captura log | reparar infraestructura primero |
| Suponer que `green` prueba entrega remota | GET de ID exacto tras captura | distinguir instrumentación y Sentry server |
| Confundir prueba de recibos con reinicio real | dos PID del SO y fixture persistida | ejecutar emulador aislado |
| Reutilizar SHA antiguo | comparar PR/main + recibos por SHA actual | invalidar/revalidar |
| Silenciar reviewer `INCOMPLETE` | status estructural y razones concretas | corregir YAML o modelo sin reducir criterios |
| Fusionar simultáneamente dos cambios CI | ramas separados y pruebas E2E en trusted main | integrar uno y medir antes del otro |
| Exponer credenciales para depurar | logs redactados, secrets en trusted main, checkout sin persistencia | fail-closed y auditoría |
| Declarar F9 terminada por emulador | banco real de dispositivos, métricas y sesiones con denominador | físico después de F8 |

## 5. Plantilla compacta de sesión de reparación (para cada nueva intervención)

```text
Repo / PR / HEAD SHA / main SHA:
Primer step fallido y log URI:
Especificación (entrada → salida) / riesgo:
Reproducción RED (comando, entorno y resultado real):
Propuesta (un mecanismo causal y diff acotado):
Prueba GREEN (mismo test, mismo contexto):
Casos adversariales (invalidos, denegación, cancelación, offline, timeout):
Review: AST / Core / Independent / owner approval:
CI actual: Android / Coverage / Sentry E2E / rollback E2E:
Seguridad/secretos y licencia de dependencias:
Resultado PASS/FAIL/BLOCKED/NOT_RUN:
Artefactos + hashes + run URL + git SHA:
¿Se puede fusionar sin violar CAR-29? Sí/No y por qué:
```

## 6. Fuentes y límites

Principios deducidos de casos auténticos del repositorio, contrastados con documentación oficial: https://developer.android.com/studio/test/managed-devices , https://developer.android.com/training/testing/continuous-integration/automation , https://docs.github.com/en/actions/how-tos/troubleshoot-workflows , https://docs.sentry.io/api/events/retrieve-an-event-for-a-project/ . MoureDev y otros videos son fuentes educativas complementarias, no autoridad para modificar permisos de workflows ni certificación de entrega Sentry.

**Este protocolo es documentación. No ejecutó E2E ni resuelve CI por sí solo. Mantener CAR-29 y F8 pendiente hasta recibos genuinos.**
