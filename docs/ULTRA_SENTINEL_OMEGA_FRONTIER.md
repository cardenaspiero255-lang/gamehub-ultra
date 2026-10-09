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
