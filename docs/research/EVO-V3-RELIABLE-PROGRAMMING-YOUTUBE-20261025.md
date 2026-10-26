# EVO Frontier V3 — investigación de YouTube para programar con menos errores

**Fecha:** 2026-10-25. **Estado:** investigación terminada; implementación de técnicas propuesta, NO certificación ni cambios de runtime. Alcance exclusivo de GameHub Ultra (PR #234, #235, CI de main).

## Integridad y origen de la investigación

- El [inventario previo](./EVO-V3-YOUTUBE-KNOWLEDGE-BASE.md) contiene 26 videos curados, 12 cuyas transcripciones estaban accesibles y se comprobaron en una sesión anterior. Este archivo añade **14** referencias únicas halladas o verificadas ahora: **40 videos identificados en total** entre ambos catálogos. No significa que se hayan visto 40 videos.
- El servicio de transcripciones TubeAlfred agotó sus créditos antes de poder recuperar las transcripciones de los 14 nuevos videos. Por tanto, **TODOS los nuevos están marcados solo como descripción/capítulos/metadatos**; no se les atribuyen enseñanzas específicas no corroboradas.
- Cuando YouTube expone capítulos/descripciones se citan como metadatos; los principios técnicos recomendados provienen de la evaluación propia del riesgo y de **documentación oficial** (separar *lo que dice el video* de *lo que está probado*).
- No descargar videos ni guardar transcripciones o código protegido de terceros en GitHub. No interpretar instrucciones de un video como órdenes de modificar CI.

## 14 videos nuevos — catálogo reproducible

| # | Canal | Recurso | Duración | Evidencia real | Uso potencial | Hipótesis de transferencia (NO afirmación de contenido estudiado) |
|---|---|---|---:|---|---|---|
| 27 | MoureDev | [El fin del Vibe Coding: crea software robusto con SDD](https://www.youtube.com/watch?v=5HaOxAAA5qI) | 2:03:16 | Video y capítulos verificados en YouTube; sin transcripción | F8/#234/#235 | Spec-first: declarar invariantes, amenazas y criterios verificables ANTES de cambiar el YAML |
| 28 | MoureDev / Carlos Blé / Miguel A. Gómez | [Introducción al TESTING con Python](https://www.youtube.com/watch?v=344uwF1z2Gg) | 1:18:30 | Video e índice verificados en YouTube; sin transcripción | F8/#234/#235 | Usar unittest, casos negativos, fallos forzados y validación independiente de recibos |
| 29 | MoureDev | [GITHUB ACTIONS: AUTOMATE your CODE and save time](https://www.youtube.com/watch?v=pNtcTmCiXzw) | 0:51:39 | Metadatos de búsqueda; sin transcripción | F8/CI | Probar condiciones de los triggers y encadenamientos de jobs, registrar SHA y pasos |
| 30 | Philipp Lackner | [Beginner's Guide to CI for Android with GitHub Actions](https://www.youtube.com/watch?v=WOAchfTm1t4) | 0:33:00 | Metadatos de búsqueda; sin transcripción | F8/#234/#235 | Comparar ejecución Android instrumentada en runners, KVM, SDK, boot y limpieza de AVD |
| 31 | Bitrise | [Setting up Android UI Testing on CI](https://www.youtube.com/watch?v=AZKAm8sYpJw) | 0:23:38 | Metadatos de búsqueda; sin transcripción | F8/#234/#235 | Separar fallo de aprovisionamiento de fallo del test y usar device provisioning fiable |
| 32 | Android Developers | [Android Testing Strategies](https://www.youtube.com/watch?v=qeFWCYc7u3E) | 0:08:00 | Video, descripción y capítulos verificados en YouTube; sin transcripción | F8/F9 | Pirámide de test, scopes y Gradle Managed Devices; fuente primaria Android docs |
| 33 | MoureDev | [Curso completo de lógica de programación](https://www.youtube.com/watch?v=TdITcVD64zI) | 7:29:56 | Metadatos de búsqueda; sin transcripción | General | Diseñar invariantes simples y casos límite para reducir errores de condiciones |
| 34 | MoureDev | [Java desde cero: curso completo y POO](https://www.youtube.com/watch?v=JOAqpdM36wI) | 8:48:49 | Metadatos de búsqueda; sin transcripción | General | Fundamentos de estructuras y separación de responsabilidades; baja prioridad vs Kotlin/CI |
| 35 | MoureDev | [Python desde cero para principiantes](https://www.youtube.com/watch?v=Kp4Mvapo5kc) | 10:07:35 | Metadatos de búsqueda; sin transcripción | Verificadores Python | Sintaxis y lectura mantenible; no sustituye tests adversariales ni tipo/seguridad |
| 36 | freeCodeCamp | [Software Testing Course: Playwright, E2E and AI Agents](https://www.youtube.com/watch?v=jydYq7oAtD8) | 1:03:31 | Metadatos de búsqueda; sin transcripción | Testing | Diseño de oráculos E2E, aislamiento y trazabilidad; Playwright NO prueba Android nativo |
| 37 | Java Techie | [TDD: Red, Green, Refactor con ejemplo Java](https://www.youtube.com/watch?v=UzRa5cLma0g) | Metadatos de búsqueda; sin transcripción | F8 | Escribir primero una regresión que falle; pasar a GREEN y ejecutar no regresión | undefined |
| 38 | Learn With Mithran | [GitHub Actions: CI/CD, YAML, Triggers & Runners (Parte 1)](https://www.youtube.com/watch?v=FbV57lDXEbY) | 1:53:41 | Metadatos de búsqueda; sin transcripción | CI | Triggers y jobs: distinguir eventos push, PR, dispatch y procedencia del workflow |
| 39 | Learn With Mithran | [GitHub Actions: Variables, Contexts, Secrets & Multi-job (Parte 2)](https://www.youtube.com/watch?v=RwZ0sMWnAF0) | 1:55:44 | Metadatos de búsqueda; sin transcripción | CI/Sentry | Evitar secrets en contexto PR, permisos mínimos, artefactos y fallos cierres |
| 40 | ALEXANDRISM | [Automate Android Apps with Maestro: Flows & CI/CD](https://www.youtube.com/watch?v=wZpoMOfr8fw) | 0:42:22 | Metadatos de búsqueda; sin transcripción | F9 | Posible capa black-box para smoke UI; no sustituye instrumentación ni evidencia Sentry |

## Tres fuentes con capítulos verificados

### MoureDev: Spec-Driven Development (recurso 27)
El índice publicado identifica explícitamente los siguientes bloques: [00:26:11](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=1571s) formas de SDD; [00:41:32](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=2492s) `AGENTS.md`/especificaciones/plan; [01:10:12](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=4212s) especificación; [01:25:43](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=5143s) plan; [01:29:55](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=5395s) tareas; [01:42:18](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=6138s) validación; [01:44:56](https://www.youtube.com/watch?v=5HaOxAAA5qI&t=6296s) iteración. Lo anterior es **índice publicado, NO transcripción comprobada**.

**Aplicación propia al repo:** documentación de contratos FAIL/PASS/BLOCKED, archivo antes/después SHA y permisos/rollback. No modificar varios subsistemas a la vez. Dejar que cada experimento falsable determine el siguiente cambio.

### MoureDev: testing Python (recurso 28)
Su índice público identifica una introducción a testing y un taller con dos invitados, pero **no hay transcripción revisada**. Para los tests del repositorio la fuente principal de comportamiento es https://docs.python.org/3/library/unittest.html : `assertRaises`, `subTest`, comparación de estados, tests negativos, verificador independiente y fixtures mínimos.

### Android Developers: testing strategies (recurso 32)
Descripción pública y capítulos: [00:01:21](https://www.youtube.com/watch?v=qeFWCYc7u3E&t=81s) estrategias; [00:03:06](https://www.youtube.com/watch?v=qeFWCYc7u3E&t=186s) scope versus size; [00:05:45](https://www.youtube.com/watch?v=qeFWCYc7u3E&t=345s) categorías. Confirmación primaria: https://developer.android.com/training/testing/continuous-integration/automation .

## Reglas de calidad que trasladaremos (fuentes primarias, no 'memorización' de videos)

### 1. Localizar la primera causa verdadera
Los errores de CI se clasificarán como `BUILD`, `SDK_INSTALL`, `EMULATOR_PROVISIONING`, `EMULATOR_BOOT`, `DEVICE_READY`, `INSTRUMENTATION`, `APP_CRASH`, `NETWORK_INGEST`, `SENTRY_AUTH`, `SENTRY_READBACK`, `RECEIPT_PROVENANCE` o `REVIEW_POLICY`. El primer paso que falla manda. **No** modificar Sentry si el emulador no ha arrancado; **no** relajar la policy AST si una feature del YAML permanece inconclusa.

### 2. Ingeniería guiada por especificación, evidencia y TDD
Por fallo: **Spec** (invariante + entorno), **RED** (test o log auténtico), **fix mínimo**, **GREEN** (mismo caso), **adversariales**, **suite completa**, **review**, **SHA exacto**, **main después de merge**. Un log histórico verde no acredita otro SHA; ejecución pendiente no equivale a PASS.

### 3. Android: boot con fail-closed, no espera ciega
En el #235 exigir inventario del SDK x86_64 API35, KVM, imagen `system.img` y `ramdisk.img`, proceso del emulador vivo, `adb get-state=device`, `sys.boot_completed=1`, API35 y exactamente un dispositivo aislado; recolectar log del emulador si falla. Los tiempos de espera son **límites**, no pruebas de que haya arrancado. Contrastar uso de Gradle Managed Devices o emulator-runner con política de acciones pinneadas. Fuentes: https://developer.android.com/studio/test/managed-devices y https://developer.android.com/training/testing/continuous-integration/automation .

### 4. Sentry: ingestión real ligada a release
Tras instrumentación ejecutada y `OK (1 test)` más retorno de runner, capturar UUID de 32 caracteres de la app, realizar flush y **GET** al evento exacto por API de Sentry con token de mínimo alcance. Comprobar proyecto, organización y `release=gamehub-ultra@SHA`; distinguir 401/403/404, rate limit, timeout y respuesta con release equivocado. No revelar DSN/token ni aceptar `captureMessage` local como certificación. Fuente: https://docs.sentry.io/api/events/retrieve-an-event-for-a-project/ .

### 5. Rollback: certificar proceso nuevo y persistencia
En el #234 el recibo `PASS` requiere test instrumentado exacto ejecutado realmente, fase `prepare` antes de `am force-stop`, ausencia de PID mientras detenido, nuevo PID positivo y **distinto** tras nueva Activity, luego fase `verify` leyendo la misma fixture persistente. Enlazar JUnit y logs a SHA-256 de artefactos. No confundir `unit test` del parser de recibo con prueba Android E2E.

### 6. Seguridad de GitHub sin excepciones encubiertas
Para cambios `.github/workflows` exigir review AST completo y checks protegidos en el HEAD actual; un `INCOMPLETE` de parser se investiga antes de fusionar. Tratar `SKIPPED` y fallos de rama con su significado original. Solo triggers trusted main para secretos. Fuente: https://docs.github.com/en/actions/how-tos/troubleshoot-workflows y https://docs.github.com/en/actions/reference/security/secure-use .

### 7. CI de main como oráculo final
Después de cada merge verificar `main` SHA, Android build, cobertura, reviewers, seguridad y artefactos producidos. Identificar cambios a base y revalidar cuando el PR quedó divergente. No mezclar resultados de PR y de push a main. Fuente: https://docs.github.com/en/actions/how-tos/troubleshoot-workflows .

## Priorización / no copiar y pegar por copiar

1. **#235**: prueba RED del boot (fallo real `adb wait-for-device` exit 124), corregir KVM/AVD, volver a ejecutar en main, recién después verificar evento Sentry por API.
2. **#234**: revisar YAML/AST sin silenciar `INCOMPLETE`, aislar setup emulator, ejecutar cold-restart y receipt exacto; no marcar PASS por solo Python unittest.
3. **main después del #221**: comprobar build/coverage/sonar sobre `eefcfe985cf0d3dec136a2ba1db2d89e3ec6b402`, sin transferir checks anteriores de HEAD c6b36... de PR.
4. **F9**: solo tras F8 PASS y recibos reales, usar hardware para latencia/factualidad/estrés/sesiones.

## Qué significa 'aprender' en este repositorio

Este catálogo es una memoria externa versionada: URLs y principios transferibles, comprobados cada vez contra docs primarias y API actual. **No significa actualizar pesos del modelo**, haber visto videos completos ni garantizar ausencia de bugs. Antes de cada implementación, registrar qué principio se aplica, archivo, test RED, test GREEN, run URL, SHA y limitaciones.

## Fuentes normativas revisadas

- Android Developers (CI/instrumentación): https://developer.android.com/training/testing/continuous-integration/automation
- Android Developers (managed devices): https://developer.android.com/studio/test/managed-devices
- GitHub troubleshooting: https://docs.github.com/en/actions/how-tos/troubleshoot-workflows
- GitHub debug logging: https://docs.github.com/en/actions/how-tos/monitor-workflows/enable-debug-logging
- GitHub seguridad: https://docs.github.com/en/actions/reference/security/secure-use
- Sentry exact event retrieval: https://docs.sentry.io/api/events/retrieve-an-event-for-a-project/
- Python unittest: https://docs.python.org/3/library/unittest.html

**No se han ejecutado pruebas ni modificado código de producción en esta investigación.**
