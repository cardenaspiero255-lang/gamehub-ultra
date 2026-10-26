# Matriz de paridad GameSir GameHub → GameHub Ultra (especificación independiente)

**Estado 2026-10-25:** matriz de investigación; no afirma haber extraído el código GameSir ni construido/emulado las funciones que faltan. **Fuente principal:** https://gamehub.xiaoji.com/ y https://play.google.com/store/apps/details?id=com.xiaoji.egggame .

## Cómo leer la matriz

- «GameHub sitio oficial» significa función publicitada, no verificación propia en un APK o dispositivo. «Proyecto propio» exige auditoría de GameHub Ultra, no equivalencia total.
- P0 = prueba de seguridad/calidad del núcleo de GameHub Ultra; P1 = interoperabilidad relativamente acotada; P2 = nuevo runtime/streaming complejo; P3 = integración comercial dependiente de terceros.
- No se construye ni distribuye una copia de UI, código decompilado, assets, marca ni endpoints privados de GameSir. El diseño autónomo debe usar APIs y componentes con licencias verificadas.

| ID | Subsistema | Comportamiento a estudiar | Fuente | Observación/REA legítima | Arquitectura independiente | Gate reproducible | Pri. |
|---|---|---|---|---|---|---|---|
| A01 | Inicio y biblioteca | Lista de juegos, favoritos, filtro y categorías | Android GameHub/Google Play | UI capturas locales en versión SHA fijada | Kotlin catálogo + Compose sin recursos originales | JVM estado + Android UI/E2E | P0 |
| A02 | Mandos/GameSir | Detección, botones, ejes y desconexión | GameSir soporte oficial | InputDevice y pruebas físicas con dispositivo propio | Adaptador por capabilities, no firmware propietario | Conexión/reconexión, joystick, desconexión | P0 |
| A03 | Importar juegos locales | Importar rutas permitidas sin acceso total automático | GameHub sitio oficial | Photo/Document Picker autorizado | SAF y metadatos locales con validaciones | Contenido ausente, corrupto, denegado | P1 |
| A04 | Lanzamiento Android | Abrir app instalada y gestionar errores | GameHub Ultra propio | Android package manager | Intent explícito seguro por paquete | Paquete ausente, resolver falso | P0 |
| A05 | Perfil por juego | Preferencias, controles y reversión del perfil | GameHub Ultra propio | Comparar función UI objetivo sin fuente ajena | DataStore y checkpoint versionado | Rollback, crash, replay | P0 |
| A06 | Steam/Epic biblioteca | Enlazar librería de cuentas autorizadas | GameHub sitio oficial | Documentación pública APIs/OAuth | Conector según APIs y permisos oficiales | Revocación, token caducado, logout | P1 |
| A07 | PC en Android | Compatibilidad Windows mediante runtime separado | GameHub sitio oficial | APK ABI + observación ejecución local con derechos | Explorar Wine/Box64/FEX/DXVK y licencias | Conjunto mínimo de juegos propios, ABI compatible | P2 |
| A08 | Gráficos Vulkan | Render/composición, GPU y limitaciones | DXVK/Android upstream | Versiones y soporte Vulkan reales | Adaptador opcional; no afirmar soporte universal | Fallback, crash y rendimiento reproducible | P2 |
| A09 | Streaming PC propio | Sesión remota autorizada de PC a Android | GameHub sitio oficial | Protocolo cliente/servidor documentado | Cliente de streaming licenciado con autenticación | Pérdida de red, latencia, cancelación | P2 |
| A10 | Cloud gaming | Lanzamiento por proveedor remoto | GameHub sitio oficial | Términos e integración autorizada | Solo enlaces e integraciones permitidos | No existe gratis si proveedor requiere pago | P3 |
| A11 | Datos de rendimiento | FPS real, batería, temperatura, conectividad | Proyecto propio + Android vitals | SurfaceFlinger y métricas dispositivo | Telemetría no privilegiada con no-data explícito | FPS nulo si no hay frames, p95 real | P0 |
| A12 | Voz Ultra | Asistente manos libres opt-in y STOP | Proyecto propio | Sesiones ASR/TTS y permisos Android | Agente propio en español, consentimiento | STOP, callback tardío, bloqueo y revocación | P0 |
| A13 | Versionado componentes | Plugins/runtime legítimos y checksums | Proyectos open-source | Firmas/releases oficiales y SPDX | Registro por licencia+hash, no APK ajeno | Checksum inválido y rollback | P2 |
| A14 | Seguridad y privacidad | Sin copiar datos o tokens GameSir | MASVS/Android | Auditoría permisos y proveedores | Permisos mínimos y controles de exportación | No PII/no secretos en logs y artefactos | P0 |

## Regla de implementación por bloques

1. **Primero cerrar EVO Frontier V3 fases 8/9:** no mezclar emulación PC compleja con PRs bloqueados por CAR-29, seguridad Sentry o pruebas físicas.
2. Capturar versión, origen de APK oficial, SHA-256, package, firma y manifest; solo sobre un dispositivo/archivo propio o permitido. Un benchmark de otra versión NO es evidencia del objetivo.
3. Elegir **un subsistema** (A01/A02 preferibles) y generar especificaciones de usuario con entradas/salidas observadas. No pegar pseudocódigo GameSir como implementación.
4. Crear tests RED de comportamiento en GameHub Ultra; implementar código original GREEN; ejecutar CI Android y pruebas físicas apropiadas.
5. Registrar fallos, licencias de dependencias, autorizaciones y riesgos. Mantener todos los módulos de compatibilidad opcionales y aislados de perfiles gaming/voz.

## Dependencias abiertas a evaluar, no incorporadas

- Winlator: https://github.com/brunodev85/winlator . Verificar licencia exacta del árbol y dependencias antes de reutilizar o redistribuir.
- Box64: https://github.com/ptitSeb/box64 . MIT según repositorio; no implica compatibilidad directa con cualquier APK ARM.
- FEX: https://github.com/FEX-Emu/FEX . MIT según LICENSE; evaluar compatibilidad y arquitectura.
- DXVK: https://github.com/doitsujin/dxvk . licencia zlib/libpng; Direct3D 9-11 a Vulkan.
- Proton: https://github.com/ValveSoftware/Proton . El proyecto distribuye varios componentes con licencias diferentes.
- GameSir Labs: https://github.com/gamesir-labs . Algunos repos son forks públicos, no el frontend GameHub.

## Estado de pruebas

| Área | Estado |
|---|---|
| 6 tests de inventario de ZIP/APK sintética local | GREEN, 2026-10-25 (no GameSir) |
| Descargar e inspeccionar APK oficial GameSir | NOT_RUN |
| Observar GameHub oficial en dispositivo legítimo | NOT_RUN |
| Clonar fuente original GameSir | NO; no se dispone de fuente original licenciada |
| Implementar características nuevas a partir de REA | NOT_RUN |
| Certificar paridad y calidad de release | NOT_RUN |
