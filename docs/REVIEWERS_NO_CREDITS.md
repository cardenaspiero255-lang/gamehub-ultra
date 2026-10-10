# Política de revisores externos — GameHub Ultra

Actualización: 9 de octubre de 2026. El propietario ha autorizado explícitamente **volver a usar Codex para revisiones de código**. Esta autorización sustituye la prohibición anterior de Codex. La prohibición continúa vigente para Qodo y CodeRabbit.

## Codex — permitido

- Se pueden solicitar revisiones de pull requests de Codex, incluidas las realizadas mediante `@codex review`.
- También pueden habilitarse revisiones automáticas de Codex para este repositorio mediante la configuración oficial de Codex si el propietario decide utilizarlas.
- El uso puede consumir cuota o créditos del servicio. La autorización comprende las revisiones de Codex, no habilita modelos o proveedores adicionales.
- Los hallazgos de Codex son hipótesis técnicas: reproducir los errores y comprobar las correcciones con pruebas. No considerar una revisión limpia garantía de ausencia de vulnerabilidades.
- Codex no sustituye Android Build, Unit Test Coverage, Core Tests, Mutation Lab, el revisor independiente de Ultra Sentinel, las protecciones de `main` ni las aprobaciones humanas exigidas por la política de seguridad. No conceder aprobación humana falsa ni habilitar auto-merge por el resultado del bot.

## Qodo y CodeRabbit — siguen desactivados

No solicitar ni invocar sus revisiones, comentarios o ejecuciones y no habilitar sus revisiones automáticas. Las configuraciones `.coderabbit.yaml` y `.pr_agent.toml` deben conservar la desactivación de sus disparadores gestionables por archivo. Su desactivación en el repositorio no desinstala necesariamente las GitHub Apps: revocar su acceso se gestiona por separado desde GitHub → Settings → Applications.

## Configuración externa

Cambiar esta política **no activa por sí solo** la GitHub App de Codex ni sus revisiones automáticas si fueron deshabilitadas desde la cuenta. Para reactivar el procesamiento real, conservar o restablecer el acceso de Codex al repositorio y habilitar Code Review en la configuración oficial de Codex. Una solicitud manual `@codex review` permite comprobar el funcionamiento si la integración está disponible.

Las referencias históricas en PR anteriores que prohíben Codex quedan reemplazadas por esta actualización; la exclusión de Qodo y CodeRabbit permanece vigente.
