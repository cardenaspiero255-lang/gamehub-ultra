# Ultra Sentinel SSS — integración operativa con Sentry

**Estado:** código de integración implementado en el PR #169; el monitoreo real permanece condicionado a que GitHub Actions tenga credenciales/variables válidas y a una prueba auténtica después de fusionar. No declarar 'conectado y operativo' solo porque pasaron los tests unitarios.

## Flujo conectado

1. `GameHubUltraApplication.kt` inicia Sentry Android solamente si `BuildConfig.SENTRY_DSN` no está vacío y establece `BuildConfig.SENTRY_RELEASE` cuando existe.
2. `.github/workflows/android.yml` construye la APK de distribución con `SENTRY_DSN` y `SENTRY_RELEASE=gamehub-ultra@<SHA>` en el contexto de release confiable; mantiene Sentry desactivado en compilaciones de PR no confiables.
3. `.github/workflows/ultra-sentinel-sentry-incidents.yml` consulta únicamente agregados de issues no resueltas de las últimas 24 h mediante API HTTPS `sentry.io`, nunca eventos privados o trazas sin filtrar.
4. `ultra_sentinel_sentry_ingest.cjs` valida identificadores, deduplica, aplica límites, descarta títulos, trazas, audio, PII y secretos. Genera exclusivamente `ultra-sentinel-incident-summary.json` y una síntesis para `GITHUB_STEP_SUMMARY`.
5. Los SHA de releases asociados a incidentes se contrastan con GitHub Actions mediante IDs y rutas auténticas de los workflows Android Build y Unit Test Coverage, consultados por HTTPS a la API de GitHub. Datos incompletos o GitHub indisponible significan **UNKNOWN**, nunca aprobación.
6. Los resultados son de solo lectura, sin propuestas de rollback automáticas, sin push de código y sin autofusión.

## Configuración necesaria en GitHub

En **Settings → Secrets and variables → Actions**, confirmar sin mostrar ni copiar credenciales en PRs públicos:

- **Secrets:** `SENTRY_AUTH_TOKEN` con acceso mínimo de lectura a issues del proyecto; `SENTRY_DSN` para compilaciones de distribución. **Nunca** pegar sus valores en un issue, chat, commit o log.
- **Variables:** `SENTRY_ORG_SLUG` y `SENTRY_PROJECT_SLUG` deben coincidir con los slugs del proyecto en Sentry.
- **Consentimiento explícito para ejecución recurrente:** establecer **ambas** variables `ULTRA_SENTINEL_SENTRY_CONSENT=true` y `ULTRA_SENTINEL_SENTRY_MONITORING_ENABLED=true`. Por defecto, sin estas variables, las ejecuciones programadas no consultan Sentry.
- Alternativamente, en Actions ejecutar **Ultra Sentinel - Sentry Incident Intake → Run workflow** sobre `main`, marcando `consent=true`; la ejecución manual puede realizarse sin habilitar la programación.

## Verificación real imprescindible después de fusionar

1. Ejecutar manualmente `Sentry Incident Intake` con consentimiento, en `main`.
2. Comprobar que el job `sanitize-sentry-issues` finaliza con éxito, sin respuestas 401/403/429 ni secretos impresos.
3. Confirmar el artefacto sanitizado `ultra-sentinel-sanitized-incidents` (retención: 7 días) y el resumen de Actions.
4. Confirmar desde una APK de distribución autenticada que el release de Sentry coincide con el SHA GitHub y verificar una incidencia de prueba controlada, sin PII.
5. Si GitHub/Sentry no entrega evidencia suficiente, registrar `PARTIAL`/`UNKNOWN` y **no** atribuir un crash a un commit ni recomendar rollback como hecho.

## Seguridad y límites

- Las pruebas de CI usan dobles de API; **no prueban** la conexión real al proyecto Sentry ni que se haya capturado un crash en un dispositivo Vivo.
- El workflow no ejecuta código de PR con credenciales de Sentry ni guarda dumps brutos.
- La búsqueda está acotada a 24 horas y 100 issues, con tamaño máximo de respuesta.
- Error de autenticación, rate limit, cortes de servicio, releases sin SHA verificable y ausencia de consentimiento **no se transforman en éxito**.
- Esta implementación fortalece **US-021/US-022** parcialmente; **US-024/US-025** (reproducción Android y confirmación causal del crash) requieren trabajo adicional.
