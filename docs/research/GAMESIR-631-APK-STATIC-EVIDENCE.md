# GameSir GameHub 6.3.1 — evidencia de APK real (inventario seguro)

**Fecha UTC:** 2026-10-25. **Alcance:** inspección estática local de APK facilitada voluntariamente por el propietario de una copia. No se ejecutó la aplicación ni se subió su APK, pseudocódigo, recursos, librerías o código propietario.

## Identidad, integridad y procedencia

- Archivo: GameHub 6.3.1 (base APK). Bytes: `67122098`.
- SHA-256: `09b3e387205215ca0d10fe6881fff034939ae2c16a07b0ce470a48c6c51c6763`; MD5 de cotejo: `0aca596ab937fcb1449dbb7d5700db83`.
- El SHA-256 coincide con el inventario de una base APK 6.3.1 publicado por **un tercero**: https://github.com/The412Banner/bannerhub-revanced/releases . Eso es correspondencia de bytes con la publicación, no verificación de fuente oficial.
- `AndroidManifest.xml` decodificado desde AXML binario: `package=com.xiaoji.egggame`, `versionName=6.3.1`, `versionCode=141`, `minSdk=29`, `targetSdk=36`, `compileSdk=37`.
- Archivo ZIP: 1.943 entradas; comprobación de integridad ZIP (CRC) completa sin fallos.
- Firma APK v2 presente. Se comprobó mediante parser local conforme a AOSP la firma de `signed data` con RSA-PKCS1-SHA256 y el digest de contenido de 66 fragmentos: **coinciden**. No se usó `apksigner` del SDK; verificación adicional de plataforma y vinculación independiente del certificado con una publicación oficial de GameSir están pendientes.
- Su certificado interno de firmante indica `CN=gamesir`, `O=gamesir`; fingerprint SHA-256 `f6dc89251d2edf60c5721524d59f2dc6373825a73b1b81d031cafeeff31c9775`. Un nombre autodeclarado en certificado auto-firmado no autentica por sí solo al editor.

## Estadísticas de estructura

| Estructura | Observación |
|---|---:|
| DEX | 5 |
| Clases definidas en DEX, incluidas dependencias externas y ofuscación | 52.124 |
| Métodos según encabezados DEX | 274.357 |
| Bibliotecas `.so` | 25 |
| ABI de todas las bibliotecas `.so` | `arm64-v8a` |
| `activity` / `activity-alias` | 46 / 2 |
| `service` / `receiver` / `provider` | 39 / 25 / 10 |
| Componentes declarados `exported=true` | 35 |
| Declaraciones `uses-permission` | 58 |

Los permisos declarados incluyen `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `RECORD_AUDIO`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `CAMERA` y `WRITE_SETTINGS`. Estar declarados no prueba concesión ni uso efectivo. No se diagnostican vulnerabilidades a partir de esos números.

## Arquitectura observable por recursos de Compose

Se encontraron recursos bajo `assets/composeResources` con los siguientes paquetes/módulos (los nombres solo constatan recursos, no la lógica de clases ni runtime):

- `features.gamepadconfig` (266 entradas), `features.gamepadconnect` (29), `features.controllermapping` (19), `features.vjoy` (30), `common.vjoy` (16).
- `features.home` (129), `features.game` (52), `features.steam` (26), `features.auth` (24).
- `features.cloud` (47), `features.download` (12), `features.mods` (6), `features.file_selector` (9).
- `common.retro_emulators` (42), `common.winemu_core` (8).
- `features.setting` (26), `features.profile` (10), `features.search` (10), `features.onboarding` (39).

## Bibliotecas y límites de la emulación

- Core de mandos: `libGamesir.so`.
- Integraciones de plataforma: `libsteamkit_core.so`, `libepickit_core.so`.
- Multimedia/transmisión: `libhaima_rtc_so.so`, `libliteavsdk.so`, `libtxffmpeg.so`, `libIjkplayer_haima.so` y dependencias relacionadas.
- No aparecen bibliotecas nativas con los nombres `libwine.so`, `libbox64.so`, `libdxvk.so`. Sí hay UI/recursos `common.winemu_core`, por lo que es **plausible** que el runtime se gestione o descargue por separado, pero **esto no se ha verificado**. No afirmar que el APK proporciona la implementación completa de Wine/Box64/DXVK.
- Algunos recursos contienen modelos ONNX asociados por nombre a lectura QR, no un LLM completo de juego. No reutilizar modelos ni recursos protegidos sin licencia.

## Hallazgos con impacto en GameHub Ultra

1. Prioridad alta: diseñar funcionalidad propia de biblioteca, perfiles, gamepad y asignación táctil a partir de especificaciones de comportamiento observadas, sin copiar el código/res originales.
2. Prioridad media: Steam/Epic únicamente por APIs y permisos legítimos, evitando endpoints privados o tokens de otra app.
3. Compatibilidad Windows: **no** tratar una APK base de 67 MB como todo el código/motor de emulación; estudiar runtime Wine/Box64/FEX/DXVK abierto por separado con versiones y licencias concretas.
4. Implementación y liberación: TDD RED→GREEN, CI Android, pruebas de hardware real, consentimiento, CAR-29 inalterado. La investigación **no** cierra las fases 8/9 de EVO Frontier V3.

## Procedimiento que sí se ejecutó

- Hashes y validación CRC del archivo ZIP.
- Parsing binario de AXML: identidad, SDK y declaraciones.
- Enumeración de DEX y lectura de encabezados/tablas de definición de clases; no decompilación de métodos.
- Inventario de nombres de recursos Compose y bibliotecas nativas.
- Parsing del bloque APK Signing v2: extracción de certificado, verificación de firma RSA y comparación criptográfica de digest de contenido conforme a AOSP.

## No ejecutado / no demostrado

- REA/JADX (requiere proveedor headless JADX y versión actual de Node; **no disponible en el entorno de esta inspección**).
- Análisis del comportamiento de GameHub en teléfono, identificación de backend, módulos post-instalación, runtime emulación PC y condiciones de licencia.
- Recuperación de código fuente original o reconstrucción completa de la app.

## Referencias

- https://source.android.com/docs/security/features/apksigning/v2
- https://github.com/morluto/rea/blob/main/docs/android-analysis.md
- https://gamehub.xiaoji.com/
- https://github.com/The412Banner/bannerhub-revanced/releases

**No se publican archivos originales ni contenido decompilado. Solo estadísticas y observaciones técnicas generales.**
