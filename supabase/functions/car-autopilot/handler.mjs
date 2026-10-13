// Signed GitHub webhook; no schedules, no direct merges, no workflow write token.
// GitHub repository ID survives repository renames.
const REPOSITORY_ID = 1375408706;
const OWNER_ID = 322222186;
const BOT = "copilot-swe-agent[bot]";
const json = (status, data) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const exactCar = (value) => /^CAR-([0-9]+)(?=\b|[^0-9])/i.exec(String(value || ""))?.[1] ?? null;
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
  if (req.headers.get("x-github-event") !== "pull_request")
    return json(202, { status: "ignored_event" });

  let event;
  try { event = JSON.parse(new TextDecoder().decode(raw)); }
  catch { return json(400, { status: "invalid_json" }); }
  const pr = event?.pull_request;
  if (event?.repository?.id !== REPOSITORY_ID ||
      event?.repository?.owner?.id !== OWNER_ID ||
      event?.action !== "closed" || pr?.merged !== true ||
      pr?.base?.ref !== "main" ||
      pr?.head?.repo?.id !== REPOSITORY_ID ||
      ![BOT, "cardenaspiero255-lang"].includes(pr?.user?.login))
    return json(202, { status: "ignored_non_authorized_merge" });

  const previousCar = exactCar(pr.title);
  if (!previousCar || !pr.merged_at) return json(202, { status: "ignored_non_car" });
  const merged = Date.parse(pr.merged_at);
  if (!Number.isFinite(merged) || timestamp - merged < -300000 ||
      timestamp - merged > 600000)
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

  try {
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

    // Scope token to repository Issues read/write, Pull requests read, and Metadata.
    // No GitHub Contents write, CI bypass, or Merge API permissions.
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
