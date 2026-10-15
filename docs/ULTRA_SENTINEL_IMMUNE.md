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

## Regla estricta RF-001 — eliminar la familia de cada error confirmado

> **«Si aparece un error, eliminar su causa raíz y todas sus variantes reproducibles dentro del alcance investigado; no basta con arreglar el caso individual».**

Esta regla se aplica a cada bug, vulnerabilidad, fallo de compilación, falso negativo,
falso positivo o regresión de Ultra Sentinel y GameHub Ultra. Se suma a la regla
CAR-29: nunca declarar un CAR, PR o reparación terminado si subsisten fallos confirmados.

1. **Reproducir primero (RED):** identificar el comportamiento real, la causa
   raíz y qué otros módulos/entradas comparten la misma causa. No cerrar por
   ausencia de alerta sin verificar el reproducer.
2. **Investigar la familia completa conocida:** enumerar variantes sintácticas,
   semánticas, límites, cadenas de acciones, estados y plataformas aplicables.
   Para parsers, incluir quoting, escapes, flags, orden, redirecciones, alias y
   rutas no soportadas. No sumar pruebas duplicadas para aparentar cobertura.
3. **Corregir el origen (GREEN):** reparar la abstracción común, no únicamente el
   ejemplo detectado; añadir pruebas adversariales y controles benignos para
   prevenir falsos positivos. Los casos fuera de la gramática segura deben
   **fallar cerrado / marcarse INCOMPLETE**, nunca certificarse como limpios.
4. **Demostrar la regresión de la familia:** conservar como mínimo el caso
   original (RED→GREEN), una variante alternativa, un caso límite y un control
   negativo. Ampliar según las rutas realmente afectadas; cubrir familias de
   transformación similares y ejecutar Core, mutación, Android Build y Coverage
   según corresponda al SHA final. Revisar nuevas variantes de Codex y otros
   revisores como hipótesis, reproducir antes de corregir.
5. **Puerta de salida innegociable:** si existe alguna variante confirmada sin
   corregir, una prueba roja, un falso positivo reproducible o una zona crítica
   que se clasifica erróneamente como limpia, bloquear el cierre y la fusión.
   Nunca silenciar el detector, reducir la cobertura o ignorar pruebas para
   conseguir verde. Tras cada cambio se repite la evaluación del SHA vigente.

**Control técnico:** `evaluateRepairGate` requiere `familyEvidence` con
`id`, `sha`, `rootCause`, `scope`, `unresolvedConfirmed: 0`,
`knownGaps: []`, `unknownSyntax: 'fail_closed'` y pruebas diferenciadas con
categorías `original`, `alternate`, `boundary` y `benign_control`.
La prueba original debe demostrar RED antes de GREEN. El evaluador rechaza
familias incompletas, evidencia de otro commit y pruebas con identificadores
repetidos. **La metadata suministrada no es evidencia externa por sí misma:**
los resultados reales de CI, el análisis de los casos y la revisión humana
independiente deben verificarse por separado.

**Límite de la regla:** no es científicamente posible garantizar que se conocen
*todas* las variantes concebibles de un error. La obligación estricta es
agotar las variantes conocidas y las clases sistemáticamente derivables en el
alcance declarado, exponer lagunas e incertidumbre y bloquear cualquier caso
confirmado pendiente. Nunca afirmar inmunidad absoluta ni «0 errores futuros».

## Para declarar un CAR listo
Comprobar en el SHA vigente: Sentinel Core Tests, Mutation Lab, Android Build, Unit Test
Coverage, arquitectura, revisores, hallazgos confirmados y **RF-001 (familias y variantes)**.
Un job pendiente o una variante confirmada sin resolver no vale verde.
Sin benchmark externo no se puede garantizar 0 % de errores ni una categoría SSS.
