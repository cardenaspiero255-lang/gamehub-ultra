# CAR-6 — Auditoría técnica de GameHub Ultra

Fecha de auditoría: 2026-09-22
Rama de auditoría: `cardenaspiero255/car-6-auditoria-y-plan-tecnico-de-gamehub-ultra`

## 1. Estado comprobado

`main` contiene una aplicación Android independiente con applicationId `com.cardenaspiero255.gamehubultra`, separada de la aplicación GameHub original.

| Área | Estado actual | Evidencia / alcance |
| --- | --- | --- |
| Estructura Android | Completa | Proyecto Gradle Android con Kotlin + Compose, minSdk 26. |
| Compilación y CI | Verde al cierre de CAR-12 | GitHub Actions valida tests JVM, APK debug, lint y artefacto. |
| Detección de hardware | Completa | CPU/SoC, ABI, núcleos, RAM, GPU por EGL y capacidades Android. |
| Perfiles | Completa | FPS balanceado, Priorizar interpolación y X4 con políticas seguras. |
| Interfaz | Completa | Dashboard Compose, biblioteca, ajustes, telemetría de batería/térmica y tema rojo/negro/dorado. |
| Biblioteca | Funcional | Descubrimiento de juegos, selección persistente y lanzamiento seguro cuando Android expone una actividad válida. |
| Compatibilidad | Reforzada | Pruebas de lanzamiento, control de errores y guardas de capacidades. |
| Asistente por voz | Completo | Reconocimiento puntual, TTS, parser español/inglés, integración opcional con asistente del sistema. |
| Documentación | Base creada | README, documentación de voz y este plan técnico. |
| Android 16 | Pendiente | `compileSdk/targetSdk` siguen en 35; siguiente fase debe migrar a API 36 y validar cambios de comportamiento. |
| Arquitectura a escala | Pendiente | `MainActivity.kt` concentra demasiado estado/UI; siguiente fase debe separar UI, ViewModel, repositorios, dominio y plataforma. |
| Engine adaptativo | Pendiente | Falta combinar temperatura, thermal headroom, batería, pantalla y carga sostenida en una política dinámica. |
| IA local | Pendiente | Existe una base determinista/allowlist para voz; la siguiente fase puede añadir IA en dispositivo con fallback seguro. |
| Release/performance | Pendiente | Falta Baseline Profile, optimización release y benchmarks de arranque/fluidez. |

## 2. Funcionalidad ya implementada y límites reales

GameHub Ultra ya puede inspeccionar información pública del dispositivo, mostrar capacidades y seleccionar perfiles.

Los perfiles no deben describirse como overclock, frame generation universal ni control directo de otro juego. Android no ofrece una API pública general para que una aplicación acompañante fuerce relojes CPU/GPU, inyecte interpolación X4 en otro proceso o cambie arbitrariamente el Game Mode de otra aplicación. Las acciones futuras deben seguir el mismo principio: ejecutar únicamente capacidades que el sistema o un OEM expongan de forma documentada.

El perfil X4 se mantiene como una intención de rendimiento sostenido compatible con la plataforma, no como una promesa de generación de frames.

El asistente de voz es de captura puntual y opcional. No se debe convertir en un hotword permanente propio sin un diseño explícito para las restricciones de micrófono y servicios en segundo plano.

## 3. Riesgos y prioridades detectadas

### P0 — Plataforma

Migrar el proyecto a Android 16 (API 36), actualizar el toolchain compatible y probar los cambios de comportamiento asociados a target 36. Desde el 31 de agosto de 2026, Google Play exige API 36+ para nuevas apps y actualizaciones de apps Android estándar.

### P0 — Arquitectura

Reducir la concentración de responsabilidades en `MainActivity.kt`. Mantener una arquitectura con estado de pantalla, ViewModels, repositorios y capas de dominio/plataforma que permita añadir funciones sin aumentar el acoplamiento.

### P1 — Motor de rendimiento adaptativo

