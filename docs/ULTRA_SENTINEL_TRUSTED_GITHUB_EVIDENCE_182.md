# Ultra Sentinel PR #182 - Recopilacion confiable, aislada y de solo lectura

## Activacion del colector del PR #181

Crea un workflow INDEPENDIENTE llamado Ultra Sentinel Trusted GitHub Evidence -
Shadow, disparado solo mediante pull_request_target para PR hacia main.
El job descarga exclusivamente el codigo de la rama predeterminada, nunca
codigo del PR, con persist-credentials desactivado.

Los permisos de contents, pull-requests y actions son todos read.
El token temporal se entrega al colector que se ejecuta desde main; no se
ejecutan scripts ni se instalan dependencias controladas por el PR analizado.
Los datos del PR se leen como contenido no confiable, sujetos a SHA y pruebas
de procedencia del colector del PR #181.

## Separacion de responsabilidades

- El workflow obligatorio Self-Review queda SIN MODIFICACIONES.
- Este nuevo workflow es diagnostico, NO una aprobacion automatica.
- Un fallo del colector se informa como fallo del workflow nuevo, SIN alterar
  los resultados del Self-Review anterior ni deshabilitarlo.
- No usar continue-on-error para ocultar fallos en pasos protegidos.
- El informe JSON se sanea, se limita y se guarda siete dias como artefacto.
- Mantener este workflow fuera de los requisitos de fusion hasta completar
  evaluacion independiente y autorizacion expresa.
- El resultado global del evaluador es INCOMPLETE y autoMergeAllowed=false
  hasta contar con escaneos completos de base y HEAD.

## Contratos y aceptacion

12 pruebas adversariales nuevas verifican permisos de solo lectura,
identidad del evento, checkout de main, dependencias de main, ausencia de
ejecucion de codigo del PR, separacion del Self-Review original, tokens
confinados a un paso protegido, acciones de GitHub fijadas por SHA y
analisis completo del YAML por el propio AST de Ultra Sentinel.

El CI del propio PR #182 SOLO verifica contratos. La primera ejecucion
real contra otro PR se producira DESPUES de la fusion a main, cuando
GitHub aplique la nueva definicion del workflow. No se declarara que
la integracion funciona en produccion antes de inspeccionar ese resultado.

Proximas etapas: escaneos autenticados base/HEAD, Family Hunter,
modos 1/2/3, memoria de regresiones y Quality Gate avanzado.

Prohibidos en este PR: auto-merge, auto-commit y permisos de escritura.
