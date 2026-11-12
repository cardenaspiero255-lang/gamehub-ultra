# REA + GameSir GameHub: investigación y reconstrucción independiente

**Fecha:** 2026-10-25. **Estado:** investigación con fuentes primarias + herramienta de inventario probada con APK sintéticas; **NO** se ha analizado una APK real de GameSir, ni obtenido su código fuente. **Propósito:** estudiar comportamientos para diseñar código propio y compatible en GameHub Ultra.

## 1. Identidad del producto y diferencia entre compilaciones

- Web del GameHub de GameSir: https://gamehub.xiaoji.com/ . Anuncia **GameHub Android 6.3.1**, con biblioteca Steam/Epic, importación PC/retro, streaming y cloud gaming.
- Google Play: https://play.google.com/store/apps/details?id=com.xiaoji.egggame . Editor: Guangzhou Chicken Run Network Technology Co., Ltd.; paquete `com.xiaoji.egggame`.
- Página de soporte GameSir https://gamesir.com/support/downloads enumera **GameHub v6.0.9** (20-03-2026). Las versiones y canales publicados son diferentes: no mezclar resultados entre APK sin hash/versionCode/certificado.
- Funciones anunciadas: biblioteca Steam/EPIC, importación de juegos, controles táctiles/mando, ejecución local PC (sin conexión en ciertos escenarios), streaming remoto y nube. **No se deduce** que todas estén disponibles en cada teléfono o versión.

## 2. Qué publica GameSir y qué NO

- Organización GameSir Labs: https://github.com/gamesir-labs ; repos públicos como https://github.com/gamesir-labs/wine, https://github.com/gamesir-labs/dxvk y https://github.com/gamesir-labs/dxmt . Son componentes o forks, **NO** el árbol fuente completo de la app Android.
- Comunidad https://github.com/Producdevity/gamehub-lite : **modificación de APK propietario**. https://github.com/gamehublite/gamehub-oss es un archivo comunitario. Los parches y los informes comunitarios no prueban que su contenido sea software original, esté actualizado o pueda redistribuirse. No se importarán sus smali, assets o cambios para superar autenticación.
- Complementos abiertos candidatos, con revisión de licencia por versión: https://github.com/ptitSeb/box64 (MIT), https://github.com/FEX-Emu/FEX (MIT), https://github.com/doitsujin/dxvk (zlib), https://github.com/ValveSoftware/Proton (múltiples licencias), y repos Gamesir-labs ya mencionados. No es correcto afirmar compatibilidad automática o que permiten empaquetado comercial sin evaluar obligaciones y dependencias.
- **Resultado:** no hay evidencia de un repositorio público que contenga la totalidad del código original GameHub Android con una licencia de reutilización. La posibilidad de inspeccionar DEX no confiere derechos sobre su código.

## 3. REA oficial: alcance y límites

- Sitio oficial: https://rea.tools/ ; proyecto: https://github.com/morluto/rea ; paquete: https://www.npmjs.com/package/rea-agents . No instalar desde clones con nombres parecidos.
- Manual de análisis APK: https://github.com/morluto/rea/blob/main/docs/android-analysis.md . REA utiliza JADX headless para análisis estático de DEX y manifiesto; expone `inspect-android-package`, `search-android-classes`, `inspect-android-class`, `inspect-android-method`, `trace-android-references`.
- Inspección Android conectado: https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md . ADB debe instalarse/autorizarse por separado. No presupone root, ni acceso a datos privados de otra app.
- Requiere Node 22.19+/24.11+/26+ y npm; para APK, JDK completo 17+ y proveedor JADX compatible y verificado. Las rutas y los comandos dependen de la versión de REA; verificar `--help` antes de automatizar.
- REA devuelve evidencias como metadatos, pseudocódigo o trazas, **no** garantiza rescatar fuente Kotlin/Java original, secretos, infraestructura de servidores, firmas ni licencias. La desofuscación puede dejar muchas incógnitas.

