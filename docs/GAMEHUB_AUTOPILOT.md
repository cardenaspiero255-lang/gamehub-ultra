# GameHub Ultra — PR/CAR autopilot (sin tareas programadas)

## Estado real

Configuración preparada **en PR #195, DRAFT**. Ningún merge automático ni creación/implementación desatendida de CAR está habilitado hasta que las aplicaciones externas estén instaladas, el propietario configure las protecciones de GitHub y se fusione el PR tras revisar CI. Los proveedores no pueden invocarse desde un prompt de ChatGPT cuando esta conversación no está activa.

No hay `cron`, ChatGPT Tasks, temporizadores, programaciones horarias ni GitHub Actions que reciban nuevos permisos de escritura. Todo se dispara por **eventos**: PR abierto/actualizado, checks completados, issues creados y asignados a un agente.

## Circuito PR: Mergify (aplicación externa independiente)

La regla de `.mergify.yml` es **opt-in** mediante etiqueta `automerge:approved` aplicada por el propietario *tras revisar el último commit*. Solo acepta PR normales creados por `cardenaspiero255-lang`, base `main`, sin conflictos ni estado draft.

Condiciones obligatorias:
- Ausencia de etiquetas de bloqueo `merge:blocked`, `security:review-required` o `do-not-merge`, y cero conversaciones de revisión pendientes de resolver.
- Cero checks fallidos, y éxito explícito de `@github-actions/build`, `@github-actions/coverage`, `@github-actions/Ultra Sentinel Core — own reviewer` y `@github-actions/Sentinel Core / full regression suite`.
- La política excluye cambios de `.github/**`, `.mergify.yml`, wrappers/configuración de build sensible y archivos de seguridad o credenciales.
- Mergify respetará todas las protecciones de `main` y no fusionará PR draft ni con conflictos. GitHub CI y revisiones asociadas a HEAD actual son obligatorias. Un check de infraestructura en verde **no garantiza ausencia de bugs**; el propietario debe confirmar que se investigaron los hallazgos relevantes.

**Configuración única por el propietario:**
1. Instalar <https://github.com/marketplace/mergify> **solo** en el repositorio `cardenaspiero255-lang/gamehub-ultra`, revisando los permisos del proveedor.
2. En GitHub Settings, proteger `main`: requerir PR y checks exitosos `build`, `coverage`, `Ultra Sentinel Core — own reviewer` y `Sentinel Core / full regression suite` (comprobar el origen GitHub Actions y que aparecen en todos los PR elegibles). Requerir conversaciones resueltas y rama actualizada antes de la fusión; deshabilitar pushes forzados y bypass. Evitar exigir aprobación de un revisor humano separado imposible para el autor único del repositorio; mantener un control manual en las categorías sensibles.
3. En Mergify, verificar que detecte la configuración del PR y que su check de configuración es correcto. Ajustar los nombres de checks solo si difieren de los resultados reales de GitHub; nunca degradar condiciones porque la CI falla.
4. Crear en GitHub la etiqueta `automerge:approved`. **No ponerla en el PR de bootstrap #195.** Solo aplicarla a un PR no sensible, con hallazgos confirmados resueltos, una vez que CI y revisiones actuales pasen.

**Límite de la autorización:** una etiqueta de GitHub no queda ligada a un SHA. Si se suben commits después del visto bueno, el propietario debe reexaminar el PR. Como control adicional, las protecciones de main con checks asociados al commit actualizado deben impedir un merge de CI obsoleta. Si se requiere un visto bueno criptográficamente vinculado al SHA del último commit, hace falta un integrador externo que elimine o invalide la etiqueta al evento `synchronize` antes de autorizar merge. No lo hemos instalado ni probado aún.

## Circuito CAR: Copilot cloud coding agent (app externa)

- `.github/agents/gamehub-car-engineer.agent.md`: agente específico de GameHub con prompt refinado (rol, límites, objetivo, evidencia, TDD, familias de errores y criterios de aceptación).
- `.github/ISSUE_TEMPLATE/car-agent.yml`: formulario GitHub que solicita número del CAR, dependencias, alcance y criterios. Intenta asignar el issue automáticamente a `copilot-swe-agent[bot]`, si tiene acceso a issues. La asignación de agente **puede requerir permisos o interfaz de Copilot** y el formulario no garantiza ejecución; el propietario debe comprobarla en el issue.
- El agente debe abrir PR *draft*, reproducir fallos con tests (RED), corregir familia + variantes justificadas (GREEN), refactorizar, ejecutar pruebas, registrar CI del commit y bloquear cierre por P1/P2 o errores confirmados.
- Para avanzar la **cola completa automáticamente** después de fusionar cada CAR (sin crear tareas programadas) hace falta una automatización externa por webhook como GitHub Copilot repository Automations, n8n o Make: evento `pull_request.closed` con `merged=true` y nombre `CAR-N` → buscar el siguiente issue CAR explícitamente listo, no duplicado y con dependencias completadas → asignarlo al agente → esperar PR y validaciones. No está conectada todavía. Limitar a una asignación activa para evitar bucles y conflictos.
- Los plugins de ChatGPT (GitHub y Prompt Engineer) sirven para tareas interactivas. **No** quedan ejecutándose por sí mismos fuera de la conversación.

### Prompt de delegación breve

> Implementa exclusivamente el CAR del issue vinculado en GameHub Ultra. Consulta main y roadmap, comprueba dependencias y PRs duplicados. Aplica TDD RED → GREEN → REFACTOR, busca familias y variantes con evidencia, conserva funcionalidades existentes, ejecuta pruebas Android/coverage y Ultra Sentinel en el commit final. Abre PR draft. Bloquea si hay P1/P2, CI pendiente/fallida o revisión requerida ausente. No merges, ni permisos privilegiados, ni ocultación de errores. Publica una tabla de evidencia con pruebas, SHA, revisores, límites y pendientes.

## Seguridad y validación de PR #195

El primer diseño (GitHub Actions con escritura) fue bloqueado por Ultra Sentinel como cambio de permisos inesperado y se **retiró**; nunca se incorporó a `main`. Esta revisión usa configuración declarativa de la App externa y definición de agente, sin secretos ni `write` tokens nuevos en workflows.

Revisar todos los checks del último SHA de #195, pruebas de sintaxis, seguridad y revisión independiente; fusionar este PR **solo manualmente y si está limpio**. Después conectar los servicios externos; confirmar con un PR/CAR de prueba antes de considerarlo operativo.
