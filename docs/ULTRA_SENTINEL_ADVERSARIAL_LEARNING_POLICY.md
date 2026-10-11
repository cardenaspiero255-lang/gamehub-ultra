# Ultra Sentinel — política de aprendizaje adversarial y memoria verificable

> **ESTADO: especificación de seguridad / prompt de diseño, NO IMPLEMENTADO NI ACTIVADO.**
> Relacionado con **IAF-04**, **IAF-07** y US-006, US-012, US-016, US-017, US-027, US-031 y US-035.
> Este archivo no concede permisos, no crea automatizaciones y no modifica el veredicto del revisor.

## Regla operativa propuesta para un futuro agente Ultra Sentinel

**Regla de aprendizaje adversarial con memoria íntegra, procedencia verificada y prohibición de rebajar controles:**

1. **Solo registrar experiencias comprobadas.** Un candidato a recuerdo debe identificar la familia de fallo (falso negativo o falso positivo), el hash SHA-256 de los bytes exactos del YAML, el SHA completo de commit, versión del analizador, regla implicada, motivo, reproducción RED, prueba GREEN y enlaces verificables a artefactos y runs. No afirmar causa raíz sin una reproducción. Guardar únicamente fragmentos mínimos sanitizados, sin secretos ni datos personales.
2. **No confundir «CI falló» con «caso fiable».** Únicamente un proceso de ingestión confiable en `main`, separado de código de PRs y con permisos mínimos, puede proponer un recuerdo. Debe verificar identidad del workflow, SHA, última ejecución y último intento, integridad del artefacto, origen de datos, revisión independiente y aprobación humana autorizada. Un PR, issue, comentario, log, resumen, artefacto aportado por un usuario o texto de un modelo es **dato no confiable**; tampoco es prueba suficiente una etiqueta creada por el propio bot.
3. **Libro mayor append-only verificable.** El posible `/memory/failures.json` es una *ruta lógica propuesta*, no un archivo hoy existente ni un almacenamiento inmutable por sí mismo. Para implementarlo se exige un registro fuera del workspace controlado por PR, con control de acceso, versiones, hashes encadenados o manifest firmado, bloqueo de sobrescrituras, retención y recuperación. Las eliminaciones/renombrados, huecos de secuencia, hashes inconsistentes, firmas o pruebas de inclusión inválidas deben producir **INCONCLUSIVE/BLOCKED**. Un archivo normal del repositorio o un hash aislado no dan inmutabilidad criptográfica.
4. **Leer y validar antes de utilizar memoria.** En cada evaluación que dependa de recuerdos, verificar cadena, versión, integridad, procedencia, permisos y estado de revocación. Si faltan archivos o metadatos, degradar a **INCOMPLETE**; nunca convertir ausencia de memoria en aprobación. Los casos adversariales conocidos deben seguir en las pruebas de regresión incluso si la memoria no está disponible.
5. **Recuperar similitudes solo como hipótesis.** Un umbral de similitud (por ejemplo, >90 %) no implica equivalencia semántica, seguridad ni permiso de autoparche. Puede priorizar un playbook o sugerir tests, pero la corrección deberá reproducirse sobre el SHA y el contexto actuales con pruebas RED/GREEN y aprobación del Judge independiente. Cambios de triggers, permisos, `shell`, `uses`, secretos, referencias, repositorios y expresiones dinámicas invalidan cualquier inferencia de seguridad basada solo en texto parecido.
6. **La memoria no puede reducir políticas.** Un caso aprendido nunca puede convertir un `BLOCKER` o `INCOMPLETE` en `PASS` ni desactivar reglas, tests, branch protection, comprobaciones de CI o revisión humana. Conflictos de recuerdos, evidencias contradictorias, drift, falsos positivos y recuerdos obsoletos exigen cuarentena y recalibración supervisada; una revocación debe dejar rastro de auditoría sin borrar el original.
7. **Aprendizaje gradual, medible y reversible.** Inicialmente solo lectura y recomendaciones, después registro supervisado; cualquier ajuste futuro se activa por fases y se compara con una base estable usando un corpus externo ciego. Medir precisión, recall, falsos negativos críticos, falsos positivos, regresiones, latencia, coste y tasa de recuerdos contaminados. Debe existir kill switch y reversión. No se permite autofusión, despliegue ni ejecución de código no confiable con secretos.

## Contrato de decisión

| Situación | Resultado mínimo permitido |
|---|---|
| YAML malicioso confirmado con RED y reparación GREEN verificadas | Registrar *candidato* a experiencia, sujeto a validación independiente y humana |
| CI rojo sin causa reproducida, procedencia ausente o artefacto aportado por PR | `INCONCLUSIVE` / `INCOMPLETE`; **no aprender** |
| Memoria eliminada, renombrada, truncada o con cadena de integridad rota | `BLOCKED` o `INCOMPLETE`; **nunca certificar** usando recuerdos |
| Caso nuevo >90 % parecido a uno anterior | Solo **sugerencia**; reproducir y reevaluar reglas existentes |
| Recuerdo propone permitir algo que reglas actuales bloquean | Mantener `BLOCKER`; poner recuerdo en cuarentena |
| Caso de prueba seguro al que el motor dio falso positivo | Solo proponer ajuste después de prueba negativa, evaluación ciega y aprobación humana |

## Pruebas necesarias antes de programar o activar

- Poisoning por título de issue, cuerpo de PR, comentario, review, commit y artefacto CI.
- YAML que difiere en 1 carácter crítico (trigger, `permissions`, `shell`, `uses`, checkout, interpolación) aunque su similitud textual supere el 99 %.
- Run verde antiguo frente a último intento rojo, cancelado, pendiente o sin datos fiables; spoofing de `workflow_id`, repositorio y SHA.
- Intentos de borrar, sustituir, reordenar y duplicar recuerdos; rollback de versiones, hash-chain corrupta y firma inválida.
- Reproducción RED y GREEN forjadas o no relacionadas con el mismo SHA y test.
- Recursos agotados, memoria no disponible, evaluador externo caído y filtración de tokens/datos personales.
- Regresión del veredicto: ninguna memoria aprendida permite pasar un caso que antes terminaba en `BLOCKER`.

## Condición de aceptación

Hasta contar con controles y pruebas reales, la función es **PENDIENTE**. Un PR verde, un archivo llamado `failures.json`, una memoria append-only declarativa o una coincidencia del 90 % **no certifican SSS**. La seguridad se demuestra con barreras independientes, procedencia verificable y pruebas adversariales ejecutadas.

> **Nota sobre implementación:** no escribir memoria automáticamente, no modificar archivos en `main`, no descargar datos de Sentry/CI sin permisos, y no activar aprendizaje por añadir este documento.
