import "jsr:@supabase/functions-js/edge-runtime.d.ts";

import { routeResearchQuery } from "./research.ts";

type ResearchRequest = {
  query?: string;
  kind?: string;
  requiresFreshData?: boolean;
};

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

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
  if (!query || query.length > 1200) {
    return json({ error: "Invalid query" }, 400);
  }

  try {
    return json(
      await routeResearchQuery(query, {
        fetcher: fetch,
        env: (name) => Deno.env.get(name),
      }),
    );
  } catch {
    return json({
      abstained: true,
      message: "No pude verificar la consulta con las fuentes actuales.",
    });
  }
});
