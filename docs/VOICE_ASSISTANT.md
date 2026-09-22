# Asistente de voz de GameHub Ultra

CAR-12 añade un asistente de voz opcional con reconocimiento puntual dentro de la app y una arquitectura compatible con el asistente de Android mediante VoiceInteractionService y VoiceInteractionSessionService.

Comandos principales:
- Abre Resident Evil 4 Remake
- Abre Resident Evil 4 Remake en X4
- Pon X4
- Prioriza interpolación
- FPS balanceado
- Dime la temperatura
- Dime el estado
- Ayuda

“Configura todo” significa seleccionar el preset X4 dentro de las funciones reales de GameHub Ultra. No significa modificar internamente el juego.

Todas las órdenes pasan por VoiceCommandEngine. El motor solo permite acciones explícitas y bloquea X4 cuando el dispositivo no informa soporte de Sustained Performance. La capa opcional NaturalLanguageIntentResolver permite conectar un intérprete de IA, pero la IA solo puede producir un VoiceCommand permitido.

La app solicita RECORD_AUDIO únicamente al pulsar “Hablar”. No implementa escucha permanente personalizada. La interacción puntual no requiere un foreground service de micrófono.

Android 12+ limita el inicio de foreground services desde segundo plano y Android 14+ impone requisitos adicionales para servicios de micrófono. La implementación actual evita depender de ese camino.

GameHub Ultra no afirma controlar clocks de CPU/GPU, generar frames dentro de otros juegos ni modificar sus archivos o ajustes internos.
