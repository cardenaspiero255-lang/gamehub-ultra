# Ultra Sentinel Immune — evidencias y límites

El PR #167 contiene mejoras experimentales. No se auto-fusionan cambios a main.

## Mutation Lab
Altera 14 expresiones seleccionadas de copias temporales del revisor JavaScript.
Cada mutante se clasifica como detectado, superviviente o inconcluso; no modifica el código original.
La detección de estos 14 mutantes no indica 100 % de cobertura en Kotlin, Android ni sus APK.

## Grafo causal de antecedentes
Cuando hay un hallazgo HIGH o BLOCKER, consulta como máximo dos rutas y seis commits por ruta.
Comparte coincidencias de archivo y descripción sin presentar falsos porcentajes de culpa.
Cada relación lleva la etiqueta NOT_ESTABLISHED. Se necesita reproducción y bisect para confirmar origen.

## Manifiesto reproducible
Para fallos de Android Build o Unit Test Coverage se puede guardar SHA, jobs y pasos fallidos,
más una receta de ejecución. No captura emuladores pausados, breakpoints, estado de memoria ni secretos.

## Feedback explícito (sin fine-tuning automático)
Un colaborador de GitHub OWNER/MEMBER/COLLABORATOR puede dejar el comentario:

<!-- ULTRA_SENTINEL_FEEDBACK_V1
{"pr":167,"sha":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","rule":"SPEECH_REENTRANT_RETRY","decision":"reject","reason":"El cambio duplica el RetryGate existente; reutilizar su instancia y ciclo de vida."}
ULTRA_SENTINEL_FEEDBACK_V1_END -->

Sustituye SHA por el SHA real y comenta en el PR correspondiente. El motor solo considera
los comentarios de colaboradores aprobados, para ese mismo commit; no borra el hallazgo
ni aprende un modelo neuronal. La aceptación tampoco habilita auto-merge.

## Presupuesto y seguridad
- Revisión rápida sin red ni Gradle.
- Historia: máximo 2 rutas × 6 commits; solo para alertas altas.
- Mutation Lab aislado: timeout 12 minutos, pruebas focalizadas.
- Bisect manual, con commit previamente verificado como bueno.
- Nunca ejecutar código de PR con secretos en pull_request_target.

## Para declarar un CAR listo
Comprobar en el SHA vigente: Sentinel Core Tests, Mutation Lab, Android Build, Unit Test
Coverage, arquitectura, revisores y hallazgos confirmados. Un job pendiente no vale verde.
Sin benchmark externo no se puede garantizar 0 % de errores ni una categoría SSS.