## 4. Instalación correcta en Windows + OpenCode (equipo autorizado)

Ejecutar **solo en el PC propio, no dentro de un workflow de PR ni con tokens/secrets de GitHub**:

~~~powershell
node -v
npm -v
java -version
npx.cmd rea-agents@latest setup --client opencode
~~~

Revisar y aprobar **el plan de instalación que REA muestre**, reiniciar OpenCode y validar conexión:

~~~powershell
npx.cmd -y rea-agents@latest doctor --client opencode --json
~~~

Para análisis APK hay que instalar y configurar el proveedor JADX verificado. Guía primaria: https://github.com/morluto/rea/blob/main/docs/android-analysis.md . Un ejemplo del proveedor documentado en esa guía es jadx-headless-mcp 0.7.1, comprobando previamente su hash SHA-256; no descargar ejecutables de mirrors ni permitir que un PR externo prepare el proveedor con credenciales.

### Adquirir evidencia del APK

1. Obtener el APK de una **fuente oficial**, según derechos aplicables, o usar el paquete instalado en un dispositivo propio con métodos legítimos.
2. Crear un directorio de evidencia **fuera de GitHub y fuera de carpetas sincronizadas públicas**; guardar el nombre de versión, fecha, URL de origen, SHA-256 y estado de firma obtenido mediante herramientas Android.
3. Ejecución de preparación, si la ruta es local y el APK autorizado: `python tools/rea_apk_inventory.py --apk 'D:/evidence/GameHub.apk' --out 'D:/evidence/gamehub-inventory.json'` . Este script solo lee ZIP metadata + hash, nunca desempaqueta ni extrae código.
4. Desde REA ya instalado: `rea inspect-android-package 'D:/evidence/GameHub.apk' --json` . Después `rea search-android-classes 'D:/evidence/GameHub.apk' MainActivity` y `rea inspect-android-class 'D:/evidence/GameHub.apk' full.package.ClassName` .
5. Trazar **una funcionalidad por vez**, nunca exigir que un decompilador reconstruya «todo el código». Consultar `rea inspect-android-method` y `rea trace-android-references` con nombres de clase auténticos obtenidos del análisis previo.
6. En dispositivo de prueba: observar solamente permisos y acciones autorizadas, reproducir flujos manuales, construir pruebas de entrada/salida; no leer contraseñas, interceptar sesiones ajenas, falsificar firmas, eludir DRM ni autorizaciones comerciales.
7. Guardar resultados: `APK hash`, `version`, `función observada`, `método de observación`, `evidencia`, `limitaciones`, `implementación independiente propuesta`, `test de paridad` y decisión de licencia.

**Nota**: Los comandos REA anteriores se han confirmado contra su documentación oficial. No se han ejecutado sobre GameSir: no hay APK real en este entorno ni una conexión REA al dispositivo.

## 5. Proceso de ingeniería inversa limpio / clean room

- **Analista**: observa comportamiento público y, cuando esté autorizado, analiza el binario local. Produce una especificación de comportamiento y datos de pruebas. Nunca transfiere el pseudocódigo, smali ni recursos protegidos al repositorio.
- **Implementador**: toma la especificación sin copiar estructuras de código protegidas y desarrolla Kotlin propio sobre interfaces de GameHub Ultra. Librerías abiertas se incorporan únicamente después de auditoría de sus licencias y avisos.
- **Validador**: contrasta comportamiento de ambas apps en un dispositivo de ensayo legítimo. Los fallos se documentan en pruebas RED, se corrigen y validan GREEN. No inventar FPS, acceso privilegiado ni compatibilidad universal.
- **Seguridad**: arquitectura sin pretender ser GameSir. Nada de copiar marca, iconos, claves, endpoints privados, certificados, tokens o lógica de evasión de protecciones. Conservar CAR-29 y protección de main.

## 6. Arquitectura independiente recomendada para GameHub Ultra

