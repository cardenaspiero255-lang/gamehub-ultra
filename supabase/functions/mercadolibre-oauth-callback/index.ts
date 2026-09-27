import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import postgres from "npm:postgres@3.4.5";

import {
  buildTokenExchangeBody,
  callbackStateMatches,
  clearOauthCookie,
  parseCookie,
  redirectUriForProject,
} from "../mercadolibre-oauth/oauth_logic.ts";

type JsonObject = Record<string, unknown>;

function page(title: string, message: string, status = 200): Response {
  const headers = new Headers({
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store, no-cache, must-revalidate",
    "Pragma": "no-cache",
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Content-Security-Policy":
      "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'",
  });
  headers.append("Set-Cookie", clearOauthCookie("ml_oauth_state"));
  headers.append("Set-Cookie", clearOauthCookie("ml_pkce_verifier"));

  const body =
    '<!doctype html><html lang="es"><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width,initial-scale=1">' +
    '<title>' + title + '</title><style>' +
    'body{font-family:system-ui,sans-serif;max-width:680px;margin:48px auto;padding:0 20px;line-height:1.5}' +
    '.box{border:1px solid #ddd;border-radius:16px;padding:24px}' +
    'h1{font-size:1.35rem;margin-top:0}</style></head><body><div class="box"><h1>' +
    title + '</h1><p>' + message + '</p></div></body></html>';

  return new Response(body, { status, headers });
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

async function saveVaultSecret(
  sql: ReturnType<typeof postgres>,
  name: string,
  secret: string,
  description: string,
): Promise<void> {
  const existing = await sql.unsafe(
    "select id::text from vault.secrets where name = $1 limit 1",
    [name],
  ) as Array<{ id: string }>;

  if (existing[0]?.id) {
    await sql.unsafe(
      "select vault.update_secret($1::uuid, $2, $3, $4)",
      [existing[0].id, secret, name, description],
    );
  } else {
    await sql.unsafe(
      "select vault.create_secret($1, $2, $3)",
      [secret, name, description],
    );
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return page(
      "Método no permitido",
      "Este endpoint solo recibe el redirect OAuth de Mercado Libre.",
      405,
    );
  }

  const url = new URL(req.url);
  if (url.searchParams.get("error")) {
    return page(
      "Autorización no completada",
      "Mercado Libre no completó la autorización. Puedes cerrar esta pestaña e intentarlo nuevamente.",
      400,
    );
  }

  const code = url.searchParams.get("code")?.trim() ?? "";
  const returnedState = url.searchParams.get("state");
  const expectedState = parseCookie(
    req.headers.get("Cookie"),
    "ml_oauth_state",
  );
  const verifier = parseCookie(
    req.headers.get("Cookie"),
    "ml_pkce_verifier",
  );

  if (!code || !callbackStateMatches(returnedState, expectedState) || !verifier) {
    return page(
      "Autorización inválida",
      "El callback no coincide con una autorización iniciada por GameHub Ultra. Vuelve a iniciar el proceso.",
      400,
    );
  }

  const projectUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const dbUrl = Deno.env.get("SUPABASE_DB_URL")?.trim();
  const clientId = Deno.env.get("MERCADOLIBRE_CLIENT_ID")?.trim();
  const clientSecret = Deno.env.get("MERCADOLIBRE_CLIENT_SECRET")?.trim();

  if (!projectUrl || !dbUrl || !clientId || !clientSecret) {
    return page(
      "Configuración incompleta",
      "Falta configurar Client ID o Client Secret en los secretos de Supabase.",
      503,
    );
  }

  const redirectUri = redirectUriForProject(projectUrl);
  const tokenResponse = await fetch("https://api.mercadolibre.com/oauth/token", {
    method: "POST",
    headers: {
      "Accept": "application/json",
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams(
      buildTokenExchangeBody({
        clientId,
        clientSecret,
        code,
        redirectUri,
        codeVerifier: verifier,
      }),
    ),
  });

  let payload: JsonObject = {};
  try {
    payload = await tokenResponse.json() as JsonObject;
  } catch {
    payload = {};
  }

  const accessToken = stringValue(payload.access_token);
  const refreshToken = stringValue(payload.refresh_token);
  const expiresIn = typeof payload.expires_in === "number"
    ? payload.expires_in
    : null;

  if (!tokenResponse.ok || !accessToken || !refreshToken) {
    return page(
      "No se pudo completar OAuth",
      "Mercado Libre rechazó el intercambio del código. Revisa que la Redirect URI, Client ID, Client Secret y PKCE coincidan.",
      502,
    );
  }

  const sql = postgres(dbUrl, {
    prepare: false,
    max: 1,
    idle_timeout: 2,
  });

  try {
    await saveVaultSecret(
      sql,
      "mercadolibre_access_token",
      accessToken,
      "Access Token OAuth de Mercado Libre para GameHub Ultra",
    );
    await saveVaultSecret(
      sql,
      "mercadolibre_refresh_token",
      refreshToken,
      "Refresh Token OAuth de Mercado Libre para GameHub Ultra",
    );
    if (expiresIn != null) {
      const expiresAt = new Date(
        Date.now() + Math.max(0, expiresIn - 60) * 1000,
      ).toISOString();
      await saveVaultSecret(
        sql,
        "mercadolibre_access_expires_at",
        expiresAt,
        "Expiración estimada del Access Token de Mercado Libre",
      );
    }
    if (payload.user_id != null) {
      await saveVaultSecret(
        sql,
        "mercadolibre_user_id",
        String(payload.user_id),
        "ID de usuario autorizado de Mercado Libre",
      );
    }
  } finally {
    await sql.end({ timeout: 2 });
  }

  return page(
    "Mercado Libre conectado",
    "La autorización se completó correctamente. Los tokens quedaron guardados cifrados en Supabase Vault. Ya puedes cerrar esta pestaña y volver a GameHub Ultra.",
  );
});
