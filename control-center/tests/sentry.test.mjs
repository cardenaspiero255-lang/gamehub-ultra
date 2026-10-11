import test from 'node:test';
import assert from 'node:assert/strict';
import { authorized, createSentrySummary } from '../lib/sentry.js';

const config = { accessToken: 'long-secret-password', sentryToken: 'sentry-private', org: 'ultra-org', project: 'android-app' };

test('rejects missing or incorrect access key with no token leakage', () => {
  assert.equal(authorized('', config.accessToken), false);
  assert.equal(authorized('incorrect', config.accessToken), false);
  assert.equal(authorized(config.accessToken, config.accessToken), true);
});

test('maps private Sentry issues with safe issue links and no private API token', async () => {
  const fetcher = async (url, options) => {
    assert.match(url, /sentry\.io\/api\/0\/projects\/ultra-org\/android-app\/issues/);
    assert.equal(options.headers.Authorization, 'Bearer sentry-private');
    return new Response(JSON.stringify([{ id: '123', title: 'TypeError in voice mode', culprit: 'VoiceCoordinator', count: '4', lastSeen: '2026-10-08T20:00:00Z', status: 'unresolved' }]));
  };
  const value = await createSentrySummary({ fetcher, config });
  assert.equal(value.issues.length, 1);
  assert.match(value.issues[0].url, /https:\/\/sentry\.io\/organizations\/ultra-org\/issues\/123/);
  assert.ok(!JSON.stringify(value).includes(config.sentryToken));
});