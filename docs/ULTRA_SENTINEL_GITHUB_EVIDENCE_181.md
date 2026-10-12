# Ultra Sentinel — PR #181 · Autenticación de evidencia GitHub (fase 1)

**Estado:** integración preparatoria en modo sombra; NO es aprobación, Quality Gate bloqueante,
autorreparación ni autorización para fusionar. No reemplaza los controles existentes.

## Lo que implementa

- Colector de solo lectura con peticiones GET a la API oficial de GitHub, paths validados,
  token de ámbito limitado y errores de procedencia que se rechazan explícitamente.
- Comprueba número del PR, propietario del repositorio base, HEAD/base SHA de 40 caracteres,
  estado abierto, merge-base obtenido de la API Compare y estabilidad del PR al finalizar.
- Página y compara el listado de archivos sin admitir resultados truncados (límite: 250).
- Lee el historial de ejecución de workflows asociado al SHA del HEAD y al PR,
  exigiendo además los IDs de los tres workflows confiables. Aplica
  selección de ejecución más reciente, incluyendo reintentos, y distingue PASS,
  BLOCKED e INCOMPLETE sin aceptar resultados omitidos, pendientes ni desactualizados.
- Reutiliza el escáner confiable existente ultra_sentinel_selfreview para leer el diff
  y, cuando procede, los archivos por SHA **como datos**. No ejecuta código del PR.
- Publica un resultado sanitizado de causas/reglas, archivos, líneas y estado de CI,
  pero jamás copia razones de texto libre, código fuente, tokens ni contenido de parches.
- Exige los contratos del evaluador del PR #180; como todavía faltan escaneos completos
  de base y HEAD, el dictamen global permanece forzosamente INCOMPLETE.
- Veintiuna pruebas nuevas de procedencia, TOCTOU, conclusiones duplicadas, reintentos,
  rutas peligrosas, evasión de SHA, filtración de secretos y límites de volumen.
- El workflow incluido **solo ejecuta esas pruebas sin credenciales** al modificar
  el colector. No accede a datos reales durante las ejecuciones de candidatos.

## Importante: integración real vs. ejecución de producción

El colector implementa la conexión real en código, pero la **ejecución confiable
automática todavía no está habilitada**. No se debe introducir un token de GitHub
en un workflow que ejecute código controlado por un PR sin revisión independiente.

Antes de habilitarlo en un workflow de confianza separado:

1. Fusionar únicamente cuando todas las pruebas y controles del SHA actual estén verdes.
2. Desde una revisión aprobada de la rama principal, ejecutar exclusivamente código de
   main con permisos de lectura (contents, pull-requests, actions). No hacer checkout,
   importar, ejecutar, compilar ni instalar dependencias suministradas por el HEAD del PR.
3. Validar que los parámetros HEAD/base pertenecen al PR recibido, y que los campos
   de procedencia vienen directamente de GitHub. Registrar únicamente diagnóstico.
4. Revalidar el SHA antes de publicar cualquier resultado. No crear estados de aprobación
   ni fusionar automáticamente.
5. Conectar escaneos independientes, completos y autenticados de base/HEAD; mientras falten,
   cualquier evaluación de la calidad integral debe declararse INCOMPLETE.
6. Medir falsos positivos, falsos negativos y estabilidad en PR reales antes de plantear
   un Quality Gate requerido.

El issue #179 sobre integración y seguridad de la línea base **permanece abierto**.
