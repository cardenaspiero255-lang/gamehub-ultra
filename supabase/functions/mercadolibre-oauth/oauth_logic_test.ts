import { persistCredentialSetAtomically } from "./credential_store.ts";
import {
  buildAuthorizationUrl,
  buildTokenExchangeBody,
  callbackStateMatches,
  redirectUriForProject,
} from "./oauth_logic.ts";

Deno.test("builds Chile Mercado Libre authorization URL with PKCE and state", () => {
  const projectUrl = "https://example.supabase.co";
  const redirectUri = redirectUriForProject(projectUrl);
  const url = new URL(
    buildAuthorizationUrl({
      clientId: "123456",
      redirectUri,
      state: "state-abc",
      codeChallenge: "challenge-xyz",
    }),
  );

  if (url.origin !== "https://auth.mercadolibre.cl") {
    throw new Error("expected Chile Mercado Libre authorization host");
  }
  if (url.pathname !== "/authorization") {
    throw new Error("expected authorization endpoint");
  }
  if (url.searchParams.get("response_type") !== "code") {
    throw new Error("expected authorization code flow");
  }
  if (url.searchParams.get("client_id") !== "123456") {
    throw new Error("expected client id");
  }
  if (url.searchParams.get("redirect_uri") !== redirectUri) {
    throw new Error("expected exact redirect uri");
  }
  if (url.searchParams.get("state") !== "state-abc") {
    throw new Error("expected state");
  }
  if (url.searchParams.get("code_challenge") !== "challenge-xyz") {
    throw new Error("expected PKCE challenge");
  }
  if (url.searchParams.get("code_challenge_method") !== "S256") {
    throw new Error("expected S256 PKCE");
  }
});

Deno.test("token exchange body carries exact redirect URI and verifier", () => {
  const body = new URLSearchParams(
    buildTokenExchangeBody({
      clientId: "123",
      clientSecret: "secret",
      code: "code-1",
      redirectUri:
        "https://example.supabase.co/functions/v1/mercadolibre-oauth-callback",
      codeVerifier: "verifier-1",
    }),
  );

  if (body.get("grant_type") !== "authorization_code") {
    throw new Error("expected authorization_code grant");
  }
  if (body.get("client_secret") !== "secret") {
    throw new Error("expected client secret");
  }
  if (body.get("code_verifier") !== "verifier-1") {
    throw new Error("expected PKCE verifier");
  }
});

Deno.test("callback state must exactly match the browser state", () => {
  if (!callbackStateMatches("same-state", "same-state")) {
    throw new Error("expected matching state");
  }
  if (callbackStateMatches("returned-state", "different-state")) {
    throw new Error("mismatched state must be rejected");
  }
});


Deno.test("OAuth credential set is committed atomically", async () => {
  const committed: string[] = [];
  let transactionCalls = 0;

  const runTransaction = async (
    action: (write: (name: string) => Promise<void>) => Promise<void>,
  ) => {
    transactionCalls += 1;
    const staged: string[] = [];
    const write = async (name: string) => {
      staged.push(name);
      if (name === "mercadolibre_refresh_token") {
        throw new Error("simulated write failure");
      }
    };

    await action(write);
    committed.push(...staged);
  };

  let failed = false;
  try {
    await persistCredentialSetAtomically(
      runTransaction,
      {
        accessToken: "access",
        refreshToken: "refresh",
        expiresAt: "2026-09-27T06:00:00.000Z",
        userId: "123",
      },
    );
  } catch {
    failed = true;
  }

  if (!failed) throw new Error("expected transaction failure");
  if (transactionCalls !== 1) throw new Error("expected one transaction");
  if (committed.length !== 0) {
    throw new Error("partial OAuth credentials must not be committed");
  }
});
