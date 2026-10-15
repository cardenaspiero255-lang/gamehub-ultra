// Signed GitHub webhook; no schedules, no direct merges, no workflow write token.
// GitHub repository ID survives repository renames.
const REPOSITORY_ID = 1375408706;
const OWNER_ID = 322222186;
const BOT = "copilot-swe-agent[bot]";
const json = (status, data) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
// Accept only an exact CAR-N token, followed by end-of-title, whitespace, or
// a genuine punctuation separator. Never treat CAR-75malformed as CAR-75.
const exactCar = (value) => /^CAR-([1-9][0-9]*)(?=$|[ \t]|[:—–-][ \t])/i.exec(String(value || ""))?.[1] ?? null;
const hasLabel = (issue, name) => issue.labels?.some((l) => (typeof l === "string" ? l : l.name) === name) === true;

export async function validSignature(bytes, header, secret) {
  if (!secret || !/^sha256=[a-f0-9]{64}$/i.test(header || "")) return false;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, bytes));
  const provided = (header || "").slice(7).toLowerCase();
  let difference = 0;
  for (let i = 0; i < digest.length; i++) {
    difference |= digest[i] ^ Number.parseInt(provided.slice(i * 2, i * 2 + 2), 16);
  }
  return difference === 0;
}

export async function handleCarWebhook(req, config, transport = fetch, timestamp = Date.now()) {
  if (req.method !== "POST") return json(405, { status: "method_not_allowed" });
  // A missing secret is never interpreted as optional authentication.
  if (!config?.secret || !config?.token) return json(503, { status: "not_configured" });
  const len = Number(req.headers.get("content-length") || "0");
  if (len > 131072) return json(413, { status: "payload_too_large" });
  const raw = new Uint8Array(await req.arrayBuffer());
  if (raw.length > 131072) return json(413, { status: "payload_too_large" });
  if (!(await validSignature(raw, req.headers.get("x-hub-signature-256"), config.secret)))
    return json(401, { status: "invalid_signature" });
  const delivery = req.headers.get("x-github-delivery");
  if (!delivery || !/^[0-9a-f-]{20,64}$/i.test(delivery))
    return json(400, { status: "invalid_delivery_id" });
  const eventType = req.headers.get("x-github-event");
  if (eventType !== "issues")
    return json(202, { status: "ignored_event" });

  let event;
  try { event = JSON.parse(new TextDecoder().decode(raw)); }
  catch { return json(400, { status: "invalid_json" }); }
  if (event?.repository?.id !== REPOSITORY_ID ||
      event?.repository?.owner?.id !== OWNER_ID)
    return json(202, { status: "bad_repository_identity" });

  // Only the issue CLOSED event may dispatch. The linked, merged PR is
  // independently verified below via REST to prevent manual "completed" skips.
  const completedIssue = event?.issue;
  if (event?.action !== "closed" || completedIssue?.state !== "closed" ||
      completedIssue?.state_reason !== "completed" ||
      completedIssue?.user?.login !== "cardenaspiero255-lang" ||
      !Number.isSafeInteger(completedIssue?.number) || completedIssue.number <= 0 ||
      !hasLabel(completedIssue, "car:active") || !exactCar(completedIssue.title))
    return json(202, { status: "ignored_non_car_close" });
  const closed = Date.parse(completedIssue.closed_at || "");
  if (!Number.isFinite(closed) || timestamp - closed < -300000 ||
      timestamp - closed > 600000)
    return json(202, { status: "stale_or_ambiguous_event" });

  const fullName = event.repository.full_name;
  const [owner, name] = String(fullName || "").split("/");
  if (owner !== "cardenaspiero255-lang" || name !== event.repository.name ||
      !/^[A-Za-z0-9_.-]{1,100}$/.test(name))
    return json(202, { status: "bad_repository_identity" });

  const api = async (method, uri, body) => {
    const response = await transport(
      "https://api.github.com/repos/" + owner + "/" + name + uri, {
        method,
        headers: {
          "accept": "application/vnd.github+json",
          "authorization": "Bearer " + config.token,
          "x-github-api-version": "2022-11-28",
          "content-type": "application/json",
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      },
    );
    if (!response.ok) throw new Error("GitHub API " + response.status);
    if (response.status === 204) return null;
    return response.json();
  };

  // Claim must be backed by a durable, atomic external ledger. No in-memory
  // promise, Set, or best-effort label write provides global idempotency.
  if (typeof config.claim !== "function")
    return json(503, { status: "durable_claim_not_configured" });

  try {
    // A manual issue closure must NOT unlock the next CAR. Inspect real
    // merged PRs and require an exact closing-reference and CAR identity.
    const recent = await api("GET", "/pulls?state=closed&sort=updated&direction=desc&per_page=100");
    if (!Array.isArray(recent) || recent.length >= 100)
      return json(409, { status: "ambiguous_linked_merges" });
    // An exact GitHub closing reference must not treat #254abc or #254.6 as #254.
    const reference = new RegExp("(?:^|\\s)(?:fixes|closes|resolves)\\s+#" + completedIssue.number + "(?=$|\\s|[,.!?;:)](?=\\s|$))", "i");
    const confirmed = recent.some((pr) => {
      const mergedAt = Date.parse(pr.merged_at || "");
      return Boolean(
        exactCar(pr.title) === exactCar(completedIssue.title) &&
        reference.test(pr.body || "") &&
        pr.base?.ref === "main" &&
        pr.head?.repo?.id === REPOSITORY_ID &&
        [BOT, "cardenaspiero255-lang"].includes(pr.user?.login) &&
        Number.isFinite(mergedAt) && mergedAt <= closed &&
        closed - mergedAt <= 900000
      );
    });
    if (!confirmed) return json(202, { status: "unverified_car_completion" });

    // One active CAR at a time. A duplicate webhook will be a no-op.
    const active = await api("GET", "/issues?state=open&labels=car%3Aactive&per_page=100");
    if (!Array.isArray(active) || active.length >= 100)
      return json(409, { status: "ambiguous_active_queue" });
    if (active.some((i) => !i.pull_request && exactCar(i.title)))
      return json(202, { status: "active_car_exists" });

    const ready = await api("GET", "/issues?state=open&labels=car%3Aready&per_page=100");
    if (!Array.isArray(ready) || ready.length >= 100)
      return json(409, { status: "ambiguous_ready_queue" });

    const eligible = ready.filter((i) => !i.pull_request &&
      i.user?.login === "cardenaspiero255-lang" &&
      exactCar(i.title) !== null && hasLabel(i, "car:ready") &&
      !hasLabel(i, "car:blocked") && !hasLabel(i, "car:active") &&
      !i.assignees?.some((a) => a.login === BOT));
    eligible.sort((a, b) => Number(exactCar(a.title)) - Number(exactCar(b.title)));
    const next = eligible[0];
    if (!next) return json(202, { status: "no_ready_car" });
    // Re-read target before writing: no stale task, no accidental reassignment.
    const latest = await api("GET", "/issues/" + next.number);
    if (latest.state !== "open" || !hasLabel(latest, "car:ready") ||
        hasLabel(latest, "car:blocked") || latest.user?.login !== "cardenaspiero255-lang" ||
        exactCar(latest.title) !== exactCar(next.title) ||
        latest.assignees?.some((a) => a.login === BOT))
      return json(409, { status: "car_changed_during_selection" });

    // Atomic reservation BEFORE any write; if claim fails do not assign.
    // Production adapter must persist and serialize claims across isolates.
    const claim = await config.claim(delivery, latest.number, completedIssue.number);
    if (claim !== true) return json(202, { status: "already_claimed" });

    // NOTE: Copilot assignment API may require a high-privilege user token.
    // The integration cannot be activated by a low-scope Issues-only token.
    // Never grant broader permissions without explicit owner authorization.
    await api("POST", "/issues/" + latest.number + "/assignees", {
      assignees: [BOT],
      agent_assignment: {
        target_repo: fullName,
        base_branch: "main",
        custom_agent: "gamehub-car-engineer",
        custom_instructions: "Follow .github/agents/gamehub-car-engineer.agent.md; prepare draft PR, TDD and strict CI. Do not merge.",
      },
    });
    await api("PATCH", "/issues/" + latest.number, {
      labels: [...new Set([
        ...(latest.labels || []).map((l) => typeof l === "string" ? l : l.name)
          .filter((label) => label !== "car:ready"),
        "car:active",
      ])],
    });
    return json(200, { status: "car_assigned", issue: latest.number });
  } catch {
    // Never log API tokens, webhook body, issue contents, or signature material.
    return json(502, { status: "dispatch_failed" });
  }
}
