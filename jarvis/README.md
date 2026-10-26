# Jarvis

Jarvis es un proyecto independiente dentro del mismo repositorio que GameHub Ultra.

## Objetivo inicial

Construir un asistente personal modular con voz, razonamiento, memoria, herramientas e integraciones, sin acoplar su ciclo de desarrollo al de GameHub Ultra.

## Límites arquitectónicos

- `jarvis/` no depende de `app/` ni de `baseline-profile/`.
- GameHub Ultra no depende de `jarvis/`.
- Cualquier componente compartido futuro deberá vivir en un módulo explícito de `shared/`, con contratos estables y pruebas propias.
- Los cambios de Jarvis deben poder revisarse y probarse de forma independiente.
- No se reutilizan secretos ni credenciales de GameHub Ultra implícitamente.

## Primeras áreas

1. Core del asistente.
2. Orquestación de herramientas.
3. Memoria y contexto.
4. Voz.
5. Integraciones.
6. Cliente/UI.

Esta carpeta es la frontera inicial del proyecto. Todavía no se conecta al build Android de GameHub Ultra, para evitar aumentar sus tiempos de compilación o introducir regresiones mientras se define la arquitectura de Jarvis.
