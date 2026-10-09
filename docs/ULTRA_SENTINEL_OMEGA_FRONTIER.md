# Ultra Sentinel OMEGA + Frontier — alcance comprobable

Este documento se aplica al PR #167 y no implica que el codigo sea matematicamente infalible.

## OMEGA: contrato de estados de la voz

- Fuente del modelo: `.github/sentinel-contracts/voice-retry.omega.json`.
- Verificador abstracto: `.github/scripts/ultra_sentinel_omega.cjs`.
- Estados: `IDLE`, `RETRY_PENDING` y `CLOSED`.
- Operaciones: `SCHEDULE`, `DISPATCH`, `RESET` y `CLOSE`.
- La maquina de estados tiene 12 transiciones deterministas verificadas exhaustivamente.
- `CLOSED` es terminal y no permite reintentos posteriores.
- `VoiceRecognitionRetryGateContractTest.kt` contrasta 16.384 trazas de 7 operaciones contra la implementacion Kotlin real.
- `VoiceAssistantControllerRobolectricTest.kt` prueba que callbacks tardios despues de `release()` no disparen acciones.

### Lo que NO garantiza

El verificador demuestra propiedades **solo** del modelo finito declarado. Las pruebas Kotlin
comparan el modelo con casos concretos del programa, pero no prueban formalmente el bytecode,
los servicios Android del fabricante, todos los hilos ni una latencia maxima de 100 ms.
Se requiere comprobar los tests Android y coverage completos del SHA vigente.

## Frontier: historial y mapa diagnostico

El generador `.github/scripts/ultra_sentinel_frontier_map.cjs` inspecciona Git local de
forma offline, acotada (hasta 1.500 commits sin merges) y sin ejecutar codigo historico.
Genera `frontier-history.json` y `frontier-history.html` navegable.

Desde la raiz de un clon con historial Git disponible:

```sh
mkdir -p frontier-reports
SENTINEL_MAP_OUT=frontier-reports SENTINEL_MAP_LIMIT=1500 node .github/scripts/ultra_sentinel_frontier_map.cjs
```

Cuando el PR llegue a main, el workflow manual `Ultra Sentinel Frontier History Map`
podra generar el mapa como artefacto privado de GitHub Actions.

Cada enlace indica dos commits que modificaron un mismo archivo; **NO es evidencia
de que un commit causara el fallo de otro**. Las causas se deben confirmar mediante
reproduccion con un test RED, logs y, cuando corresponda, bisect.

## Politica de autonomia

El inspector puede entregar diagnosticos, advertencias y `fix.patch` limitados.
No auto-fusionar codigo ni parchear en vivo el APK de usuarios. No borrar
protecciones solo por ausencia de crashes observados. No considerar verde un
workflow inconcluso, omitido indebidamente o asociado a otro SHA.


## Autovalidacion: Sentinel vs Sentinel

- El workflow `.github/workflows/ultra-sentinel-self-review.yml` incluye dos evaluaciones separadas:
  - `pull_request`: pruebas del candidato en un runner con permisos de lectura, sin credenciales persistidas. **No es una revision independiente**.
  - `pull_request_target`: despues de integrar el workflow en `main`, se comprueba el PR con el analizador estable de la rama predeterminada, sin ejecutar ni descargar codigo de la rama del PR.
- El motor `.github/scripts/ultra_sentinel_selfreview.cjs` analiza exclusivamente datos del diff fijados a un SHA verificado antes y despues. Rechaza cambios inseguros de permisos, checkout de PR no confiable en contextos privilegiados, acciones nuevas no fijadas y nuevos permisos de auto-merge.
- Las pruebas `.github/scripts/ultra_sentinel_selfreview.test.cjs` incluyen entradas adversariales. La bateria independiente de mutaciones de JavaScript continua siendo un control adicional.
- La herramienta puede devolver `BLOCKED` o `ADVISORY`. `ADVISORY` **no significa aprobado**. Toda modificacion del propio gate requiere revision humana y controles de branch protection con checks requeridos.
- No usa secretos de proveedores ni concede permisos para fusionar, cambiar ramas o aplicar reparaciones.
- Mientras el workflow aun no este en `main`, solo se ejecutan sus tests de candidato. **La autovalidacion independiente no se activa hasta entonces**.
