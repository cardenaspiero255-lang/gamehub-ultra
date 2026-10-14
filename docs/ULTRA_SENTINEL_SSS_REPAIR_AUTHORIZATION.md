# Ultra Sentinel Triple SSS — bloque 4: autorización de reparaciones

Esta etapa implementa un **preflight** para proponer commits de reparación, NO un escritor automático. `preflightRepair` nunca hace `push`, `commit`, `merge` ni invoca servicios externos.

## Requisitos estrictos
- GitHub debe confirmar inventario **completo** de archivos y checks para el HEAD inmutable del PR, con checks finalizados en `success`. Un estado en cola, fallido, saltado, desconocido o SHA obsoleto bloquea.
- Solo se consideran archivos Kotlin/Java ordinarios en `app/src/main/java/`, `app/src/test/` o `app/src/androidTest/`. Configuración de CI, scripts, Gradle, manifest, Supabase, seguridad y archivos mixtos **no** son elegibles.
- El verificador independiente debe validar una autorización firmada por el propietario, emitida para el **número de PR, SHA y scope exactos**; autorización de no más de 15 minutos, nonce no reutilizado y revisión humana independiente actual. JSON del PR, labels, comentarios de plugins y archivos del candidato **no son** verificadores.
- Cambiar una sola línea cambia el SHA y revoca inmediatamente la autorización previa.
- El resultado `ELIGIBLE_FOR_HUMAN_COMMIT` indica solamente que pueden presentarse candidatos para aprobación manual. `autoCommitAllowed:false` y `autoMergeAllowed:false` permanecen siempre falsos.

## Lo que falta para activar commits controlados
Requiere infraestructura confiable separada de la rama del PR: verificador GitHub autorizado, ledger duradero y atómico para nonce anti-replay, credenciales de mínimo privilegio, revalidación a nivel de transacción en el instante de escribir y confirmación humana del patch y tests. No conectar un escritor a este gate sin esos controles ni declarar el modo 3 de Family Hunter operativo antes de integrar el adaptador PR #194.

## Regla estricta y superioridad respecto de Codex
- Siempre TDD (RED/GREEN), revisión de familias y variantes, cobertura, Android Build, Core, revisión independiente y HEAD exacto. No fusionar con P1/P2, fallas conocidas, gates pendientes o hilos sin resolver.
- Nada se «recuerda como verificado» solo por estar escrito en el ledger. La memoria requiere evidencia de merge, tests y revisión confirmada por servicio confiable.
- El objetivo de superar Codex necesita benchmark ciego, reproducible y con revisores independientes. No se afirma SSS ni ventaja global sin las métricas.

## Secuencia de PR
#194 adaptador (pendiente) → #197 memoria → #198 variantes multiarchivo → #199 observer de plugins → este PR de preflight. No alterar la política `main` ni la autofusión Mergify hasta satisfacer el proceso autorizado.
