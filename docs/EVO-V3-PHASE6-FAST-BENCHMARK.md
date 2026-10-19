# EVO Frontier V3 — prueba rápida de FPS y frame pacing (Fase 6)

Este procedimiento es **opcional, de corta duración y sin instalar una APK nueva**. Permite analizar una sesión **real** en Android con `SurfaceFlinger --timestats` (OpenGL, Vulkan y capas Android), batería, estado térmico y una prueba de latencia a una IP pública. No necesita permisos root.

## Ejecución (Vivo V25 Pro u otro Android)

Necesitas **Python 3.9+**, **ADB/Android Platform Tools** en el PATH, un cable USB con depuración USB autorizada y el juego ya abierto en el teléfono. En tu PC con Windows, desde la carpeta del repositorio:

```powershell
adb devices
python scripts/quick_game_benchmark.py
```

Con **un solo teléfono conectado**, el script intenta detectar automáticamente el paquete de la aplicación en primer plano. La captura dura **15 segundos** de juego (más unos segundos para los comandos ADB). Mueve el personaje o juega normalmente durante la captura. Genera un informe local `quick-game-benchmark.json` que puedes compartir para revisar los resultados.

Si el juego no se detecta automáticamente:

```powershell
python scripts/quick_game_benchmark.py --package com.ejemplo.juego --seconds 15
```

Para una prueba aún más breve (sin ping público):

```powershell
python scripts/quick_game_benchmark.py --seconds 8 --no-ping
```

La duración permitida es de **5 a 45 segundos**. El script no lanza Gradle, no instala aplicaciones, no cambia perfiles, no toca CPU/GPU ni abre puertos. No requiere repetir pruebas físicas para verificar cada parche: los cálculos y manejo de errores se prueban automáticamente en CI en milisegundos.

## ¿Qué resultados son auténticos?

| Campo | Origen e interpretación |
|---|---|
| `fps_presented` | FPS **presentados** por la superficie del juego indicada por `SurfaceFlinger`; no frecuencia de pantalla (Hz) ni FPS inferidos de otra aplicación |
| `p95_present_interval_ms` y `p99_present_interval_ms` | Tiempos entre presentaciones, **aproximados por el histograma**; indican estabilidad/pacing, no tiempo de GPU o CPU individual |
| `slow_30fps_percent` / `slow_20fps_percent` | Porcentaje de intervalos de presentación de 34 ms o más / más de 50 ms; no es pérdida de fotogramas del motor interno |
| `battery_start`, `battery_end` | Porcentaje y **sensor de batería** Android (no temperatura CPU/GPU); 15 s no bastan para calcular autonomía ni degradación sostenida |
| `android_thermal_status_start/end` | Estado térmico categórico Android cuando esté disponible; nunca °C |
| `public_diagnostic_ping_ms` | Latencia opcional a `1.1.1.1` para comprobar conectividad general; **NO** es el ping del servidor del juego |

**Fail-closed:** si faltan privilegios para TimeStats, el juego no está abierto, no hay suficientes fotogramas, el dispositivo no publica la superficie o hay varias superficies del juego ambiguas, `fps_presented` será `null` y se indicará el motivo. No se sustituyen datos con FPS de pantalla, resultados de otros juegos ni estadísticas globales. Si hay varias capas, la salida indicará sus nombres; puedes elegir una por su nombre completo con `--layer "..."`.

El test no acredita por sí solo comportamiento de 30-60 minutos, compatibilidad de todos los juegos, experiencia real de TTS/ASR ni estabilidad a largo plazo. Es una primera medición reproducible y **rápida**. La precisión depende de que Android exponga las estadísticas de presentación de la capa del juego.

## Autopruebas sin teléfono — una fracción de segundo

```powershell
python -m unittest discover -s scripts/tests -p 'test_quick_game_benchmark.py' -v
```

Prueban selección de capa, FPS no fabricados, histogramas, campos ausentes, identificación de juego en primer plano, errores ADB y restauración del muestreo. También existe el workflow independiente **Fast Game Benchmark Unit Tests**; no instala ni compila la APK. Los tests con datos simulados **validan la lógica, no aportan mediciones físicas**.

## Referencias oficiales

- [Android Developers — Slow Sessions / SurfaceFlinger TimeStats](https://developer.android.com/topic/performance/issues/slow-session)
- [Android Developers — Game frame rate / SurfaceFlinger](https://developer.android.com/games/optimize/framerate)
- [Android Developers — dumpsys / gfxinfo](https://developer.android.com/tools/dumpsys)

No subas al repositorio informes JSON individuales con datos del dispositivo, salvo que expresamente quieras publicarlos.