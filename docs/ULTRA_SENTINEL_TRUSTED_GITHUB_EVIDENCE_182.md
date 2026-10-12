# Ultra Sentinel PR #182 - Recoleccion confiable en modo sombra

## Alcance

Este PR activa recoleccion de evidencia de GitHub exclusivamente mediante el job
de confianza del workflow Self-Review (evento pull_request_target).
El checkout se hace desde la rama predeterminada, no desde el PR.
Los permisos actions: read y pull-requests: read solo se conceden al job
protegido. El job candidato hereda exclusivamente contents: read.

El colector creado en PR #181 recupera SHA inmutable, datos del PR, merge-base,
archivos y comprobaciones CI, y utiliza el analizador confiable ya existente.
No instala dependencias del PR, no ejecuta archivos del candidato en el job
protegido, no modifica el repositorio y no habilita autoridad de fusion.

## Ejecucion

- El analizador protegido de main sigue siendo el control principal.
- Un paso posterior recopila y guarda un informe JSON sanitizado como artefacto
  con retencion limitada a siete dias.
- El paso nuevo es diagnostico (continue-on-error: true). Sus fallas no vuelven
  verde un gate anterior ni anulan errores del analizador protegido.
- Los datos de GitHub se validan por identidad, workflow ID y SHA al inicio
  y final de la recopilacion. La perdida de evidencia no se trata como PASS.
- Hasta implementar escaneos completos autenticados base/HEAD, el estado global
  sigue siendo INCOMPLETE y autoMergeAllowed permanece en false.
- La activacion real ocurre una vez fusionado el codigo del workflow a main.
  El CI del PR candidato solo confirma su estructura, no una ejecucion real.

## Pruebas y limites

Once pruebas nuevas exigen permisos de lectura, checkout de main, separacion
de eventos, parser confiable, ausencia de escrituras, aislamiento de credenciales
y publicacion limitada. El resto de controles Core, Android y Coverage no cambia.

Faltan los escaneos completos base/HEAD, Family Hunter y sus modos SSS,
y la decision independiente del Quality Gate. Una prueba de humo sobre un PR
posterior confirmara que el colector corre realmente en GitHub.

No hay auto-merge, auto-commit ni aprobacion automatica.
