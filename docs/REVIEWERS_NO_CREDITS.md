# Política de revisores externos — GameHub Ultra

**Actualización: 11 de octubre de 2026.** Por decisión explícita del propietario, las revisiones de Codex quedan **suspendidas indefinidamente** debido al agotamiento de créditos. Esta decisión sustituye la autorización del 9 de octubre. No existe fecha de reactivación; solo una nueva instrucción explícita del propietario podrá levantarla.

## Codex — prohibido hasta nueva autorización

- No solicitar revisiones mediante `@codex review`, asignar el bot, utilizar otras invocaciones de Codex ni desencadenar tareas de Codex en este repositorio.
- No habilitar revisiones automáticas de Codex para PR nuevos, actualizaciones, ramas apiladas ni ningún otro evento.
- No reactivar Codex porque se repongan créditos automáticamente: esperar autorización expresa.
- Conservar el historial de hallazgos antiguos como evidencia; no cerrarlos como resueltos sin reproducir sus casos y validar las correcciones.
- No vincular la aprobación de PR a una nueva revisión de Codex mientras esta suspensión siga vigente: usar CI, pruebas adversariales, Ultra Sentinel y una revisión alternativa independiente cuando sea necesaria. Ningún CI verde ni revisor automatizado implica garantía de ausencia de vulnerabilidades.

## Qodo Open Source — suspendido indefinidamente (11 de octubre de 2026)

El propietario ha ordenado **desactivar Qodo indefinidamente**, revocando la autorización anterior. **No solicitar** `/agentic_review`, `/review`, `/improve`, ni otras invocaciones Qodo, **ni habilitar** revisiones automáticas, comentarios de checks, aprobaciones o correcciones. Solo una nueva instrucción explícita del propietario podrá reactivarlo.

El archivo `.pr_agent.toml`, tanto en `main` como en la rama activa de PR #169, configura `[config] disable_auto_feedback=true` e `ignore_repositories=["cardenaspiero255-lang/gamehub-ultra"]`, vacía `github_app.pr_commands` y `push_commands`, y pone `handle_push_trigger=false`, `review_agent.enabled=false`, `publish_output=false` y la revisión y reparación automáticas desactivadas. Esto evita las invocaciones de Qodo **según la configuración del repositorio**, pero **no revoca permisos** de una GitHub App ya instalada.

Para impedir de forma completa el acceso de la aplicación alojada, el propietario debe ir a [GitHub Installed GitHub Apps](https://github.com/settings/installations), seleccionar Qodo, y retirar el acceso a `cardenaspiero255-lang/gamehub-ultra` o desinstalar Qodo. Los conectores disponibles aquí no modifican instalaciones de apps ajenas; **no afirmar que Qodo está desinstalado** por editar TOML.

La ausencia de revisiones Qodo no cuenta como aprobación independiente. Seguir utilizando pruebas, CI y revisiones alternativas autorizadas, sin fingir acreditación de terceros.

## CodeRabbit — sigue desactivado

No solicitar ni invocar revisiones de CodeRabbit, ni habilitar sus revisiones automáticas sin autorización expresa. Conservar `.coderabbit.yaml` con sus disparadores desactivados; su desactivación no desinstala necesariamente la GitHub App.

## Configuración de GitHub y Codex

**Esta política es una prohibición operativa, no un control técnico de la GitHub App.** Para impedir nuevos consumos automáticos, el propietario también debe retirar a Codex el acceso a `cardenaspiero255-lang/gamehub-ultra` desde GitHub → Settings → Applications → Installed GitHub Apps → Codex → Configure → Repository access (o desinstalar esa GitHub App), y comprobar el estado de Auto review en la configuración oficial de Codex. Si no hay permiso o herramienta para modificar esa instalación, no afirmar que está desconectada.

Este cambio no modifica Android Build, Coverage, las pruebas Core ni Ultra Sentinel. No conceder aprobaciones humanas ficticias, no forzar ni automatizar merges por el resultado de otro bot.
