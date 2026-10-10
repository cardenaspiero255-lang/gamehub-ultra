# Ultra Sentinel — laboratorio adversarial reproducible

Este laboratorio mide las capacidades actuales del revisor frente a ejemplos **sintéticos e inertes**. Nunca ejecuta los fragmentos sospechosos ni descarga código. Los dominios `example.invalid` son ficticios.

## Componentes

- `.github/scripts/ultra_sentinel_adversarial_gauntlet.cjs`: generador determinista, dos analizadores, detección, métricas y resumen.
- `.github/scripts/ultra_sentinel_gauntlet_extreme.cjs`: evasiones difíciles y variantes benignas.
- `.github/scripts/ultra_sentinel_adversarial_gauntlet.test.cjs`: integridad de los datos de pruebas y del informe.
- `.github/workflows/ultra-sentinel-adversarial-gauntlet.yml`: workflow sin secretos, de solo lectura, que ejecuta una evaluación **estricta** en GitHub Actions.

## Ejecutar localmente

```bash
npm ci --ignore-scripts --no-audit --no-fund
node --test .github/scripts/ultra_sentinel_adversarial_gauntlet.test.cjs
node .github/scripts/ultra_sentinel_adversarial_gauntlet.cjs --compact
node .github/scripts/ultra_sentinel_adversarial_gauntlet.cjs --strict --compact
```

`--strict` devuelve un código distinto de cero si hay escapes silenciosos, falsos positivos, patrones no reconocidos que se declaran limpios o fallos del motor. Por eso es **normal y deseable que este workflow quede rojo si encuentra deficiencias genuinas**; no debe marcarse `continue-on-error` ni omitirse para obtener verde.

## Qué prueba

Casos y mutaciones de sintaxis/orden: Unicode en claves YAML, BOM, CRLF, comentarios, comillas y nombres de jobs; ejecución remota con tuberías, sustitución de proceso, stdin, redirecciones y `eval`; datos de eventos y cadenas de taint; expresiones aritméticas; checkout privilegiado y refs mutables; acciones sin SHA; permisos de escritura; workflows desactivados, runners y shells extraños; YAML malformado o demasiado grande; código Android; manipulación de parches y archivos críticos.

La categoría **extrema** añade ejecución en varias etapas (descargar a disco y ejecutar después), intérpretes nativos Python/Node/Ruby/Perl, PowerShell, codificaciones, paquetes de versión móvil y contenedores mutables. Cada amenaza se reescribe mediante transformaciones deterministas para comprobar que un cambio de presentación no altera la seguridad.

## Cómo leer los resultados

| Categoría | Significado |
| --- | --- |
| Detección explícita | El analizador identifica una regla o bloquea una manipulación de seguridad concreta |
| Incomplete / inconcluso | No logra demostrar seguridad y se niega a certificar el código; **no se cuenta como detección** |
| Escape silencioso | Un caso peligroso recibe `NO_RISK_PATTERN` o no detecta el problema: requiere reparación |
| Falso positivo | Un caso benigno se marca sospechoso o no puede certificarse |
| Unknown clean | Un escenario deliberadamente no verificable se certifica como seguro |
| Crash | El analizador produce una excepción no controlada |

La tasa de detección se calcula únicamente con los ataques etiquetados: `detectados / escenarios maliciosos`. La tasa *fail-closed* incluye también los inconclusos, pero ambas métricas deben mostrarse **separadas**. Los falsos positivos se calculan sobre los ejemplos benignos.

## Límites y resolución

La batería es un conjunto de ejemplos etiquetados, **no** una evaluación de vulnerabilidades reales en todas las configuraciones, ni una prueba de inmunidad. Muchos casos son variantes de una misma raíz; no son muestras independientes para calcular precisión sobre el mundo real. No se debe reemplazar revisión experta, SAST, análisis dinámico, CI Android/Coverage ni aprobación independiente con este marcador.

Ante un escape: conservar su identificador, crear un test RED reproducible, corregir la familia en el motor, demostrar GREEN y repetir las regresiones. Los resultados del workflow deben seguir en rojo hasta que no queden escapes o falsos positivos incluidos en el contrato estricto. No borrar pruebas difíciles ni reinterpretarlas como seguras para maquillar resultados.
