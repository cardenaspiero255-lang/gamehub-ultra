import { timingSafeEqual } from 'node:crypto';
const asString = (v, max = 180) => String(v ?? '').slice(0, max);
export function authorized(presented, expected) {
  if (typeof presented !== 'string' || !expected || typeof expected !== 'string') return false;
  const a = Buffer.from(presented), b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}
export async function createSentrySummary({ fetcher = fetch, config } = {}) {
  if (!config?.sentryToken || !config.org || !config.project) {
    return { configured: false, issues: [] };
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(config.org) || !/^[a-zA-Z0-9_-]+$/.test(config.project)) {
    throw new Error('Configuración de Sentry inválida');
  }
  const url = `https://sentry.io/api/0/projects/${config.org}/${config.project}/issues/?query=is:unresolved&limit=8`;
  const res = await fetcher(url, {
    headers: { Authorization: `Bearer ${config.sentryToken}`, Accept: 'application/json' },
    signal: AbortSignal.timeout(9000),
  });
  if (!res.ok) throw new Error(`Sentry HTTP ${res.status}`);
  const issues = await res.json();
  return { configured: true, issues: (Array.isArray(issues) ? issues : []).slice(0, 8)
    .filter(issue => /^\d+$/.test(String(issue.id ?? '')))
    .map(issue => ({
      id: String(issue.id), title: asString(issue.title, 190), culprit: asString(issue.culprit, 90),
      count: Number(issue.count) || 0, lastSeen: typeof issue.lastSeen === 'string' ? issue.lastSeen : null,
      url: `https://sentry.io/organizations/${config.org}/issues/${issue.id}/`,
    })),
  };
}