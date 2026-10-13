# GameHub Ultra — PR/CAR autopilot: estado de seguridad

## Estado actual

**La autofusión Mergify está deshabilitada deliberadamente** (`pull_request_rules: []`). La configuración original del PR #195 tenía tres problemas confirmados por Codex: no autenticaba la procedencia de cada CAR y sesión Copilot contra el HEAD, podía fusionar mientras checks aplicables seguían pendientes y omitía la aprobación humana requerida para PR draft/workflows de Copilot. No se deben describir esos riesgos como resueltos por tener cuatro checks verdes.

Las pruebas `tests/automerge_policy.test.cjs` bloquean de manera fail-closed las vías conocidas de activación de autofusión: `pull_request_rules[].actions.merge`, `actions.queue`, `merge_protections_settings.auto_merge_conditions`, variantes heredadas `auto_merge`, `queue_rules[].autoqueue` y `extends` (que puede importar reglas de otro repositorio). Se comprueba la **presencia** de cada clave, incluso con YAML `null` o `false`, y se prueban configuraciones adversariales: las pruebas no dependen solo de que la configuración actual esté vacía. Esto es una barrera de regresión; **no** es una autorización para reactivar la autofusión.

Por decisión del propietario, **Codex no se utiliza en las nuevas revisiones de este proyecto hasta nueva orden**. Esta instrucción del repositorio no revoca por sí sola la GitHub App ni su configuración de revisiones en ChatGPT; esa desconexión debe efectuarse en los paneles de administración correspondientes. No desactivar ni suplantar otros revisores para simular auditorías independientes.

Mergify instalado **no significa** que exista una política segura de autofusión. Los PR se revisan y fusionan explícitamente por el propietario una vez verificado el HEAD, la CI completa, las revisiones y las protecciones de `main`. Ningún label persistente autoriza un merge.

## Condiciones antes de reactivar cualquier autofusión

1. Demostrar mediante un verificador independiente, con credenciales limitadas, que el PR fue generado para el issue CAR autorizado por la sesión Copilot delegada y que la autorización corresponde al **SHA actual**; identidad del bot, título, rama y etiquetas no son prueba suficiente.
2. Enumerar y esperar **todos** los checks y workflows aplicables a los archivos modificados, incluyendo los que aún están `queued`, `pending` o `in_progress`; bloquear si falta evidencia, hay conflictos, hilos abiertos o revisiones P1/P2 sin corregir.
3. Respetar el paso humano de GitHub para sacar de draft y aprobar ejecución de workflows de Copilot. No afirmar que esta etapa está completamente desatendida.
4. Excluir archivos privilegiados (`.github/`, `.mergify.yml`, `supabase/`, Gradle, manifest, configuración de seguridad, scripts de despliegue) y PR del propietario. Mantener las protecciones de `main` sin bypass.
5. Crear pruebas negativas para falsificación de procedencia, nuevos commits tras aprobación, checks pendientes/faltantes, archivos mixtos, PR draft y ejecución sin aprobación humana. Exigir CI verde y revisión independiente del HEAD antes de habilitar una nueva regla.

## Secuencia acordada

PR #195 (configuración segura sin autofusión) → PR #196 (webhook CAR, tras correcciones y pruebas) → despliegue controlado Supabase → verificación Copilot/Mergify → prueba integral Family Hunter PR #194, **con fusión manual para cambios privilegiados** → CAR restantes. Hasta probar el flujo de extremo a extremo, no declarar que los CAR avanzan autónomamente.

El webhook de Supabase no está desplegado ni tiene credenciales verificadas. Los plugins de ChatGPT tampoco ejecutan trabajos continuos por sí mismos.
