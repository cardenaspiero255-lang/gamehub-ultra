# Política de revisores externos — GameHub Ultra

**Actualización: 11 de octubre de 2026.** Por decisión explícita del propietario, las revisiones de Codex quedan **suspendidas indefinidamente** debido al agotamiento de créditos. Esta decisión sustituye la autorización del 9 de octubre. No existe fecha de reactivación; solo una nueva instrucción explícita del propietario podrá levantarla.

## Codex — prohibido hasta nueva autorización

- No solicitar revisiones mediante `@codex review`, asignar el bot, utilizar otras invocaciones de Codex ni desencadenar tareas de Codex en este repositorio.
- No habilitar revisiones automáticas de Codex para PR nuevos, actualizaciones, ramas apiladas ni ningún otro evento.
- No reactivar Codex porque se repongan créditos automáticamente: esperar autorización expresa.
- Conservar el historial de hallazgos antiguos como evidencia; no cerrarlos como resueltos sin reproducir sus casos y validar las correcciones.
- No vincular la aprobación de PR a una nueva revisión de Codex mientras esta suspensión siga vigente: usar CI, pruebas adversariales, Ultra Sentinel y una revisión alternativa independiente cuando sea necesaria. Ningún CI verde ni revisor automatizado implica garantía de ausencia de vulnerabilidades.

## Qodo Open Source — reactivado con autorización del propietario (11 de octubre de 2026)

El propietario ha autorizado explícitamente Qodo Open Source como revisor alternativo de GitHub para los PR #173, #169 y las futuras actualizaciones del repositorio. Permitir únicamente `/agentic_review` y su disparo automático al abrir o actualizar PR a través de `.pr_agent.toml`. La aprobación, aplicación de sugerencias, modificación de código y fusión automáticas siguen **prohibidas**. Los hallazgos deben reproducirse y contrastarse con pruebas antes de corregirlos. La instalación de la GitHub App y sus permisos reales se administran fuera del archivo TOML.

## CodeRabbit — sigue desactivado

No solicitar ni invocar revisiones de CodeRabbit, ni habilitar sus revisiones automáticas sin autorización expresa. Conservar `.coderabbit.yaml` con sus disparadores desactivados; su desactivación no desinstala necesariamente la GitHub App.

## Configuración de GitHub y Codex

**Esta política es una prohibición operativa, no un control técnico de la GitHub App.** Para impedir nuevos consumos automáticos, el propietario también debe retirar a Codex el acceso a `cardenaspiero255-lang/gamehub-ultra` desde GitHub → Settings → Applications → Installed GitHub Apps → Codex → Configure → Repository access (o desinstalar esa GitHub App), y comprobar el estado de Auto review en la configuración oficial de Codex. Si no hay permiso o herramienta para modificar esa instalación, no afirmar que está desconectada.

Este cambio no modifica Android Build, Coverage, las pruebas Core ni Ultra Sentinel. No conceder aprobaciones humanas ficticias, no forzar ni automatizar merges por el resultado de otro bot.
