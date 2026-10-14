# Ultra Sentinel Triple SSS — interoperabilidad y automatización (bloque 3)

## No duplicar lo existente
- PR #195: Mergify sin autofusión, agente Copilot CAR y política `main` fail-closed.
- PR #196: webhook CAR firmado y ledger transaccional Supabase; el **despliegue real** y credenciales deben confirmarse por separado.
- PR #194: adaptador Family Hunter de solo lectura (pendiente de revisión/fusión).
- PR #197: memoria histórica, con verificador externo obligatorio.
- PR #198: familias/variantes multiarchivo, **hipótesis** no conclusiones certificadas.

## Plugins y revisores
`ultra_sentinel_review_integrations.cjs` solo consume **evidencia obtenida por API de GitHub**: revisiones y checks. Codex, CodeRabbit, Qodo y Sentry son señales *advisory*: la presencia de un comentario, check verde o plugin instalado no equivale a aprobación independiente o a detección completa de errores. Se descartan evidencias anteriores al HEAD; un `COMMENTED` no significa `APPROVED`. Los estados pendientes/fallidos se muestran explícitamente.

Los plugins de ChatGPT (por ejemplo, GitHub, Prompt Engineer, GodPrompt, Qodo u otros que el usuario conecte) no pueden llamarse desde GitHub Actions por el simple hecho de estar instalados en ChatGPT. No se inventarán conectores, revisiones ni tokens. La política vigente en `docs/GAMEHUB_AUTOPILOT.md` mantiene **Codex desactivado en nuevas revisiones hasta reactivación expresa del propietario**; se pueden mostrar revisiones históricas, sin solicitarlas automáticamente.

## Automatización sin bypass
El observer corre en `pull_request_target` desde **código confiable de main**, solo con permisos de lectura y sin checkout/ejecución del código del PR. Cruza el SHA actual con reviews/checks, publica un resumen de familias y proveedores y falla ante inventario incompleto o movimiento del HEAD. No comenta, modifica, aprueba, hace push, merge ni despliega. No cambia `.mergify.yml`, branch protection ni aprobación humana. Un resumen verde no reemplaza los otros gates.

Los cambios de `.github/`, Supabase, CI o permisos son privilegiados y exigen auditoría independiente. No declarar un CAR terminado si su PR tiene errores confirmados, pruebas incompletas, hilos abiertos o faltan revisiones obligatorias.

## Criterio para afirmar que supera a Codex
No se puede certificar superioridad a partir de los resultados de la CI interna. Exigir evaluación ciega sobre casos **nunca vistos** con errores reales y variantes semánticas. Publicar muestras, taxonomía, ground truth revisado de forma independiente y los resultados de **ambos** revisores con el mismo presupuesto de tiempo. Medir recall P1/P2, precisión, false negatives, false positives, tasa de reparaciones verificadas, regresiones introducidas, costes y tiempo. Repetir por dominio (Android, voz, seguridad, CI, Supabase). Solo declarar ventaja en los dominios y métricas donde exista evidencia comparable.

## Fusión
Stack: #197 → #198 → este PR → Bloque 4 (autorización de reparaciones). Ningún PR es fusionable por ser parte del stack. El propietario debe comprobar SHA, CI completa, revisión independiente y permisos vigentes.
