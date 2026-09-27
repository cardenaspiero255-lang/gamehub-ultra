export type AuthorizationUrlInput = {
  clientId: string;
  redirectUri: string;
  state: string;
  codeChallenge: string;
};

export type TokenExchangeInput = {
  clientId: string;
  clientSecret: string;
  code: string;
  redirectUri: string;
  codeVerifier: string;
};

export function redirectUriForProject(projectUrl: string): string {
  return projectUrl.replace(/\/+$/, "") +
    "/functions/v1/mercadolibre-oauth-callback";
}

export function buildAuthorizationUrl(
  input: AuthorizationUrlInput,
): string {
  const url = new URL("https://auth.mercadolibre.cl/authorization");
  url.searchParams.set("response_type", "code");
  url.searchParams.set("client_id", input.clientId);
  url.searchParams.set("redirect_uri", input.redirectUri);
  url.searchParams.set("state", input.state);
  url.searchParams.set("code_challenge", input.codeChallenge);
  url.searchParams.set("code_challenge_method", "S256");
  return url.toString();
}

export function buildTokenExchangeBody(
  input: TokenExchangeInput,
): Record<string, string> {
  return {
    grant_type: "authorization_code",
    client_id: input.clientId,
    client_secret: input.clientSecret,
    code: input.code,
    redirect_uri: input.redirectUri,
    code_verifier: input.codeVerifier,
  };
}

export function callbackStateMatches(
  returnedState: string | null,
  expectedState: string | null,
): boolean {
  if (!returnedState || !expectedState) return false;
  if (returnedState.length !== expectedState.length) return false;

  let diff = 0;
  for (let i = 0; i < returnedState.length; i += 1) {
    diff |= returnedState.charCodeAt(i) ^ expectedState.charCodeAt(i);
  }
  return diff === 0;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

export function randomUrlSafe(bytes = 32): string {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return base64Url(data);
}

export async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(verifier),
  );
  return base64Url(new Uint8Array(digest));
}

export function parseCookie(header: string | null, name: string): string | null {
  if (!header) return null;
  for (const segment of header.split(";")) {
    const [key, ...rest] = segment.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

export function oauthCookie(
  name: string,
  value: string,
  maxAgeSeconds = 600,
): string {
  return [
    `${name}=${encodeURIComponent(value)}`,
    "Path=/",
    `Max-Age=${maxAgeSeconds}`,
    "HttpOnly",
    "Secure",
    "SameSite=Lax",
  ].join("; ");
}

export function clearOauthCookie(name: string): string {
  return [
    `${name}=`,
    "Path=/",
    "Max-Age=0",
    "HttpOnly",
    "Secure",
    "SameSite=Lax",
  ].join("; ");
}
