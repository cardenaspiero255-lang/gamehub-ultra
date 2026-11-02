import test from 'node:test';
import assert from 'node:assert/strict';
import statusHandler from '../api/status.js';
import errorsHandler from '../api/errors.js';

function fakeResponse() {
  return {
    code: 200,
    headers: {},
    body: null,
    setHeader(key, value) { this.headers[key.toLowerCase()] = value; },
    status(value) { this.code = value; return this; },
    json(value) { this.body = value; return this; },
  };
}

test('status API disallows writes and uses nosniff', async () => {
  const response = fakeResponse();
  await statusHandler({method: 'POST'}, response);
  assert.equal(response.code, 405);
  assert.equal(response.headers['x-content-type-options'], 'nosniff');
});

test('Sentry endpoint stays private when configured and rejects missing keys', async () => {
  const keys = ['SENTRY_AUTH_TOKEN','SENTRY_ORG_SLUG','SENTRY_PROJECT_SLUG','CONTROL_CENTER_ACCESS_KEY'];
  const before = Object.fromEntries(keys.map(k => [k, process.env[k]]));
  Object.assign(process.env, {
    SENTRY_AUTH_TOKEN: 'secret-sentry-token',
    SENTRY_ORG_SLUG: 'gamehub',
    SENTRY_PROJECT_SLUG: 'ultra',
    CONTROL_CENTER_ACCESS_KEY: 'very-secret-password-123',
  });
  try {
    const response = fakeResponse();
    await errorsHandler({method:'GET',headers:{}}, response);
    assert.equal(response.code, 401);
    assert.equal(response.headers['cache-control'], 'private, no-store');
    assert.ok(!JSON.stringify(response.body).includes('secret-sentry-token'));
  } finally {
    for (const key of keys) { if (before[key] === undefined) delete process.env[key]; else process.env[key] = before[key]; }
  }
});

test('Sentry endpoint shows configuration instructions without exposing tokens when disabled', async () => {
  const response = fakeResponse();
  const old = process.env.SENTRY_AUTH_TOKEN;
  delete process.env.SENTRY_AUTH_TOKEN;
  try {
    await errorsHandler({method:'GET',headers:{}}, response);
    assert.equal(response.code, 200);
    assert.equal(response.body.configured, false);
    assert.deepEqual(response.body.issues, []);
  } finally { if (old !== undefined) process.env.SENTRY_AUTH_TOKEN = old; }
});