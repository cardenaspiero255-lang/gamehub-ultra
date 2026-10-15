# Arquitectura inicial de Jarvis

## Principio

Jarvis y GameHub Ultra comparten repositorio, no ownership de código.

```text
repository/
├── app/                 # GameHub Ultra
├── baseline-profile/    # GameHub Ultra performance tests
├── jarvis/              # Jarvis
│   ├── core/
│   ├── integrations/
│   ├── voice/
│   ├── memory/
│   └── docs/
└── shared/              # Solo cuando exista una necesidad real compartida
```

## Reglas

1. Dependencias dirigidas hacia contratos, no implementaciones.
2. Ninguna importación desde Jarvis hacia internals de GameHub Ultra.
3. Ninguna importación desde GameHub Ultra hacia internals de Jarvis.
4. Las integraciones externas se encapsulan detrás de interfaces.
5. La memoria y credenciales de cada aplicación permanecen separadas.
6. Todo comportamiento nuevo se desarrolla con pruebas.
7. Los workflows de Jarvis se aislarán por rutas para no ejecutar builds innecesarios de GameHub Ultra.

## Fases

### JAR-1 — Foundation
Definir límites, arquitectura, stack y pipeline independiente.

### JAR-2 — Assistant Core
Entrada normalizada, intents, respuestas y contratos de herramientas.

### JAR-3 — Tool Orchestrator
Registro, autorización, ejecución y manejo de fallos de herramientas.

### JAR-4 — Memory
Memoria de sesión y persistente con políticas explícitas.

### JAR-5 — Voice
Wake word, STT/TTS y conversación continua.

### JAR-6 — Integrations
Servicios externos mediante adaptadores independientes.

### JAR-7 — UI
Cliente visual desacoplado del core.
