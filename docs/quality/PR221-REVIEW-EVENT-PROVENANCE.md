# PR #221 — causa raíz de la procedencia del YAML en `pull_request_review`

Fecha: 2026-10-25. **Estado:** DIAGNOSTICADO, NO RESUELTO EN ESTE CAMBIO; CAR-29 permanece bloqueante. Este documento es un recibo de investigación, no una autorización de fusión.

## Identidad del incidente

- Repositorio: `cardenaspiero255-lang/gamehub-ultra`.
- PR protegida: [#221](https://github.com/cardenaspiero255-lang/gamehub-ultra/pull/221); HEAD inmutable en esta auditoría: `3c328b6d2f40e1c9e10e361a843cd25ed2036b3f`.
- Workflow: `.github/workflows/ultra-sentinel-independent-review.yml` y su post-upload step `Reauthenticate scoped evidence after artifact upload`.
- Ejecución fallida: [40158765605](https://github.com/cardenaspiero255-lang/gamehub-ultra/actions/runs/40158765605), job `read-only-review` 121065662210.
- Resultados que NO reemplazan el fallo del evento de revisión: run 40158613453 (recibo Stage 2), 40158613448 (reviewer trusted) y 40158613489 (Core trusted), reportados SUCCESS en el recibo previo del #221.

## RED observado en el workflow real

El job falla en post-upload con `Post-upload attestation changed: missing trusted reviewer`. El log muestra el código ejecutado en ese evento:

```javascript
const scopePath=path.resolve('.github/scripts/ultra_sentinel_pr172_scope.cjs');
if(report.pr!==172||!fs.existsSync(scopePath)){
  core.setFailed('Post-upload attestation changed: missing trusted reviewer');return;
}
const {data:pr}=await github.rest.pulls.get({owner,repo,pull_number:172});
```

El código ejecutado ignora el #221 y vuelve a pedir datos del #172, aunque el checkout de los scripts de análisis es de main confiable. Por eso la lectura del script externo actualizado NO puede solucionar que el paso inline ya venga definido en YAML antiguo.

En `main` al auditar sí se observa código distinto: `![172,221].includes(report.pr)`, `pull_number:report.pr`, resolución del script desde `GITHUB_WORKSPACE`. Contrato estático del proyecto `tools/test_pr221_postupload_guard.cjs` comprueba esas invariantes; no reproduce el provenance del workflow cargado por evento.

## Causa raíz apoyada en documentación oficial

GitHub ejecuta `pull_request_review` desde la referencia `refs/pull/NUMBER/merge`; tanto `GITHUB_REF` como el SHA del evento proceden de esa referencia de integración. En cambio `pull_request_target` carga su definición de la rama por defecto de confianza. El evento no garantiza usar la última definición del YAML de `main` aunque un paso posterior haga `checkout` de `main`. El código YAML inline ya fue elegido antes del checkout.

- https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request_review
- https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request_target
- https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target

**Corrección de diagnóstico:** la explicación previa de los workflows, que decía que `pull_request_review` ejecuta YAML de main confiable, es falsa. Un `checkout ref: main` solo afecta archivos leídos durante los pasos posteriores, no el origen del archivo YAML del workflow ya elegido.

## Opciones de reparación segura a evaluar (NO ejecutadas)

### Opción A: actualizar la integración PR y revalidar TODO

Sin saltar la revisión, incorporar el `main` actual en el branch de #221 o regenerar su integración mediante el procedimiento protegido legítimo. **Puede cambiar SHA del HEAD y el merge commit**: si cambia HEAD, invalidar toda autorización anterior, blobs y recibos; repetir RED/GREEN, CAR-29, revisión estructural, Stage 2 y ambos revisores sobre las nuevas identidades. No hacerlo de manera encubierta: el propietario autorizó solo el SHA original.

### Opción B: rediseñar el disparador de revisión con definición de trusted main

Diseñar un nuevo revisor protegido que se active por un evento cuya definición proceda obligatoriamente de la rama por defecto (por ejemplo un `issue_comment` autenticado que verifique PR/autor/SHA, o `workflow_dispatch` sujeto a controles). **No sustituir ni eliminar silenciosamente checks obligatorios**: antes de adoptar esta vía, demostrar equivalencia con el contrato anterior y que no pierde detección de revisiones, no ejecuta código de PR, conserva permisos mínimos y respeta branch protection. El permiso autenticado del propietario NO reemplaza una aprobación humana independiente exigida por reglas de rama.

**No es solución:** reintentar a ciegas el mismo run de `pull_request_review`, editar solo el script en checkout de main, aprobar el PR con un comentario del bot, eliminar el check fallido o forzar merge.

## Reglas de pruebas RED→GREEN para la reparación

1. RED: fixture que reproduzca la definición antigua del evento en un merge ref, aunque checkout se haga de main nuevo. Debe probar explícitamente que #221 se rechaza y que los `pull_number` antiguos sobreviven.
2. GREEN: demostrar con un evento real de GitHub que la definición de YAML ejecutada contiene la corrección #221; registrar `GITHUB_EVENT_NAME`, `GITHUB_REF`, SHA del workflow, SHA de HEAD y SHA de checkout sin imprimir secretos.
3. Adversariales: PR #172 correcto, #221 correcto, otro número rechazado, PR de fork rechazado, SHA movido, estado Stage 2 incompleto, artifacts históricos, reintento fallido y archivo confiable ausente; todos deben fallar cerrados cuando corresponda.
4. No aceptar pruebas estáticas de string como único criterio. Un `PASS` exige al menos un run auténtico del evento que antes fallaba, asociado al nuevo contrato, además de CI y permisos.
5. Validar la protección GitHub actual y la autorización humana efectiva antes de fusión de YAML protegido. Mantener #221 en DRAFT hasta entonces.

## Cómo conecta con la investigación YouTube

Los cursos documentados en `docs/research/EVO-V3-YOUTUBE-KNOWLEDGE-BASE.md` aportan nociones prácticas de Git (MoureDev), workflows y testing (DevOps Directive, Android testing). **Para semántica de eventos y seguridad prevalece la documentación oficial GitHub**, no la transcripción de un video.

**Resultado actual: ROOT_CAUSE_IDENTIFIED / FIX_NOT_DEPLOYED / MERGE_BLOCKED**.
