# EVO Frontier V3 — Fase 9: protocolo de certificación física, AppSec y release

**Fecha de apertura:** 2026-10-25. **Clase:** contrato de aceptación / documentación, no certificación. **Issue:** https://github.com/cardenaspiero255-lang/gamehub-ultra/issues/207
**Línea base comprobada:** main `a838a51fd52dc9b6be343aff5d1fcab0a95eb2aa` (PR #234 fusionado). Si main avanza, rehacer matriz y SHA.

## 0. Procedencia y límite de evidencia

- **F8 técnica:** Sentry SDK→servidor evento/release exactos PASS en main `71b3ec15bb7f` ([run 40167504019](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40167504019)); rollback Android tras `am force-stop` PASS en emulador API35, main `a838a51fd52d` ([run 40207136989](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40207136989), artefacto `car29-cold-restart-proof`), Android [40207136984](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40207136984), Coverage [40207136981](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40207136981), Sonar [40207137043](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40207137043) SUCCESS. **No significa certificación de teléfono físico ni que todo el checklist #207 esté completado.**
- **Investigación YouTube:** 26 referencias iniciales en [PR #232](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/232) y 14 adicionales en [PR #236](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/236): 40 videos identificados; solo 12 con transcripciones accesibles previamente revisadas. Los 14 adicionales **no** tuvieron transcripción revisada; utilizar exclusivamente metadatos/capítulos y documentación oficial. No atribuir afirmaciones de video a una clase no estudiada.
- **F9 comienza como NOT_RUN** para el banco físico, la auditoría dinámica AppSec, la identidad de firma de producción y la publicación. CI de API35 no suple el dispositivo real.
- **Estados:** `PASS` = observación del resultado esperado y evidencia SHA/entorno; `FAIL` = incumplimiento reproducible; `INFRA_FAILURE` = falta SDK/equipo/servicio; `BLOCKED` = permiso o precondición; `NOT_RUN` = sin ejecución. No transformar `SKIPPED` ni falta de incidentes en PASS.

## 1. Orden de trabajo y criterio general

1. Congelar release candidate SHA, versionCode/versionName y APK/AAB SHA256. Registrar origen de artefacto y certificado público SHA256; no subir claves privadas, audio, transcripciones ni identificadores de usuario.
2. Verificar CI, Android Build/Coverage/Sonar y resultados protegidos CAR-29 en el SHA actual. No fusionar ni publicar con P0/P1/P2 relevante confirmado, pruebas saltadas o gates incompletos.
3. Ejecutar matriz física de voz, navegación, consentimiento y stress. Conservar fallos como RED; reparar mínimo mediante PR, TDD, revisión protegida, nueva CI y **repetir** física con el nuevo APK.
4. Completar MASVS/MASTG y compatibilidad 33/34/35/36 donde haya entorno autorizado; valorar restricciones de foreground microphone en Android >=14.
5. Firmar con **clave de carga estable custodiada** y verificar actualización sobre la misma identidad; AAB Play App Signing, Play Console, políticas/privacidad, pruebas internas. No lanzar solo porque AAB de CI existe.
6. Cierre F9 únicamente con paquete de evidencias y decisión humana de propietario, sin auto-merge ni modificación de CAR-29.

## 2. Pruebas físicas mínimas — Vivo V25 Pro, Android 14/API34

**Precondiciones:** APK identificable por SHA; instalación limpia + actualización desde una versión con la misma firma; consentimiento explícito a micrófono y escucha continua; notificación visible; un juego real; medir batería/RAM sin registrar información personal. Usar una matriz al menos con modo silencioso y durante juego, y con Wi-Fi/Internet disponible frente a offline. No asumir que tener permiso micrófono autoriza iniciar servicio desde segundo plano.

| ID | Caso / estímulo | Oráculo falsable | Evidencia |
|---|---|---|---|
| VOZ-01 | Habilitar escucha continua con app visible; abrir juego, bloquear/desbloquear si la plataforma lo permite | escucha permanece solo dentro de permisos/restricciones Android; sin cierre y sin activar en secreto | API, foco, notificación, resultado por ciclo |
| VOZ-02 | `Ultra para` durante TTS extenso y durante investigación en curso | se detienen voz y consulta; cero audio/órdenes provenientes de callbacks tardíos tras STOP | tiempos monotónicos STOP y última emisión; 10 repeticiones por condición propuestas |
| VOZ-03 | `No desactives X4`; `¿Qué pasaría si activara X4?` | 0 cambios reales de perfil; diferencia entre orden, negación e hipótesis | estado anterior/posterior; 10 intentos por frase propuestos |
| VOZ-04 | `Ultra pon X4`; `Ultra desactiva X4` bajo condiciones autorizadas | cambios solo tras orden afirmativa permitida y confirmación requerida; errores seguros | selección real y permiso, no solo TTS |
| VOZ-05 | Revocar RECORD_AUDIO, cancelar diálogo y reabrir app | sin captura sin permiso, sin loop infinito ni crash; recuperación con permiso nuevo | resultados, motivo de fallo, callback tardío |
| VOZ-06 | Audio focus perdido, motor ASR ocupado, cambio de juego, auriculares/altavoz | cancelación/reintento acotado; sin UI bloqueada ni repeticiones | escenario/resultado, conteo de fallos |
| TTS-01 | Respuesta de más de 4.000 caracteres y orden STOP a mitad | termina el contenido cuando no se interrumpe; STOP real cuando sí | total fragmentos esperados/emitidos; verificación audible humana |
| FACT-01 | 11 Pro / 11S Pro y comparación RedMagic 11S Pro vs 12 Pro con SKU/región/fecha | no mezcla entidades ni inventa especificaciones o productos no confirmados; atribuye fuentes y desacuerdos | pregunta, fuentes consultadas, juicio reproducible |
| FACT-02 | 5000 vs 6000 mAh, precio actual sin red, dato histórico estable sin red | señala contradicción e incertidumbre; no afirma verificación online sin Internet | expectativa, respuesta, conectividad |
| PRIV-01 | Micrófono negado; desactivar escucha; revisar notificación; cierre/reinicio | captura y retención solo tras consentimiento y con parada efectiva | UI, permisos, configuración restaurada |
| JUEGO-01 | 3 sesiones propuestas de 30 minutos jugando, voz activa voluntaria, alternando escenarios | no crash/ANR; RAM/consumo/estado térmico medidos, no inferidos; FPS solo si TimeStats atribuible | duración/sesiones, errores, deltas de batería, FPS o null |
| UI-01 | instalación, iniciar, biblioteca, permisos, desinstalar, reinstalar/actualizar con la misma firma | acciones sin pérdida inesperada de datos, cierre apropiado y estado esperado | APK SHA, firma, evidencia de instalación, UI |

**Muestreo inicial propuesto, NO umbral oficial ni resultado:** >=30 comandos afirmativos, >=30 entradas negativas/ambivalentes repartidas entre escenarios; >=20 latencias por escenario para informar p50 y p95, número de fallos y distribución; >=3 sesiones de juego de 30 minutos. Si el presupuesto de prueba es menor, registrar `INSUFFICIENT_SAMPLE` en vez de inventar confiabilidad. Definir tolerancias de latencia contra baseline físico acordado y pruebas repetidas; no importar P50/P95 históricos sin misma versión, tipo de red y muestra.

### Latencia y estabilidad

- Registrar t0 inicio de voz, t1 fin ASR, t2 primera respuesta semántica, t3 primer audio TTS, t4 fin de TTS; usar reloj monotónico si hay instrumentación. Reportar duración **ASR→primera voz audible** y de turno completo por separado; no sustituir con tiempo de petición HTTP.
- Calcular p50/p95 indicando N, percentiles/método, dispositivos, red, temperatura/estado térmico y tasa de fallos. No completar métricas faltantes con cero; separar fallo de reconocimiento de latencia válida.
- Crash-free = (sesiones observadas sin crash)/(sesiones observadas), N, intervalo y origen; no decir `100% en producción` a partir de 3 sesiones. Para Google Play, contrastar con Android Vitals, no con una muestra local.
- Para FPS ejecutar el procedimiento existente `docs/EVO-V3-PHASE6-FAST-BENCHMARK.md`: `python scripts/quick_game_benchmark.py --seconds 15 --no-ping` en PC autorizada con ADB. `fps_presented` y p95/p99 de SurfaceFlinger dependen de capas; si no disponibles = null. Hz de pantalla NO es FPS, ping público NO es ping del juego.

## 3. Compatibilidad Android y permisos

| Nivel | Evidencia actual | Pendiente para F9 |
|---|---|---|
| API33 Android 13 | No identificado ensayo físico separado en esta revisión | instalar/actualizar, permisos notificaciones/audio, background/foreground, funcionamiento básico |
| API34 Android 14 | Vivo V25 Pro disponible para ensayo | permisos `while-in-use`, arranque legítimo foreground service de micrófono, voz bajo juego, permisos revocados |
| API35 Android 15 | CI Android en emulador y E2E rollback PASS | prueba funcional integral voz y permisos en entorno API35, no extrapolar solo rollback |
| API36 Android 16 | `targetSdk=36`, `compileSdk=36` observados en Gradle | test de compatibilidad en emulador/dispositivo API36; no inferir ejecución por compilación |

El manifiesto observado declara RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE, SPECIAL_USE, POST_NOTIFICATIONS e INTERNET; `allowBackup=false`, `usesCleartextTraffic=false`; algunos servicios exported exigen permisos Android. **Solo observaciones estáticas**: auditar servicios, declaración de specialUse y permisos/consentimiento en runtime. Fuentes: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start , https://developer.android.com/about/versions/14/changes/fgs-types-required .

## 4. AppSec — revisión OWASP MASVS/MASTG obligatoria

| Dominio MASVS | Verificaciones con evidencia; sin afirmar conformidad automática |
|---|---|
| STORAGE / PRIVACY | audio y transcripciones no persistidos indebidamente; consentimientos, borrado de datos/memoria; backup apagado en manifiesto no sustituye inspección |
| CRYPTO | no guardar secretos en APK/BuildConfig sin distinguir claves **publicables** y tokens privados; verificar firmas, cifrado para datos sensibles si corresponde |
| AUTH | autorizaciones reales para backend y acciones críticas; no confundir autenticación con estar logueado |
| NETWORK | TLS efectivo, endpoints permitidos, timeouts, redacción de errores; MITM controlado solo en laboratorio autorizado |
| PLATFORM | exported components, permisos Quick Settings/voice, intents, FGS especial/micrófono y consentimiento en Android 13–16 |
| CODE | dependencias, SBOM/licencias, secretos en logs/CI, minificación release y ausencia de hallazgos P0/P1/P2 |
| RESILIENCE | controles de manipulación y procedencia razonables; no alegar resistencia total a reverse engineering |

En cada dominio registrar MASVS control o MASTG test concreto, alcance, herramientas, pasos, prueba positiva/negativa, evidencia y decisión. Inspección de manifiesto **no** equivale a pentest. Fuentes: https://mas.owasp.org/MASVS/ y https://mas.owasp.org/MASTG/ .

## 5. Preparación del release: bloqueos detectados en el árbol actual

1. **Firma de CI efímera:** `.github/workflows/android.yml` crea un keystore aleatorio en cada ejecución (`openssl rand`, `keytool`) para bundle/release APK de prueba. Son útiles para CI, pero **no garantizan firma estable para actualizar instalaciones ni constituyen clave de carga persistente de Play**. NO publicar una build efímera como release final. Diseñar flujo de firma autorizado con clave estable fuera del repositorio, sin imprimir secretos, y comprobar huella pública + actualizaciones firma-consistente.
2. **Versión observada:** `app/build.gradle.kts` tiene `versionCode=3`, `versionName=0.3.0`. Antes de entregar actualización, comprobar versión superior a la ya publicada (si existe), packageName y registro del release candidate.
3. **Plataforma:** `targetSdk=36` ya se alinea con la exigencia de Google Play para apps nuevas/updates desde 2026-08-31 (sin garantizar aceptación). Probar Android 16 real/API36 y requisitos de política.
4. **Recorrido Android APK/AAB:** construir, verificar SHA256, firma APK (`apksigner verify --print-certs`) / bundle y upload certificate, instalar, iniciar, actualizar con la misma firma, desinstalar, verificar datos/revocación; Play App Signing y track de pruebas internas autorizado.
5. **Contenido de tienda y privacidad:** ficha, icono, descripción veraz, políticas de datos y micrófono, declaraciones de permisos/FGS specialUse, Data safety/privacidad si aplica, licencias de assets y dependencias. No prometer FPS/overclock/X4 de otras apps sin API real y permiso.
6. **Gate publicación:** Sentry release e identidad SHA, bloqueo de crash/ANR confirmados, canary controlado y rollback del **despliegue** distinto del rollback de configuración probado en F8.

Fuente: https://developer.android.com/studio/publish/app-signing , https://developer.android.com/studio/publish/preparing , https://support.google.com/googleplay/android-developer/answer/11926878 , https://developer.android.com/google/play/vitals .

## 6. YouTube: transferencia explícita de los 40 recursos al trabajo

Sin copiar transcripciones ni fingir estudiar los 28 sin acceso a ellas. Índices conforme a los PR #232/#236:

- **01–05 (MoureDev Kotlin/Android/Git/agentes):** lifecycle, contrato de estado, ramas y aprobación separada; la funcionalidad real la deciden tests/reviews del repo.
- **06–14 (DevExpert, Android Developers, Philipp Lackner, AristiDevs):** Flow, corrutinas, cancelación cooperativa, callback tardío, pruebas con tiempo controlado, unit/instrumented/E2E. Aplicar a VOZ-02/05/06 antes de parchear.
- **15–17 (rendimiento Android):** tracing Compose, macrobenchmark y Android Vitals; FPS vs Hz, ANR/crash por N real.
- **18–20 (Sentry/Bugsnag):** error triage, crash-free sessions con denominador, release/provenance; aprovechar F8 Sentry E2E sin extender su certificación a otro SHA.
- **21–22 (OWASP):** MASVS/MASTG como guía normativa de AppSec, validar controles individualmente.
- **23–26 (Actions, AAB, distribución):** SHA, CI, artefactos, firma y smoke install/update/uninstall; distinguir clave CI efímera de firma de lanzamiento.
- **27–29 (MoureDev SDD/testing/GitHub Actions):** spec → plan → tareas → RED → GREEN → revisión → evidencia antes de integrar. Solo capítulos/metadatos en videos adicionales.
- **30–32 (Philipp Lackner/Bitrise/Android Developers):** aislamiento de emulador, device provisioning y test scopes; hardware físico para rendimiento real.
- **33–35 (MoureDev bases programación/Java/Python):** legibilidad, invariantes, verificadores y negativos; prioridad menor que pruebas del proyecto.
- **36–40 (freeCodeCamp/Java Techie/Learn With Mithran/Maestro):** arquitectura de E2E/TDD, triggers/secretos CI, smoke black-box opcional. Playwright no prueba app Android nativa; Maestro opcional, no gate sin CI y resultados auténticos.

**Orden de precedencia:** evidencia real del repo y pruebas físicas > documentación oficial actual > transcripción comprobada > capítulos/metadatos. Las fuentes de videos pueden inspirar **tests**, no sustituirlos.

## 7. Decisión de liberación (sin aprobación automática)

- `GO_INTERNAL_TEST` solo si CI del SHA final es verde, el build está firmado de forma reproducible, AppSec sin hallazgos graves sin resolver y la cohorte interna consiente pruebas con datos minimizados.
- `GO_PUBLIC` requiere además cierre de matriz física, compatibilidad, política de tienda, soporte/privacidad, canal de actualización, monitoreo y rollback de release verificados con evidencia.
- Si falta un bloque, declarar `NO_GO` o `NOT_CERTIFIED`, registrar causa, responsable y oráculo; no modificar el gate para forzar PASS. No marcar F9 en #207 por existir este documento.

## Evidencia práctica

Registrar cada caso en el archivo compañero `docs/quality/EVO-V3-PHASE9-TEST-RECEIPT-TEMPLATE.md` **localmente** antes de publicarlo (no subir datos personales, audios ni trazas crudas a GitHub). Responsable humano acredita qué se oyó en el dispositivo real.
