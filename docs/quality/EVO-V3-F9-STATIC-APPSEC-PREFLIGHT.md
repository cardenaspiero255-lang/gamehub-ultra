# EVO Frontier V3 — Fase 9: preflight AppSec y candidata de prueba

**Estado:** ingeniería en PR, NO certificación física, NO aprobación de release. Base: main a838a51fd52dc9b6be343aff5d1fcab0a95eb2aa; cualquier nuevo SHA invalida una suposición de actualidad.

## Alcance y transferencia desde la investigación YouTube

Se aplicaron principios de los recursos catalogados en PR [#232](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/232) y [#236](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/236): 40 videos **identificados**, 12 con transcripción revisada, 28 con solo metadatos/capítulos o sin transcripción. No se han visto 40 videos completos.

- MoureDev SDD/TDD y Testing Python: especificar invariantes antes del código, ensayo RED por módulo ausente, GREEN con casos positivos y adversariales; pruebas con unittest.
- Android Developers, Philipp Lackner, DevExpert: separar pruebas JVM/sintéticas, source-only, emulador y pruebas **físicas**; ninguna implica a otra.
- OWASP MASVS/MASTG: comprobaciones estáticas delimitadas a privacidad de backup, cleartext, permisos y componentes exportados. Esto **no** es un informe de conformidad MASVS ni un pentest.
- GitHub Actions: checkout pinneado y sin credenciales persistidas, permiso mínimo contents: read, sin secretos ni pull_request_target para ejecutar código del PR; fallo cerrado.
- Release: validar procedencia y firma por separado. Los certificados de debug/keystore efímeros de CI nunca justifican publicación.

## Archivos y ejecución

- tools/f9_security_preflight.py: inspección de fuente y del par APK + phase3-artifact-provenance.env de la misma ejecución GitHub.
- tools/f9_source_audit.py: fuente real del checkout sin APK ni supuesta certificación.
- tools/test_f9_security_preflight.py: 16 tests de alteración de hash, SHA equivocado, componente exportado, backup, cleartext, permisos MIC FGS, minificación y firma temporal.
- tools/test_f9_source_audit.py: 4 tests de comportamiento source-only y negativo.
- .github/workflows/f9-static-appsec-preflight.yml: ambas suites y fuente real en PR o push main. No modifica CAR-29.

Con Python >= 3.11, ejecutar desde la raíz:

    python3 -W error::ResourceWarning -m unittest discover -s tools -p 'test_f9_*.py' -v
    python3 tools/f9_source_audit.py --root . --channel internal-debug

Para APK debug y recibo de la misma CI, descargados de GitHub:

    python3 tools/f9_security_preflight.py --root . --apk ./app-debug.apk --provenance ./phase3-artifact-provenance.env --expected-sha a838a51fd52dc9b6be343aff5d1fcab0a95eb2aa --channel internal-debug

**SOURCE_CHECK_ONLY y STATIC_PRECHECK_ONLY no son una certificación.** No sustituyen revisión de manifiesto binario, firma criptográfica, AppSec dinámico MASVS, pruebas físicas ni Play Console. BLOCKED = investigar, no silenciar.

## APK de certificación física

Fuente debug: [Android CI #40207136984](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40207136984), artifact gamehub-ultra-debug (app-debug.apk + phase3-artifact-provenance.env).

- SHA de origen: a838a51fd52dc9b6be343aff5d1fcab0a95eb2aa.
- SHA256 del APK: 01c32034ef7ea4729d29aa90721f3be8fc78f40f85217b03c7a63d8057fd38dd.
- Es solo candidata debug, no apta para Play Store. Actualizar puede fallar por firma diferente; no desinstalar una versión anterior sin revisar la pérdida de datos.

## Bloqueo de seguridad separado

Ultra Sentinel SSS - Verified Post-CI Evidence registró fallo en [40209542687](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40209542687) (WAITING) y en [40211060731](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40211060731) (FAILED): procedencia Android/Coverage de un workflow anterior sin evidencia suficiente. No prueban fallo del APK debug de #40207136984, pero requieren triage independiente. No suprimir ni reinterpretar como PASS.

## Matriz física y publicación

Seguir el [protocolo F9 del PR #237](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/237). Prioridad: Ultra para, No desactives X4, hipótesis sobre X4, micrófono revocado, TTS de más de 4000 caracteres, pregunta real, juegos abiertos, latencia ASR→TTS, RAM/crashes, SurfaceFlinger atribuible. Documentar muestras y p50/p95 sin inventar FPS.

Release público = NO_GO hasta firma estable, AppSec dinámico, aceptación física, resolución de problemas confirmados P0/P1/P2 bajo CAR-29 y aprobación humana sobre SHA final. No auto-publicación.

Referencias: https://mas.owasp.org/MASVS/ ; https://mas.owasp.org/MASTG/ ; https://support.google.com/googleplay/android-developer/answer/11926878 ; https://developer.android.com/studio/publish/app-signing .
