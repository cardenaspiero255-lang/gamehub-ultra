import { strict as assert } from "node:assert";
import { test } from "node:test";

test("production Deno entrypoint loads and rejects unauthenticated requests without env secrets", async () => {
  const prior = globalThis.Deno;
  let capturedHandler;
  globalThis.Deno = {
    env: { get: () => undefined },
    serve: (fn) => { capturedHandler = fn; },
  };
  try {
    await import("./index.mjs?ci-offline-entrypoint-test");
    assert.equal(typeof capturedHandler, "function", "entrypoint must call Deno.serve");
    const req = new Request("https://example.invalid/functions/v1/car-autopilot", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{}",
    });
    const res = await capturedHandler(req);
    assert.equal(res.status, 503, "without secrets, production entrypoint must fail closed");
    assert.deepEqual(await res.json(), { status: "not_configured" });
  } finally {
    if (prior === undefined) delete globalThis.Deno;
    else globalThis.Deno = prior;
  }
});
