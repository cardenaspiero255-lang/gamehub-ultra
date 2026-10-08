import { createGithubDashboard } from '../lib/data.js';
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'private, max-age=30, stale-while-revalidate=45');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  if (req.method !== 'GET') return res.status(405).json({ error: 'Método no permitido' });
  try { return res.status(200).json(await createGithubDashboard()); }
  catch { return res.status(503).json({ error: 'No se pudo consultar GitHub' }); }
}
