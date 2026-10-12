# Ultra Sentinel PR #183 - Escaneo autenticado base/HEAD, modo sombra

## Que se implementa

- Recolecta **ambos arboles Git** de los commits base y HEAD via GitHub API.
- Verifica commit SHA -> tree SHA -> blob SHA, incluyendo el hash Git SHA-1
  calculado sobre los bytes originales, tipo, tamaño, UTF-8 y ruta.
- Identifica fuentes potencialmente analizables antes de validar su nombre:
  rutas con caracteres no admitidos o traversal se rechazan, no se omiten.
- Rechaza ejecutores dinamicos o configuraciones de runners no modeladas, incluso
  cuando el workflow sea no privilegiado.
- Rechaza arboles truncados, entradas duplicadas, symlinks, submodulos,
  rutas inseguras, blobs manipulados y resultados incompletos.
- Descarga el mismo objeto Git una sola vez si base y HEAD comparten el blob.
- Analiza de forma determinista los **archivos incluidos en este alcance**:
  (1) workflows .github/workflows/*.yml/.yaml con el parser existente,
  (2) scripts .github/scripts/ultra_sentinel_*.cjs de produccion con AST
  Acorn y seguimiento de capacidades existentes,
  (3) app/src/main/**/*.kt y *.java con reglas del Core sobre todo el archivo.
  Los archivos se tratan como DATOS. Nunca se importan ni ejecutan desde el PR.
- Alimenta el evaluador shadow PR #180 con evidencias completas SOLO cuando
  ambos escaneos del alcance terminan, y mantiene clasificaciones NEW,
  EXISTING, REACTIVATED, MOVED y EXACERBATED segun evidencia.
- Registra limites, conteos, estados, clasificacion y reglas sin copiar
  fuentes, tokens, parches ni razones no confiables al informe publicado.
- No modifica el Self-Review obligatorio ni la politica de fusion.

## Limites explicitos de confianza y cobertura

Este motor tiene cobertura completa **dentro del conjunto de formatos y rutas
soportadas que pueda escanear sin exceder los presupuestos**. No afirma que
comprende semanticamente todos los lenguajes, archivos, dependencias y efectos
del repositorio. No cubre inicialmente XML, TS/TSX, Python, Gradle y otros
tipos de produccion. Ese alcance requiere implementacion y pruebas propias.

Los limites son 20.000 entradas por arbol, 400 archivos analizables por
snapshot, 150 KB por archivo, 16 MB por snapshot y 5.000 hallazgos. Si se
superan limites, falla una llamada o el analizador no puede verificar una
estructura, el resultado se considera INCOMPLETE; **nunca se inventa PASS**.

Los errores HIGH/CRITICAL/BLOCKER encontrados y los fallos CI confirmados
mantienen reviewRequired=true incluso cuando un snapshot es INCOMPLETE.

Un resultado con fuente y hashes verificados tampoco constituye una prueba de
ausencia de todos los defectos posibles: las reglas actuales son parciales.
El campo hasAuthenticatedFullRepositoryScan permanece FALSE.

La propiedad "scopedEvaluator" es un diagnostico, no una decision vinculante.
Aunque la comparacion de ambos snapshots resulte PASS en el alcance,
el estado general sigue INCOMPLETE mientras el Quality Gate de produccion no
este explicitamente autorizado. No hay auto-merge ni commits autonomos.

## Seguridad operacional y CI

- Sigue ejecutandose desde el workflow read-only protegido del PR #182.
- Protege contra TOCTOU: vuelve a consultar PR y SHA al final de las lecturas.
- Si no obtiene evidencias validas, el job diagnostico falla en cerrado.
- La ejecucion real del nuevo codigo solo se probara despues de fusionarlo
  a main, pues pull_request_target utiliza codigo confiable de main.
- Las 32 pruebas del escaner y 8 pruebas de integracion del colector se
  añaden al Core b1: 11.637 -> 11.677.
- Las pruebas de Android Build, Coverage, Self-Review, Mutation Lab, Core,
  Adversarial Gauntlet y demas revisiones deben aprobar en HEAD antes de merge.

## Fases posteriores

Ampliar formatos y analisis entre archivos, buscar causas raiz verificables
y conectar Family Hunter SSS y modos 1/2/3, siempre evitando duplicados.
No habilitar Quality Gate requerido sin validacion de precision independiente.
