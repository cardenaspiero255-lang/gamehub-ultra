# CAR Autopilot — webhook GitHub → Supabase → Copilot (preparado, **NO DESPLEGADO**)

## Contrato de seguridad

- Solo procesa eventos `issues.closed` firmados por GitHub con `X-Hub-Signature-256`, tamaño <=128 KiB, ID inmutable de repositorio y emisor verificables, CAR del propietario con `car:active`, estado `completed` y cierre reciente.
- **No confía en el cierre manual**: consulta a GitHub y exige un PR `CAR-N` realmente fusionado a `main`, mismo número CAR, mismo repositorio, usuario de confianza y `Fixes #<número EXACTO de issue>` en el cuerpo, cerca de la fecha del cierre.
- Solo selecciona un issue `car:ready` creado por el propietario. Relee el issue antes de actuar. Si hay otro CAR activo, datos ambiguos o resultados truncados, falla cerrado.
- **Una reserva transaccional y duradera** en Supabase (tabla/RPC del archivo `supabase/migrations/20261013180000_car_autopilot_dispatch.sql`) se obtiene antes de asignar el CAR. La RPC serializa las entregas con un advisory lock, registra el CAR más recientemente asignado y rechaza eventos duplicados/retrasados. Si faltan migración o credenciales, **NO asigna nada**. No se almacena información privada de los issues.
- Si GitHub confirma la asignación pero falla el etiquetado o viceversa, se conserva la reserva y se detiene para recuperación manual (no se repite ciegamente el despacho).
- **No fusiona PR ni modifica código**. Las reglas independientes de Mergify y la protección de main gobiernan las fusiones.

## Limitación importante de permisos de Copilot

La API de GitHub Copilot para asignar un issue a un agente **requiere un token de usuario** (PAT o GitHub App user-to-server token). GitHub documenta que un PAT de granularidad fina debe tener **Metadata:read y Actions, Contents, Issues y Pull requests:read/write**, incluso aunque este webhook invoque solo el endpoint de asignación. Ver https://docs.github.com/en/copilot/how-tos/use-copilot-agents/cloud-agent/use-cloud-agent-via-the-api.

Por eso **NO ES CORRECTO** decir que un token `Issues:write` basta ni que una credencial para el servicio nunca podría modificar código. Por seguridad, usa una identidad GitHub específica, credencial de corta duración si es posible, limitada a este único repositorio, sin compartirla en chats ni en archivos. La autorización de esos permisos sensibles requiere una decisión consciente del propietario antes de desplegar. Usa el secreto `CAR_GITHUB_COPILOT_USER_TOKEN`.

## Instalación manual única (pendiente)

1. Validar y fusionar el PR #195 con revisión de seguridad y todas sus comprobaciones verdes en el HEAD; confirma que el agente `gamehub-car-engineer` esté en `main`.
2. Verificar protección `main` en GitHub. El 13/10/2026 GitHub reportó `protected=true` y cuatro checks obligatorios.
3. Revisar la migración SQL, su RLS, privilegios y transacción. Aplicarla **solo después de la revisión** al proyecto Supabase autorizado. No habilitar Data API de escritura a usuarios anónimos o autenticados; la función RPC y tabla conceden acceso solo a `service_role`.
4. Configurar en los secrets de Supabase Edge Functions: `CAR_GITHUB_WEBHOOK_SECRET` (secreto aleatorio fuerte propio) y `CAR_GITHUB_COPILOT_USER_TOKEN` (token de usuario con los permisos documentados, restringido a este repositorio). El servicio recibe `SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` en backend; nunca los envíes al cliente ni a GitHub. Validar su presencia tras el despliegue.
5. Desplegar **únicamente esta función** con `verify_jwt=false`, porque GitHub autentica con la firma HMAC del cuerpo. Se rechazan solicitudes no firmadas o sin secrets. No desactivar JWT de otras funciones.
6. Crear en GitHub Settings → Webhooks un webhook con URL `https://<ref-del-proyecto>.supabase.co/functions/v1/car-autopilot`, content type JSON, SSL verification habilitado, el secreto igual al de Supabase, y evento **Issues** únicamente. El webhook no necesita escuchar `pull_request`, evitando carreras mientras GitHub cierra issues.
7. Habilitar Copilot cloud coding agent y verificar que es asignable en el repositorio; crear primero un CAR piloto no sensible con `car:ready`. Las dependencias deben verificarse antes de aplicar esa etiqueta. El agente tiene que crear PR draft con título `CAR-N` y `Fixes #<issue>` en el cuerpo.
8. Prueba de extremo a extremo: cierre manual sin merge no activa otro CAR; CAR fusionado activa exactamente uno; dos webhooks paralelos no duplican; secretos inválidos y errores de GitHub fallan cerrado; la nueva fusión requiere los cuatro checks de main. Registrar pruebas y bloquear ante fallos.

## Tests y límites

Pruebas unitarias: `node --test supabase/functions/car-autopilot/handler.test.mjs`; workflow GitHub Actions `CAR Autopilot Webhook Tests`. El registro SQL se verifica en un entorno seguro antes de producción. La función no corrige PR fallidos por sí sola: esa labor queda para el agente Copilot y el seguimiento horario. Si la migración no existe o GitHub rechaza el token, no hay despacho, que es el comportamiento seguro.

**Estado actual:** archivos en PR #196 draft, no fusionados ni desplegados; credenciales y webhook externo sin configurar. No considerar el circuito operativo hasta una validación real.
