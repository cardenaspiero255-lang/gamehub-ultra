# EVO Frontier V3: base de conocimiento YouTube (GameHub Ultra)

Fecha: 2026-10-25. Documento técnico original para el issue #207: https://github.com/cardenaspiero255-lang/gamehub-ultra/issues/207

ESTADO: RESEARCH / NO CERTIFICADO. Este documento no afirma que la fase 8 ni la fase 9 estén terminadas.

## Metodología y alcance

- Se localizaron 26 videos técnicos, priorizando MoureDev, Android Developers y especialistas.
- En 12 videos la integración de YouTube devolvió una transcripción (filas marcadas transcripción); los restantes se localizaron mediante metadatos, sin declarar haber visto ni leído sus clases.
- La recuperación de subtítulos no equivale a verificar imágenes, código en pantalla o afirmaciones del ponente. Los subtítulos automáticos pueden contener errores.
- NO se copian transcripciones ni videos protegidos al repositorio: enlaces, metadatos y conclusiones propias.
- Estos cursos son referencias educativas. La autoridad para controles de Android, GitHub y AppSec corresponde a la documentación vigente y a los resultados reproducibles del repositorio.

## Inventario de 26 videos

| # | Canal | Video | Duración | Evidencia de acceso | Fase | Aplicación propuesta |
|---|---|---|---|---|---|---|
| 01 | MoureDev | [Kotlin desde cero: fundamentos](https://www.youtube.com/watch?v=T3ugOYTRF7c) | 64 min | transcripción | 8 | Null safety y modelos de estado; contrastar contra Kotlin actual. |
| 02 | MoureDev | [Aprende Android con Kotlin creando app](https://www.youtube.com/watch?v=ebQphhLpJG0) | 144 min | transcripción | 9 | Ciclo de vida y navegación; comprobar APIs actuales. |
| 03 | MoureDev | [Git y GitHub curso completo](https://www.youtube.com/watch?v=3GymExBkKjE) | 305 min | transcripción | 8/9 | Ramas, historial y revisión; no reemplaza controles CAR-29. |
| 04 | MoureDev | [Programación con agentes IA](https://www.youtube.com/watch?v=H3gH_Fe6xvs) | 184 min | transcripción | 8 | Triage humano supervisado y agentes con herramientas seguras. |
| 05 | MoureDev | [Kotlin Android nivel medio](https://www.youtube.com/watch?v=AA3N_yQQtHY) | 88 min | metadatos | 9 | Patrones Android; revisar transcripción antes de atribuir detalles. |
| 06 | DevExpert | [Kotlin Flows curso completo](https://www.youtube.com/watch?v=EksgrR4OKjk) | 122 min | transcripción | 8/9 | Cancelación de recolectores y eventos tardíos. |
| 07 | DevExpert | [Corrutinas Android repaso](https://www.youtube.com/watch?v=KqLtW8d8PXY) | 86 min | metadatos | 8 | Scopes de trabajo y control de excepciones. |
| 08 | DevExpert | [MVVM Clean Hilt Testing](https://www.youtube.com/watch?v=lX7CeooKCcQ) | 254 min | metadatos | 8 | Separar UI y dominio; tests aislados. |
| 09 | Android Developers | [Testing Coroutines on Android](https://www.youtube.com/watch?v=KMb0Fs8rCRs) | 21 min | transcripción | 8 | Pruebas deterministas de concurrencia con tiempo virtual. |
| 10 | Android Developers | [Kotlin Flows in practice](https://www.youtube.com/watch?v=fSB6_KE95bU) | 21 min | metadatos | 8 | Procesamiento confiable de eventos y estados. |
| 11 | Philipp Lackner | [Ultimate guide to Android Testing](https://www.youtube.com/watch?v=nDCCwyS0_MQ) | 70 min | transcripción | 8 | Piramide de pruebas y selección de casos E2E. |
| 12 | Philipp Lackner | [Coroutine cancellation and exception handling](https://www.youtube.com/watch?v=VWlwkqmTLHc) | 22 min | transcripción | 8 | Cancelar de manera cooperativa; nunca responder tras STOP. |
| 13 | AristiDevs | [Testing Android con Kotlin](https://www.youtube.com/watch?v=xCjIJMydI3s) | 39 min | transcripción | 8 | Estrategia para reproducir regresiones unitarias. |
| 14 | Android Summit | [Unit Testing Kotlin Channels and Flows](https://www.youtube.com/watch?v=uEnpIFVspMo) | 38 min | metadatos | 8 | Pruebas de flujos con callbacks tardíos. |
| 15 | Philipp Lackner | [Android app performance measurement](https://www.youtube.com/watch?v=XHz_cFwdfoM) | 22 min | metadatos | 9 | Medir rendimiento sin convertir Hz en FPS. |
| 16 | Android Developers | [Enhancing Jetpack Compose app performance](https://www.youtube.com/watch?v=Z96wfbID_Yc) | 28 min | metadatos | 9 | Profiling de UI cuando sea aplicable. |
| 17 | Android Developers | [Android vitals diagnostics](https://www.youtube.com/watch?v=dx6LBaFqEHU) | 40 min | metadatos | 9 | Crash/ANR por dispositivos, sin métricas inventadas. |
| 18 | Sentry | [Mobile Release Health crash-free sessions](https://www.youtube.com/watch?v=dGaO7MIdH7Y) | 28 min | metadatos | 8/9 | Release y sesiones con denominadores reales. |
| 19 | Sentry | [Application monitoring and error tracking](https://www.youtube.com/watch?v=Kz-SudCTdu4) | 55 min | metadatos | 8 | Verificación real de evento e identidad. |
| 20 | Bugsnag | [Crash course in crash reporting](https://www.youtube.com/watch?v=4zwyQ3XNUWI) | 22 min | transcripción | 8/9 | Triage y análisis de problemas reales. |
| 21 | Black Hills InfoSec | [OWASP MASTG getting started](https://www.youtube.com/watch?v=odnip3LL6hc) | 72 min | transcripción | 9 | Pruebas de seguridad móvil con resultados reproducibles. |
| 22 | droidCon Archive | [OWASP MASVS and MSTG](https://www.youtube.com/watch?v=NxRdG0XXPAQ) | 21 min | metadatos | 9 | Modelo de controles AppSec. |
| 23 | DevOps Directive | [GitHub Actions full course](https://www.youtube.com/watch?v=Xwpi0ITkL3U) | 223 min | transcripción | 8/9 | Permisos, jobs, artefactos y SHA exactos. |
| 24 | freeCodeCamp | [GitHub Actions certification](https://www.youtube.com/watch?v=Tz7FsunBbfQ) | 190 min | metadatos | 8/9 | Automatización de evidencia en CI. |
| 25 | Android Developers | [App Bundles Live Q&A](https://www.youtube.com/watch?v=fnsxImWDxj0) | 48 min | metadatos | 9 | Entrega y pruebas de paquete de lanzamiento. |
| 26 | Android Developers | [Testing distribution with Android App Bundle](https://www.youtube.com/watch?v=flhib2krW7U) | 37 min | metadatos | 9 | Canales de distribución y prueba de APK/AAB. |

## Síntesis técnica original: pautas comprobables

### A. Voz, ASR, TTS, STOP y concurrencia
Los resultados de ASR, TTS y consultas pueden llegar después de la cancelación, revocación del micrófono o cambio de sesión. Hipótesis: vincular cada acción al identificador monotónico de sesión y comprobar el estado antes de emitir sonido, ejecutar acciones o reportar éxito. Pruebas: STOP durante voz, callback tardío, pérdida de audio focus, motor ocupado, bloqueo/desbloqueo, red ausente y permiso revocado. Referencias 06, 09, 11, 12, 13. Debe reproducirse el fallo en el proyecto antes de cambiar producción.

### B. TDD, oráculos y CI con trazabilidad
Para cada fallo: reproducción RED, parche mínimo GREEN, tests de regresión, suite completa y recibo con SHA exacto. No reutilizar runs antiguos de otro SHA; SKIPPED no equivale a SUCCESS. Separar pruebas unitarias, integraciones y pruebas reales en hardware. Referencias 03, 08, 09, 11, 13, 23. Fuente de control: https://developer.android.com/training/testing/fundamentals/strategies

### C. Ultra Sentinel, seguridad y rollback (fase 8)
Un agente puede sugerir triage y una solución, pero nunca autorizar su propia fusión. Mantener CAR-29, identidad SHA/blobs, revisión autenticada, permisos mínimos y evidencias independientes. Evitar ejecutar código de PR no confiable en un contexto con secretos. Registrar exactamente qué se restaura y cómo se comprueba rollback. Referencias 03, 04, 21, 23. Normas: https://docs.github.com/en/actions/reference/security/secure-use y https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target

### D. Sentry: delivery E2E real (fase 8)
Capturar localmente no demuestra entrega remota. Exigir UUID sintético no personal, flush, SHA de release y lectura independiente del mismo evento mediante API autorizada. Fallar cerradamente ante 401/403, ID no encontrado o release distinto; jamás mostrar secretos en logs. PR #228 sigue abierto en borrador al hacer esta investigación: https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/228 . Referencias 18, 19, 20.

### E. Rendimiento y calidad del release (fase 9)
Fijar dispositivo, API, versión, SHA y prueba; medir latencias p50/p95, ANRs, crashes, batería y memoria reales. Diferenciar Hz de pantalla de FPS presentado. No declarar crash-free sin denominador de sesiones ni confundir emuladores con certificación física. Referencias 15, 16, 17, 18, 25, 26. Normas: https://developer.android.com/games/optimize/vitals y https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

### F. Seguridad de aplicación móvil (fase 9)
Aplicar controles medibles de OWASP MASVS/MASTG a almacenamiento, permisos, red, exportación, datos personales y registros. Los tutoriales no prueban por sí solos la conformidad. Referencias 21/22; fuente primaria https://mas.owasp.org/MASTG/ .

## Reglas para aplicar esta investigación

1. Elegir fallo o riesgo REAL y asociarlo a issue, ruta, commit, versión de librerías y oráculo independiente.
2. Extraer una técnica de las fuentes y contrastar con la API oficial vigente.
3. Añadir test RED que falle, corregir GREEN, ejecutar la batería del repositorio y guardar recibo autenticado.
4. No editar CAR-29 ni workflows protegidos a partir de tutoriales.
5. Nunca convertir estos apuntes en afirmaciones de validación física, CI verde o cierre de fase.
6. Documentar dudas y limitaciones: la popularidad del video y la disponibilidad de subtítulos no son evidencias de calidad.

## Orden de consulta y transferencia

- Fase 8: 03, 04, 06, 09, 11, 12, 13, 18, 19, 20, 21, 23 y 24.
- Fase 9: 02, 05, 08, 15, 16, 17, 18, 21, 22, 25 y 26.
- MoureDev: Kotlin y Git útiles como fundamentos; la evaluación final debe partir de los tests de GameHub Ultra, no del curso.
