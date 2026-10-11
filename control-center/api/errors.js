import { authorized, createSentrySummary } from '../lib/sentry.js';
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'private, no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  if (req.method !== 'GET') return res.status(405).json({ error: 'Método no permitido' });
  const config = {
    sentryToken: process.env.SENTRY_AUTH_TOKEN,
    org: process.env.SENTRY_ORG_SLUG,
    project: process.env.SENTRY_PROJECT_SLUG,
  };
  if (!config.sentryToken || !config.org || !config.project || !process.env.CONTROL_CENTER_ACCESS_KEY) {
    return res.status(200).json({ configured: false, issues: [] });
  }
  if (!authorized(req.headers['x-control-key'], process.env.CONTROL_CENTER_ACCESS_KEY)) {
    return res.status(401).json({ error: 'Clave de acceso requerida' });
  }
  try { return res.status(200).json(await createSentrySummary({ config })); }
  catch { return res.status(503).json({ error: 'Sentry no disponible' }); }
}
