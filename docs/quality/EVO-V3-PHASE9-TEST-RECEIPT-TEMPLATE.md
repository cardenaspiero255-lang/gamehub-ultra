# EVO Frontier V3 — Fase 9: registro de certificación física (plantilla sin resultados)

**Estado inicial:** NOT_RUN — ningún campo de esta plantilla constituye una prueba ejecutada. Leer primero [protocolo F9](./EVO-V3-PHASE9-CERTIFICATION-20261025.md).

## Identidad del ensayo

| Campo | Valor medido o referencia |
|---|---|
| Fecha/hora UTC y zona | PENDIENTE |
| Git SHA **completo** del APK instalado | PENDIENTE |
| Workflow URL/run attempt y nombre del artefacto | PENDIENTE |
| SHA-256 real del APK descargado | PENDIENTE |
| Certificado público de firma SHA-256 (NO clave privada) | PENDIENTE |
| packageName/versionName/versionCode | PENDIENTE |
| Dispositivo | Vivo V25 Pro / confirmar en ejecución |
| Android API y versión observadas | PENDIENTE — no copiar valor supuesto |
| Estado batería inicial/final, red, tipo de audio | PENDIENTE |
| Juegos, condiciones de ruido y duración | PENDIENTE — solo datos no sensibles |
| Consentimiento de micrófono y notificaciones | PENDIENTE |

## Matriz resumida de ejecución

| ID | Intentos | Éxitos | Fallos | Evidencia local/no personal | Estado |
|---|---:|---:|---:|---|---|
| VOZ-01 escucha durante juego | — | — | — | — | NOT_RUN |
| VOZ-02 Ultra para / STOP TTS y búsqueda | — | — | — | — | NOT_RUN |
| VOZ-03 negativas e hipótesis X4 | — | — | — | — | NOT_RUN |
| VOZ-04 órdenes afirmativas con permiso | — | — | — | — | NOT_RUN |
| VOZ-05 revocación MIC / callback tardío | — | — | — | — | NOT_RUN |
| VOZ-06 audio focus / cambio juego / motor ocupado | — | — | — | — | NOT_RUN |
| TTS-01 respuesta >4000 caracteres completa e interrumpible | — | — | — | — | NOT_RUN |
| FACT-01 modelos/versión/región/fuentes | — | — | — | — | NOT_RUN |
| FACT-02 offline y contradicciones | — | — | — | — | NOT_RUN |
| PRIV-01 consentimiento y borrado | — | — | — | — | NOT_RUN |
| JUEGO-01 estrés 3×30 min propuesto | — | — | — | — | NOT_RUN |
| UI-01 instalación/actualización/desinstalación | — | — | — | — | NOT_RUN |

## Latencias observadas (NO INVENTAR)

| Escenario | N válidos | N fallidos | p50 ASR→1.er audio (ms) | p95 (ms) | Método y entorno |
|---|---:|---:|---:|---:|---|
| Pregunta factual en línea | — | — | — | — | NOT_RUN |
| Orden afirmativa perfil | — | — | — | — | NOT_RUN |
| STOP sobre TTS | — | — | — | — | NOT_RUN |
| Conectividad interrumpida | — | — | — | — | NOT_RUN |

Si una muestra falta, dejar `null`/`NOT_RUN`, jamás 0. Registrar p95 sólo con N y método conocidos. El STOP incluye comprobación *cero* acciones tardías y sonido después de cancelar.

## Rendimiento/estabilidad con métricas físicas

| Métrica | Valor | Procedencia / limitación |
|---|---|---|
| Sesiones totales con duración documentada | — | NOT_RUN |
| Sesiones sin crash y tasa con N | — | NOT_RUN |
| ANR observados con logs no sensibles | — | NOT_RUN |
| Memoria PSS/USS o memoria usada del proceso | — | NOT_RUN |
| Batería inicio-fin por sesión; minutos | — | NOT_RUN |
| Estado térmico categórico Android | — | NOT_RUN |
| FPS presentados SurfaceFlinger, capa y N fotogramas | null | NOT_RUN; null si capa no atribuible |
| p95/p99 frame pacing | null | NOT_RUN; no derivar de Hz |
| Startup Macrobenchmark (dispositivo físico) | — | NOT_RUN |
| Sentry release/crashes en ventana y N | — | NOT_RUN; no deducir 100% |

Protocolo existente para muestreo corto con PC autorizada: `python scripts/quick_game_benchmark.py --seconds 15 --no-ping`. **NO** ejecutar el E2E destructivo `scripts/car29-verify-android-e2e.sh` en Vivo: solo acepta emulador aislado.

## Registro de incidentes RED→GREEN

| Incidente | Caso | Primer paso fallido y logs | Test RED comprobado | Reparación/PR SHA | GREEN y mismo caso | Decisión |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | NOT_RUN |

## AppSec, compatibilidad y firma

| Gate | Fuente de evidencia | Estado |
|---|---|---|
| MASVS-STORAGE/PRIVACY (micrófono, transcripciones, memoria, borrado) | Control MASTG identificable y resultado | NOT_RUN |
| MASVS-NETWORK/CRYPTO/AUTH (TLS, almacenamiento de secretos, backend) | Prueba estática/dinámica pertinente | NOT_RUN |
| MASVS-PLATFORM/CODE (exports, FGS, logs, dependencias) | APK manifiesto + inspección dinámica | NOT_RUN |
| API33, API34 Vivo, API35, API36 | Dispositivo/emulador/SDK, pruebas y SHA por versión | NOT_RUN F9 |
| Firma de carga **estable** y verificación de actualización | SHA256 cert público + APKs firmados compatibles | NOT_RUN |
| Versión de publicación incremental, AAB y políticas Play | Artefacto, versión, Play Console y declaración | NOT_RUN |
| Privacidad, Data safety y licencias de assets | Revisión legal y técnica | NOT_RUN |
| CAR-29 sobre SHA candidato, Android/Coverage/Sonar | Runs con SHA actual y gates correctos | NOT_RUN para futuro RC F9 |

## Decisión

- [ ] Todos los fallos reproducibles relevantes están resueltos bajo CAR-29 y revalidados en SHA actual.
- [ ] Pruebas físicas mínimas y latencias con muestras y entornos reales; ninguna afirmación inverificable.
- [ ] AppSec sin P0/P1/P2 relevante sin resolver; consentimiento y privacidad probados.
- [ ] Artefactos firmados con identidad estable, actualización real y prueba de distribución controlada.
- [ ] Propietario aprueba explícitamente el release candidate **exacto**; no auto-merge ni auto-publicación.

**Decisión actual: NO_GO / NOT_CERTIFIED.** Reemplazar solo cuando existan pruebas observables.

### Tratamiento de datos

No subir audio de terceros, nombres personales, identificadores de dispositivo, transcripciones reales, secretos, Sentry auth/DSN ni logs íntegros con PII. Usar identificadores de prueba sintéticos, hashes de artefactos y registros locales redactados. No confundir reporte de usuario con evidencia instrumental.
