import test from 'node:test';
import assert from 'node:assert/strict';
import { createGithubDashboard, parseGithubResponse } from '../lib/data.js';

function mockFetch(routes) {
  const called = [];
  const fetcher = async (url, opts) => {
    called.push({ url, opts });
    const key = new URL(url).pathname + new URL(url).search;
    const value = routes[key];
    if (!value) return new Response('{"message":"Not Found"}', { status: 404 });
    return new Response(JSON.stringify(value), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  return { fetcher, called };
}

const repo = 'cardenaspiero255-lang/gamehub-ultra';
const prefix = `/repos/${repo}`;

test('sanitizes GitHub results and avoids inventing APKs', async () => {
  const { fetcher, called } = mockFetch({
    [`${prefix}/pulls?state=open&sort=updated&direction=desc&per_page=12`]: [{ number: 158, title: 'Ultra Frontier V2', draft: true, state: 'open', updated_at: '2026-10-08T12:00:00Z', html_url: 'https://evil.example/fake', head: { sha: 'abcdef12345' } }],
    [`${prefix}/actions/runs?per_page=25`]: { workflow_runs: [{ id: 1234, name: 'Android build', status: 'completed', conclusion: 'failure', created_at: '2026-10-08T13:00:00Z', head_sha: 'abcde12345', head_branch: 'feature/test' }] },
    [`${prefix}/actions/runs?status=failure&per_page=8`]: { workflow_runs: [{ id: 1234, name: 'Android build', status: 'completed', conclusion: 'failure', created_at: '2026-10-08T13:00:00Z' }] },
    [`${prefix}/releases?per_page=6`]: [{ tag_name: 'v0.1', html_url: 'https://github.com/test', assets: [{ name: 'notes.txt', browser_download_url: 'https://evil.example/download' }] }],
    [`${prefix}/actions/artifacts?per_page=45`]: { artifacts: [] },
  });
  const data = await createGithubDashboard({ fetcher, token: 'server-secret' });
  assert.equal(data.repo, repo);
  assert.equal(data.pullRequests[0].url, `https://github.com/${repo}/pull/158`);
  assert.equal(data.recentRuns[0].url, `https://github.com/${repo}/actions/runs/1234`);
  assert.equal(data.failedRuns.length, 1);
  assert.equal(data.apks.length, 0);
  assert.deepEqual(data.problems, []);
  assert.ok(called.every(({ opts }) => opts.headers.Authorization === 'Bearer server-secret'));
  assert.ok(!JSON.stringify(data).includes('server-secret'));
  assert.ok(!JSON.stringify(data).includes('evil.example'));
});

test('reports partial availability without inventing fake success states', async () => {
  const { fetcher } = mockFetch({
    [`${prefix}/pulls?state=open&sort=updated&direction=desc&per_page=12`]: [],
  });
  const data = await createGithubDashboard({ fetcher });
  assert.equal(data.pullRequests.length, 0);
  assert.equal(data.recentRuns.length, 0);
  assert.ok(data.problems.length >= 1);
  assert.equal(data.sections.recentRuns, false);
});

test('rejects non-OK responses and malformed JSON payloads', async () => {
  await assert.rejects(() => parseGithubResponse(new Response('{}', { status: 403 })), /GitHub HTTP 403/);
  await assert.rejects(() => parseGithubResponse(new Response('{bad}', { status: 200 })), /respuesta inválida/);
});