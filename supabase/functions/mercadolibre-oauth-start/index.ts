import "jsr:@supabase/functions-js/edge-runtime.d.ts";

import {
  buildAuthorizationUrl,
  oauthCookie,
  pkceChallenge,
  randomUrlSafe,
  redirectUriForProject,
} from "../mercadolibre-oauth/oauth_logic.ts";

function html(message: string, status = 500): Response {
  return new Response(
    `<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GameHub Ultra</title><body><h1>GameHub Ultra</h1><p>${message}</p></body></html>`,
    {
      status,
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
        "Referrer-Policy": "no-referrer",
        "X-Content-Type-Options": "nosniff",
      },
    },
  );
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return html("Este endpoint solo admite GET.", 405);
  }

  const projectUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const clientId = Deno.env.get("MERCADOLIBRE_CLIENT_ID")?.trim();
  if (!projectUrl || !clientId) {
    return html(
      "Falta configurar MERCADOLIBRE_CLIENT_ID en los secretos de Supabase.",
      503,
    );
  }

  const state = randomUrlSafe(32);
  const verifier = randomUrlSafe(64);
  const challenge = await pkceChallenge(verifier);
  const redirectUri = redirectUriForProject(projectUrl);
  const authorizationUrl = buildAuthorizationUrl({
    clientId,
    redirectUri,
    state,
    codeChallenge: challenge,
  });

  const headers = new Headers({
    "Location": authorizationUrl,
    "Cache-Control": "no-store",
    "Referrer-Policy": "no-referrer",
  });
  headers.append("Set-Cookie", oauthCookie("ml_oauth_state", state));
  headers.append("Set-Cookie", oauthCookie("ml_pkce_verifier", verifier));

  return new Response(null, { status: 302, headers });
});