Crear una política que combine thermal status/headroom, batería, carga, ahorro de energía, sesión y capacidades sostenidas. Debe incorporar histéresis para evitar cambios de perfil demasiado frecuentes.

### P1 — Perfil por juego

Persistir configuración independiente por paquete/juego: perfil, preferencias térmicas, objetivo de refresco y opciones permitidas. El sistema debe poder restaurar el último estado sin fingir que modificó ajustes internos del juego.

### P1 — Diagnóstico Gaming Readiness

Un módulo único debe calcular una lectura explicable de:
- CPU/SoC, GPU y RAM.
- estado térmico.
- batería y carga.
- refresco disponible.
- conectividad y ancho de banda estimado.
- almacenamiento libre.
- periféricos conectados.

### P1 — Pantalla y red

Detectar los modos de refresco expuestos por Android y mostrar recomendaciones sin prometer forzar una frecuencia en otra aplicación. Añadir monitor de conectividad con transporte, red medida y estimaciones disponibles, y métricas de latencia cuando puedan medirse de forma fiable.

### P2 — Experiencia

Biblioteca inteligente con favoritos, recientes, juego seleccionado y perfil por juego. Añadir accesos rápidos del sistema y, donde corresponda, un Quick Settings Tile para una acción frecuente de GameHub Ultra.

### P2 — IA

Añadir un asesor local cuando el dispositivo sea compatible, usando contexto de hardware/temperatura/batería/pantalla/juego. Toda ejecución de acciones debe seguir una allowlist determinista; la IA no podrá generar comandos arbitrarios del sistema.

### P2 — Rendimiento de la propia app

Añadir Baseline Profile, revisar estabilidad Compose, reducir trabajo en recomposición, optimizar release y añadir benchmarks de inicio/navegación/biblioteca.

## 4. Plan consolidado después de CAR-6

### CAR-13 — GameHub Ultra Core 2.0
- Migración a Android 16/API 36 y toolchain compatible.
- Refactor de arquitectura.
- DataStore para preferencias/configuración.
- Per-game profiles.
- Biblioteca inteligente.
- App shortcuts.
- Contratos de estado y capacidades estables para el resto de módulos.

### CAR-14 — GameHub Performance Engine
- Thermal Engine con thermal status + thermal headroom.
- Batería/carga/ahorro de energía.
- Sustained performance y Performance Hint cuando corresponda.
- Asesor de refresco.
- Gaming Readiness.
- Monitor de conectividad.
- Historial de sesiones.
- Diagnóstico de almacenamiento/periféricos.

### CAR-15 — GameHub AI
- Asesor IA local cuando haya soporte.
- Contexto de dispositivo y sesión.
- Fallback determinista offline.
- Integración profunda con la interfaz y voz.
- Guardas de permisos y allowlist para cualquier acción.

### CAR-16 — Gaming Experience & Release
- Gamepad/teclado/ratón/audio externo.
- Quick Settings.
- Pulido UX/accessibilidad.
- Baseline Profile y optimización de release.
- Benchmarks.
- Matriz de compatibilidad.
- APK/AAB de release validado.

## 5. Criterio de finalización de cada fase

Una fase solo se considera terminada cuando:
1. El código está integrado en `main`.
2. Las pruebas relevantes pasan.
3. Debug APK compila.
4. Lint pasa sin introducir nuevos errores.
5. No se documentan capacidades que Android no permita realmente.
6. Las funciones nuevas tienen fallback seguro cuando la capacidad del dispositivo no existe.

## 6. Referencias oficiales

- Android 16 SDK setup: https://developer.android.com/about/versions/16/setup-sdk
- Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Android architecture guidance: https://developer.android.com/topic/architecture
- Performance hints: https://developer.android.com/reference/android/os/PerformanceHintManager
- Thermal headroom: https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(float)

## Resultado de CAR-6

La auditoría original queda sustituida por este plan técnico actualizado sobre el estado real posterior a CAR-12. El orden de ejecución queda fijado como CAR-13 → CAR-14 → CAR-15 → CAR-16.
