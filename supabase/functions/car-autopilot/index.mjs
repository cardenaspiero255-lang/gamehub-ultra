import { handleCarWebhook } from "./handler.mjs";

// GitHub does not send a Supabase JWT. Inbound requests are instead
// authenticated with the raw-body GitHub HMAC-SHA256 webhook signature.
// The privileged service-role key is never sent to GitHub or to clients.
// The private transactional reservation RPC MUST be installed first.
const supabaseUrl = Deno.env.get("SUPABASE_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

async function durableClaim(deliveryId, nextIssue, completedIssue) {
  if (!supabaseUrl || !serviceKey ||
      !/^https:\/\/[A-Za-z0-9.-]+\.supabase\.co$/.test(supabaseUrl))
    throw new Error("Durable CAR ledger is not configured");
  const response = await fetch(supabaseUrl + "/rest/v1/rpc/car_autopilot_reserve", {
    method: "POST",
    headers: {
      "apikey": serviceKey,
      "authorization": "Bearer " + serviceKey,
      "content-type": "application/json",
      "accept": "application/json",
      "cache-control": "no-store",
    },
    body: JSON.stringify({
      p_repository_id: 1375408706,
      p_completed_issue: completedIssue,
      p_next_issue: nextIssue,
      p_delivery_id: deliveryId,
    }),
  });
  if (!response.ok) throw new Error("CAR ledger RPC unavailable: " + response.status);
  return (await response.json()) === true;
}

Deno.serve((request) => handleCarWebhook(request, {
  secret: Deno.env.get("CAR_GITHUB_WEBHOOK_SECRET"),
  token: Deno.env.get("CAR_GITHUB_COPILOT_USER_TOKEN"),
  claim: durableClaim,
}));
