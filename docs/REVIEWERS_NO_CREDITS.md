# Política permanente: revisores externos desactivados

Desde el 9 de octubre de 2026, por decisión del dueño de GameHub Ultra, **Qodo, CodeRabbit y Codex NO son revisores de este repositorio**. No solicitar revisiones manuales, no invocar sus comandos de PR ni hacer obligatoria su aprobación. No habilitar sus revisiones automáticas ni consumir créditos de estos servicios sin nueva autorización explícita.

Revisión válida sin coste externo: inspección manual de código, pruebas Node/Gradle, Android Build, Unit Test Coverage, mutation/reliability, revisión independiente confiable de Ultra Sentinel y logs/evidencia del mismo commit. Mantener los controles de seguridad: cero errores confirmados antes de fusionar y no tratar gates omitidos como aprobados. Un proveedor externo no es requisito ni sustituto de CI independiente.

Las configuraciones `.coderabbit.yaml` y `.pr_agent.toml` desactivan sus disparadores gestionables por archivo. **No eliminan automáticamente GitHub Apps**, ni garantizan detener las llamadas ya iniciadas o configuraciones de cuenta/organización. Para prevenir consumos no autorizados de forma permanente, quitar la autorización del repositorio en GitHub → Settings → Applications → Installed GitHub Apps → Configure, y desactivar Code Review en Codex. Si se exige detener absolutamente todo, eliminar acceso a este repositorio de cada una de las tres Apps.

Menciones históricas de los bots en PRs, commits y benchmarks NO constituyen una instrucción para invocarlos.