- `LibraryCatalog`: catálogo propio de juegos locales, favoritos, instalación y sesiones; registrar de dónde viene cada dato.
- `ExternalLibraryProvider`: integraciones oficiales autorizadas (por ejemplo, OAuth/API documentada) por proveedor y permiso; sin robo de cookies, falsos tokens ni uso de APIs privadas como atajo.
- `ControllerInputAdapter`: detección de `InputDevice`, mapeo/diagnóstico de mandos según las APIs Android, siempre diferenciando hardware no compatible.
- `GameLaunchAdapter`: lanzadores por Android Intents con paquetes permitidos; estado de error explícito y sin ejecutar software ajeno automáticamente.
- `PCCompatibilityRuntime`: **proyecto futuro** separado que evalúa Wine/Box64/FEX/DXVK solo tras compatibilidad real, permisos, licencias, sandbox y recursos del teléfono. No prometer emulación PC a partir de un simple launcher.
- `StreamingProvider`: cliente de streaming mediante protocolos públicos y servidores autorizados; no acceso gratuito indebido a servicios de pago.
- `GamingDiagnostics`: medición real de frame pacing, batería, térmica y latencia diagnosticada; los Hz del panel no equivalen a FPS del juego.
- `EvidenceRecorder`: versiona fuentes, hashes, resultados de test, fecha y revocaciones, sin datos personales.

## 7. Requisitos para afirmar «paridad»

- La feature existe y está documentada en versión/hash exactos de GameHub oficial.
- Hay permiso o fundamento legítimo para estudiar la funcionalidad y no se han reutilizado obras ajenas sin licencia.
- Existe una implementación propia compilable con tests positivos y negativos en GameHub Ultra.
- La comparación la ejecuta un dispositivo permitido; se registran dispositivos, resultados, latencias, fallos y límites.
- AppSec y CI Android pasan en el SHA propuesto. Fase EVO 8/9 no se marcan completas por esta investigación.

## 8. Estado de evidencia al crear esta investigación

| Hecho | Estado |
|---|---|
| REA soporta análisis de APK y ADB con herramientas locales | DOCUMENTADO OFICIALMENTE |
| GameHub GameSir tiene Android y paquete com.xiaoji.egggame | VERIFICADO EN SITIOS OFICIALES |
| Componentes abiertos públicos Wine/DXVK, Box64/FEX | FUENTES REVISADAS; licencias para versión concreta pendientes |
| Código completo oficial original disponible y redistribuible | **NO VERIFICADO** |
| APK original obtenida, hasheada y analizada con REA | **NOT_RUN** |
| Funciones de GameHub clonadas en Kotlin desde REA | **NOT_RUN** |
| Script local de inventario con tests sintéticos | **6 tests GREEN locales**; no valida GameSir |

**Siguiente experimento verificable:** adquirir APK oficial autorizada, generar SHA-256 y manifest/DEX/ABI inventario local, usar REA para identificar el recorrido de la biblioteca de juegos, escribir especificación limpia y un test de paridad. Sin APK, informar NOT_RUN.

## Fuentes adicionales

- https://gamehub.xiaoji.com/ (descripción del producto) y https://gamesir.com/support/downloads (descarga oficial).
- https://github.com/morluto/rea/blob/main/docs/installation.md (registro de agentes); https://github.com/morluto/rea/blob/main/docs/cli.md (comandos y evidencias).
- https://github.com/morluto/rea/blob/main/docs/android-analysis.md (JADX y modo APK) y https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md (ADB).
- https://developer.android.com/guide/topics/manifest/manifest-intro (manifiestos); https://developer.android.com/reference/android/view/InputDevice (entradas de mando).
- https://github.com/gamesir-labs ; https://github.com/ptitSeb/box64 ; https://github.com/FEX-Emu/FEX ; https://github.com/doitsujin/dxvk ; https://github.com/ValveSoftware/Proton .

Este documento no otorga permiso de redistribución. En caso de restricciones contractuales o legales concretas, mantener únicamente investigación de comportamiento externo o solicitar autorización del titular.
