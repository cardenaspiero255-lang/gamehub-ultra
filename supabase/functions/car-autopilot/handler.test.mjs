import { strict as assert } from "node:assert";
import { test } from "node:test";
import { handleCarWebhook, validSignature } from "./handler.mjs";

const secret = "unit-test-signing-secret";
const config = { secret, token: "unit-test-token" };
const stamp = Date.parse("2026-10-13T17:00:00Z");

function fixture(overrides = {}) {
  return {
    action: "closed",
    repository: {
      id: 1375408706, name: "gamehub-ultra",
      full_name: "cardenaspiero255-lang/gamehub-ultra",
      owner: { id: 322222186, login: "cardenaspiero255-lang" },
    },
    pull_request: {
      title: "CAR-75 — test", merged: true, merged_at: "2026-10-13T17:00:00Z",
      user: { login: "copilot-swe-agent[bot]" }, base: { ref: "main" },
      head: { repo: { id: 1375408706 } },
    },
    ...overrides,
  };
}
async function request(event = fixture(), headers = {}) {
  const raw = JSON.stringify(event);
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), {
    name: "HMAC", hash: "SHA-256",
  }, false, ["sign"]);
  const digest = new Uint8Array(await crypto.subtle.sign(
    "HMAC", key, new TextEncoder().encode(raw),
  ));
  const sig = Array.from(digest, (n) => n.toString(16).padStart(2, "0")).join("");
  return new Request("https://edge.example/car-autopilot", {
    method: "POST", body: raw, headers: {
      "x-github-delivery": "c2d327d1-4350-43b8-a886-7802417fa2fa",
      "x-github-event": "pull_request",
      "x-hub-signature-256": "sha256=" + sig,
      ...headers,
    },
  });
}
function fakeGithub({ active = [], ready = [], latest = null, broken = false } = {}) {
  const calls = [];
  async function transport(url, options) {
    calls.push({ url, options });
    if (broken) return new Response("unavailable", { status: 503 });
    const path = new URL(url).pathname;
    let payload = {};
    if (path.endsWith("/issues")) {
      payload = url.includes("car%3Aactive") ? active : ready;
    } else if (/\/issues\/\d+\/assignees$/.test(path)) {
      payload = { assignees: [{ login: "copilot-swe-agent[bot]" }] };
    } else if (/\/issues\/\d+$/.test(path)) {
      payload = latest || ready[0];
    }
    return Response.json(payload);
  }
  return { calls, transport };
}
const next = {
  number: 256, state: "open", title: "CAR-76 — offline improvements",
  labels: [{ name: "car:ready" }], user: { login: "cardenaspiero255-lang" },
  assignees: [],
};

test("reject missing config, invalid auth and invalid method", async () => {
  assert.equal((await handleCarWebhook(await request(), {}, fetch, stamp)).status, 503);
  const bad = await request();
  bad.headers.set("x-hub-signature-256", "sha256=" + "0".repeat(64));
  assert.equal((await handleCarWebhook(bad, config, fetch, stamp)).status, 401);
  const get = new Request("https://edge.example");
  assert.equal((await handleCarWebhook(get, config, fetch, stamp)).status, 405);
  assert.equal(await validSignature(new Uint8Array(), "sha256=" + "0".repeat(64), secret), false);
});

test("ignore unrelated, stale and unauthorized events without API calls", async () => {
  for (const ev of [
    fixture({ action: "opened" }),
    fixture({ repository: { ...fixture().repository, id: 123 } }),
    fixture({ pull_request: { ...fixture().pull_request, merged: false } }),
    fixture({ pull_request: { ...fixture().pull_request, title: "not a CAR" } }),
  ]) {
    const api = fakeGithub();
    assert.equal((await handleCarWebhook(await request(ev), config, api.transport, stamp)).status, 202);
    assert.equal(api.calls.length, 0);
  }
  const late = await handleCarWebhook(await request(), config, fakeGithub().transport, stamp + 7200000);
  assert.equal((await late.json()).status, "stale_or_ambiguous_event");
});

test("dispatch exactly one oldest ready CAR with custom agent and no merge permissions", async () => {
  const older = { ...next, number: 255, title: "CAR-74 — scheduler safe" };
  const api = fakeGithub({ ready: [next, older], latest: older });
  const res = await handleCarWebhook(await request(), config, api.transport, stamp);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { status: "car_assigned", issue: 255 });
  const assign = api.calls.find((x) => x.url.endsWith("/issues/255/assignees"));
  assert.ok(assign);
  const body = JSON.parse(assign.options.body);
  assert.deepEqual(body.assignees, ["copilot-swe-agent[bot]"]);
  assert.equal(body.agent_assignment.custom_agent, "gamehub-car-engineer");
  const patch = api.calls.find((x) => x.options.method === "PATCH");
  assert.ok(patch);
  assert.deepEqual(JSON.parse(patch.options.body).labels, ["car:active"]);
  assert.ok(!api.calls.some((x) => /\/merges$/.test(x.url)));
});

test("active CAR blocks duplicate dispatch; no ready or invalid issue does not dispatch", async () => {
  const running = { ...next, title: "CAR-74 — still active", labels: [{ name: "car:active" }] };
  const f1 = fakeGithub({ active: [running], ready: [next] });
  assert.equal((await (await handleCarWebhook(await request(), config, f1.transport, stamp)).json()).status, "active_car_exists");
  assert.equal(f1.calls.filter((x) => x.options.method === "POST").length, 0);
  const f2 = fakeGithub({ ready: [{ ...next, user: { login: "attacker" } }] });
  assert.equal((await (await handleCarWebhook(await request(), config, f2.transport, stamp)).json()).status, "no_ready_car");
});

test("fail closed on changed issue or GitHub API error", async () => {
  const modified = { ...next, labels: [{ name: "car:blocked" }] };
  const f1 = fakeGithub({ ready: [next], latest: modified });
  assert.equal((await handleCarWebhook(await request(), config, f1.transport, stamp)).status, 409);
  const f2 = fakeGithub({ broken: true });
  assert.equal((await handleCarWebhook(await request(), config, f2.transport, stamp)).status, 502);
});
