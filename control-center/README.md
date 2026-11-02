# GameHub Ultra Control Center

Panel de estado para GameHub Ultra, preparado para Vercel. Estética rojo/negro, adaptable a móvil, **datos de GitHub reales** (PR, GitHub Actions, ejecuciones fallidas, APK de releases o enlaces a artefactos) y una vista **privada y opcional** de incidencias de Sentry.

## Despliegue en Vercel

1. En Vercel, crea **New Project → Import Git Repository → `cardenaspiero255-lang/gamehub-ultra`**. Autoriza el repositorio si aún no aparece.
2. Configura **Root Directory** como `control-center` (esta carpeta). Selecciona **Framework Preset: Other**. No requiere Build Command ni Output Directory personalizados.
3. Publica el proyecto. Vercel generará una dirección `*.vercel.app`. **No se despliega automáticamente solo por tener la GitHub App instalada.**
4. Opcionalmente, añade `GITHUB_TOKEN` como variable de entorno para aumentar los límites de GitHub API. Usa un token de permisos **solo lectura**, limitado a este repositorio, con `Contents` y `Actions` / `Pull requests` según los endpoints que uses. **Nunca** añadas secretos al frontend ni al repositorio.
5. Si deseas mostrar errores reales de Sentry, configura **todas** estas variables como secretos del proyecto en Vercel: `SENTRY_AUTH_TOKEN` (token con permiso de lectura de incidencias), `SENTRY_ORG_SLUG`, `SENTRY_PROJECT_SLUG` y `CONTROL_CENTER_ACCESS_KEY` (clave fuerte y distinta del token de Sentry). Se pide esta última en el panel para acceder a errores. La clave no se guarda en el navegador ni se incluye en URLs.

La web puede ser pública para **metadatos del repositorio que ya sean públicos**. Las incidencias de Sentry **nunca** se muestran sin una clave de acceso si la integración está configurada. No conectes Sentry a un proyecto público si quieres mantener privado hasta el propio metadato de sus errores.

## Desarrollo y pruebas

```bash
cd control-center
npm test
npm run check
```

Node.js 20+ y ninguna dependencia de terceros para ejecutar las pruebas.

## Comportamiento y límites

- Los PR incluyen su estado Draft, que **no** se considera aprobado.
- Se muestran estados reales de GitHub: los checks pendientes no son presentados como aprobados.
- El contador de workflows y fallos se limita a las últimas páginas consultadas (no es el total histórico).
- Se priorizan APK publicadas en GitHub Releases. Si no hay, se enlazan los workflows con artefactos APK recientes para abrirlos en GitHub (no hay descarga directa pública garantizada).
- Si GitHub limita o rechaza una consulta, el panel señala **datos parciales**; nunca inventa una cifra ni un estado verde.
- El botón Actualizar obtiene nuevos datos. No se necesita una cuenta Vercel para ver la parte pública.
- Este panel es de **solo lectura**. No modifica PR, no reinicia jobs, no descarga secretos y no ejecuta una autocorrección autónoma.
- Sentry requiere una clave de acceso, enviada directamente al endpoint serverless sobre HTTPS. Evita compartirla.

## Seguridad

La parte privada de Sentry se sirve solo desde `/api/errors` y exige `X-Control-Key`; la comparación de la clave usa `timingSafeEqual`. No almacenar la clave en `localStorage`, no habilitar CORS público en `/api/errors`, y mantener token y clave distintos. Aplica límites de acceso de Vercel/WAF cuando haya varios usuarios, dado que un password simple sin limitación de peticiones es una capa de protección mínima.

Los enlaces renderizados por el frontend se limitan a `github.com` y `sentry.io`. La política CSP rechaza JavaScript externo y scripts inline.