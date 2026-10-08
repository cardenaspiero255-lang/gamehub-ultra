import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import postgres from "npm:postgres@3.4.5";

import { routeResearchQuery } from "./research.ts";

const ULTRA_RESEARCH_ENGINE_VERSION = "20";

type ResearchRequest = {
  query?: string;
  clientEngineVersion?: string;
  context?: string;
  kind?: string;
  verificationMode?: string;
  requiresFreshData?: boolean;
  correlationId?: string;
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

const quietRequestLogs =
  Deno.env.get("ULTRA_QUIET_REQUEST_LOGS")?.trim() === "1";

let vaultSql: ReturnType<typeof postgres> | null = null;

async function vaultSecret(name: string): Promise<string | undefined> {
  const dbUrl = Deno.env.get("SUPABASE_DB_URL")?.trim();
  if (!dbUrl) return undefined;

  vaultSql ??= postgres(dbUrl, {
    prepare: false,
    max: 1,
    idle_timeout: 5,
  });

  const rows = await vaultSql.unsafe(
    "select decrypted_secret from vault.decrypted_secrets where name = $1 limit 1",
    [name],
  ) as Array<{ decrypted_secret?: string }>;

  const value = rows[0]?.decrypted_secret;
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function validApiKey(req: Request): boolean {
  const supplied = req.headers.get("apikey") ?? "";
  if (!supplied) return false;

  let publishableMatch = false;
  try {
    const keys = JSON.parse(
      Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}",
    );
    publishableMatch = Object.values(keys).includes(supplied);
  } catch {
    publishableMatch = false;
  }

  const legacy = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  return publishableMatch || (legacy.length > 0 && supplied === legacy);
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }
  if (!validApiKey(req)) {
    return json({ error: "Unauthorized" }, 401);
  }

  let body: ResearchRequest;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const query = (body.query ?? "").trim();
  const context = (body.context ?? "").trim();
  if (!query || query.length > 1200 || context.length > 1200) {
    return json({ error: "Invalid query" }, 400);
  }

  if (
    typeof body.clientEngineVersion === "string" &&
    body.clientEngineVersion.trim() &&
    body.clientEngineVersion.trim() !== ULTRA_RESEARCH_ENGINE_VERSION
  ) {
    return json(
      {
        engineVersion: ULTRA_RESEARCH_ENGINE_VERSION,
        abstained: true,
        reasonCode: "CLIENT_VERSION_MISMATCH",
        retryable: false,
        stage: "protocol",
        message:
          `Ultra Research V${ULTRA_RESEARCH_ENGINE_VERSION} requiere un cliente compatible.`,
      },
      409,
    );
  }

  const correlationId = (
    typeof body.correlationId === "string" &&
      /^[a-zA-Z0-9-]{8,80}$/.test(body.correlationId)
  )
    ? body.correlationId
    : crypto.randomUUID();
  const kind = body.kind ?? "";
  const verificationMode = body.verificationMode ?? "";
  if (
    verificationMode !== "LOCAL" &&
    verificationMode !== "OPTIONAL" &&
    verificationMode !== "REQUIRED"
  ) {
    return json({ error: "Invalid verification mode" }, 400);
  }

  if (!quietRequestLogs) {
    console.info(JSON.stringify({
      event: "ultra_research_started",
      correlationId,
      kind,
      verificationMode,
    }));
  }

  try {
    const result = await routeResearchQuery(
      query,
      {
        fetcher: fetch,
        env: (name) => Deno.env.get(name),
        secret: vaultSecret,
        sleep: (milliseconds) =>
          new Promise((resolve) => setTimeout(resolve, milliseconds)),
      },
      context,
      kind,
      verificationMode,
    );

    if (!quietRequestLogs) {
      console.info(JSON.stringify({
        event: "ultra_research_finished",
        correlationId,
        kind,
        verificationMode,
        abstained: result.abstained === true,
        reasonCode: result.reasonCode ?? null,
        stage: result.stage ?? null,
        sourceCount: result.sourceIds?.length ?? (result.sourceId ? 1 : 0),
      }));
    }

    return json({
      ...result,
      engineVersion: ULTRA_RESEARCH_ENGINE_VERSION,
    });
  } catch (error) {
    console.error(JSON.stringify({
      event: "ultra_research_failed",
      correlationId,
      kind,
      verificationMode,
      reasonCode: "BACKEND_FAILURE",
      errorType: error instanceof Error ? error.name : "UnknownError",
      errorMessage: error instanceof Error ? error.message : String(error),
      errorStack: error instanceof Error ? error.stack ?? null : null,
    }));

    return json(
      {
        engineVersion: ULTRA_RESEARCH_ENGINE_VERSION,
        abstained: true,
        reasonCode: "BACKEND_FAILURE",
        retryable: true,
        stage: "edge",
        message: "El servicio de consulta no está disponible ahora. Reintenta.",
      },
      502,
    );
  }
});
