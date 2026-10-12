# Ultra Sentinel PR #187 — Protocolo de calibración firmada (modo sombra)

## Objetivo
Aceptar un corpus **externamente aportado** y reportes ejecutados por un runner distinto, con integridad Ed25519, SHA exacto y métricas reproducibles. **Ningún dato real etiquetado se incluye en este PR**, y las pruebas unitarias crean llaves efímeras con datos sintéticos. Pasar esas pruebas no certifica precisión, independencia del evaluador ni SSS.

## Contrato del bundle
```json
{
  "schema": "ultra-sentinel-external-calibration/v1",
  "headSha": "<SHA de 40 dígitos del objeto evaluado>",
  "corpus": [{"id":"case-0001","sha":"<mismo SHA>","humanReviewed":true,"unseenAtTraining":true,"expected":[{"rule":"RULE_NAME","path":"src/Example.kt"}]}],
  "reports": [{"provider":"ultra-sentinel","caseId":"case-0001","sha":"<mismo SHA>","findings":[],"durationMs":123}],
  "signatures": {
    "labeler": {"keyId":"<identificador del equipo revisor>","sha256":"<digest del JSON canónico del corpus>","signature":"<Ed25519 base64>"},
    "runner": {"keyId":"<identificador del ejecutor>","sha256":"<digest del JSON canónico de reports>","signature":"<Ed25519 base64>"}
  }
}
```

- El sello del etiquetador firma UTF-8 de `ultra-sentinel-external-calibration/v1\nlabeler\n<headSha>\n<sha256-del-corpus>\n<bytes>`. El sello del runner firma `ultra-sentinel-external-calibration/v1\nrunner\n<headSha>\n<sha256-del-corpus>\n<sha256-del-reporte>\n<bytes>`; así no se pueden combinar dos conjuntos incompatibles pero firmados por separado. Los hashes usan SHA-256 sobre `stableJson(payload)` (claves del objeto ordenadas recursivamente). `bytes` corresponde a la longitud UTF-8 de ese JSON.
- `evaluateSignedCalibration(bundle, trustedKeys, {expectedHeadSha})` exige que el SHA del bundle coincida con el SHA **fijado por el llamador confiable**. Sin esa identidad inmutable o con SHA viejo se devuelve `UNVERIFIED_EVIDENCE`. Requiere `trustedKeys.labeler` y `trustedKeys.runner` con `keyId` y `publicKeySpki` DER SPKI en base64. Las claves de confianza **deben suministrarse desde un ejecutor protegido y fuera del PR**; nunca desde archivos, comentarios o entradas del PR.
- Roles, firmas, digests, SHA, casos duplicados/incompletos, rutas y proveedores se validan estrictamente. Dos roles no pueden reutilizar identidad ni clave criptográfica: se compara el SPKI Ed25519 canónico, se valida que cada punto sea canónico, distinto de la identidad y pertenezca al subgrupo de orden primo, y se rechazan alias DER, torsión y bytes sobrantes. La serialización JSON controla el presupuesto de bytes **antes** de materializar strings escapados, limita la cantidad de propiedades antes de ordenarlas y rechaza accesores, arrays incompletos y entradas sobredimensionadas.
- No registrar el corpus bruto ni tokens; el resultado de la función contiene sólo estados y métricas acotadas.
- No existe ejecución automática de esos bundles en un job con permisos, por diseño. La revisión independiente del origen real del corpus aún está pendiente.

## Pasos para completar calibración real
1. Un revisor **humano independiente** crea y documenta al menos 100 positivos y 100 negativos, representando 5 familias, desde defectos verificados y casos limpios.
2. Un runner aislado, distinto del etiquetador, ejecuta la versión inmutable en los mismos casos y registra salida y tiempos observados.
3. Cada parte firma exclusivamente su propio artefacto; un administrador valida identidades y fija claves públicas *fuera de la rama candidata*.
4. Un sistema confiable invoca `evaluateSignedCalibration` con esos artefactos. Después se inspeccionan manualmente FN/FP, cobertura por familia y sesgos, sin usar los umbrales sintéticos como autoridad de producción.

## Control de autoridad
Los estados `UNVERIFIED_EVIDENCE`, `INVALID_CASES` y `SIGNED_SHADOW_ONLY` nunca conceden `autoMergeAllowed`, `autoCommitAllowed` ni `productionBlockingEnabled`. Las dos firmas sólo establecen integridad frente a las claves del llamador: **no prueban por sí solas calidad empírica o independencia organizacional**. Todos los resultados quedan en modo sombra hasta una auditoría real.

## Regresiones tras la revisión P1/P2
- Cuatro contratos adicionales verifican rechazo de firmas forjadas con puntos identidad y alias del bit de signo, puntos de orden pequeño o no canónicos, limitación de claves antes de ordenar, y serialización determinista justo al límite de 100 propiedades.
- Los 28 contratos del adaptador contribuyen al baseline B1 de 11.731 sin disminuir los 500.500 casos de la matriz Core.
- Estas verificaciones técnicas no sustituyen el etiquetado independiente de datos reales ni cambian la autoridad del modo sombra.

## Refuerzo adicional P2 — arrays con propiedades maliciosas
- El adaptador ya no usa `Object.keys(v)` sin cota para validar arrays: reutiliza `cappedOwnKeys(v,v.length)` antes de crear una lista completa de claves. Los arrays densos de hasta 2.500 elementos siguen aceptándose y los arrays dispersos se rechazan.
- Tres contratos nuevos reproducen el fallo con propiedades adicionales, comprueban el rechazo de arrays contaminados y mantienen arrays densos válidos. Baseline B1 actualizado de 11.732 a **11.735** sin reducir la matriz Core de 500.500 casos.

## Frontera de entrada acotada para evidencia externa (refuerzo P2)
- `evaluateSignedCalibration(rawBundleJson, trustedKeys, {expectedHeadSha})` recibe **JSON textual de tamaño UTF-8 máximo 1 MiB**. Rechaza directamente objetos JavaScript, buffers, entradas sobredimensionadas y JSON inválido antes de analizar propiedades o verificar firmas. Los sistemas de confianza deben transmitir los bytes recibidos y **no analizar primero el JSON para luego pasarlo como objeto**.
- `stableJson(rawJson)` acepta únicamente JSON textual preacotado para canonicalización; la serialización interna de estructuras que proceden de ese límite es independiente y no exporta acceso de objetos arbitrarios. Esto impide que objetos programáticos de tamaño desconocido eludan el presupuesto a través de `for...in` o `Object.keys`.
- El contrato de firmas, los dos roles, la separación de claves, el SHA fijado y el modo sombra no se modifican. Prueba RED de objeto externo y mensaje serializado confirma la necesidad de esta frontera; baseline B1: 11.736.
