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
- `evaluateSignedCalibration(bundle, trustedKeys)` requiere `trustedKeys.labeler` y `trustedKeys.runner` con `keyId` y `publicKeySpki` DER SPKI en base64. Las claves de confianza **deben suministrarse desde un ejecutor protegido y fuera del PR**; nunca desde archivos, comentarios o entradas del PR.
- Roles, firmas, digests, SHA, casos duplicados/incompletos, rutas y proveedores se validan estrictamente. Dos roles no pueden reutilizar identidad o clave.
- No registrar el corpus bruto ni tokens; el resultado de la función contiene sólo estados y métricas acotadas.
- No existe ejecución automática de esos bundles en un job con permisos, por diseño. La revisión independiente del origen real del corpus aún está pendiente.

## Pasos para completar calibración real
1. Un revisor **humano independiente** crea y documenta al menos 100 positivos y 100 negativos, representando 5 familias, desde defectos verificados y casos limpios.
2. Un runner aislado, distinto del etiquetador, ejecuta la versión inmutable en los mismos casos y registra salida y tiempos observados.
3. Cada parte firma exclusivamente su propio artefacto; un administrador valida identidades y fija claves públicas *fuera de la rama candidata*.
4. Un sistema confiable invoca `evaluateSignedCalibration` con esos artefactos. Después se inspeccionan manualmente FN/FP, cobertura por familia y sesgos, sin usar los umbrales sintéticos como autoridad de producción.

## Control de autoridad
Los estados `UNVERIFIED_EVIDENCE`, `INVALID_CASES` y `SIGNED_SHADOW_ONLY` nunca conceden `autoMergeAllowed`, `autoCommitAllowed` ni `productionBlockingEnabled`. Las dos firmas sólo establecen integridad frente a las claves del llamador: **no prueban por sí solas calidad empírica o independencia organizacional**. Todos los resultados quedan en modo sombra hasta una auditoría real.
