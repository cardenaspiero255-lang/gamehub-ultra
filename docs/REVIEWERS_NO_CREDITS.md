# Política de revisores externos — GameHub Ultra

**Actualización: 11 de octubre de 2026.** Por decisión explícita del propietario, las revisiones de Codex quedan **suspendidas indefinidamente** debido al agotamiento de créditos. Esta decisión sustituye la autorización del 9 de octubre. No existe fecha de reactivación; solo una nueva instrucción explícita del propietario podrá levantarla.

## Codex — prohibido hasta nueva autorización

- No solicitar revisiones mediante `@codex review`, asignar el bot, utilizar otras invocaciones de Codex ni desencadenar tareas de Codex en este repositorio.
- No habilitar revisiones automáticas de Codex para PR nuevos, actualizaciones, ramas apiladas ni ningún otro evento.
- No reactivar Codex porque se repongan créditos automáticamente: esperar autorización expresa.
- Conservar el historial de hallazgos antiguos como evidencia; no cerrarlos como resueltos sin reproducir sus casos y validar las correcciones.
- No vincular la aprobación de PR a una nueva revisión de Codex mientras esta suspensión siga vigente: usar CI, pruebas adversariales, Ultra Sentinel y una revisión alternativa independiente cuando sea necesaria. Ningún CI verde ni revisor automatizado implica garantía de ausencia de vulnerabilidades.

## Qodo y CodeRabbit — siguen desactivados

No solicitar ni invocar sus revisiones, comentarios o ejecuciones, ni habilitar sus revisiones automáticas hasta nueva autorización explícita. Conservar las configuraciones de desactivación `.coderabbit.yaml` y `.pr_agent.toml` si están presentes. La evaluación de alternativas gratuitas no autoriza su instalación ni reactivación.

## Configuración de GitHub y Codex

**Esta política es una prohibición operativa, no un control técnico de la GitHub App.** Para impedir nuevos consumos automáticos, el propietario también debe retirar a Codex el acceso a `cardenaspiero255-lang/gamehub-ultra` desde GitHub → Settings → Applications → Installed GitHub Apps → Codex → Configure → Repository access (o desinstalar esa GitHub App), y comprobar el estado de Auto review en la configuración oficial de Codex. Si no hay permiso o herramienta para modificar esa instalación, no afirmar que está desconectada.

Este cambio no modifica Android Build, Coverage, las pruebas Core ni Ultra Sentinel. No conceder aprobaciones humanas ficticias, no forzar ni automatizar merges por el resultado de otro bot.
