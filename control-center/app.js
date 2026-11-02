const $ = (id) => document.getElementById(id);
const state = { data: null, filter: 'all' };
const githubRepo = 'https://github.com/cardenaspiero255-lang/gamehub-ultra';

function node(tag, className = '', text = '') {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text) el.textContent = String(text);
  return el;
}
function link(text, url, className = '') {
  const a = node('a', className, text);
  const safeUrl = String(url ?? '');
  if (!/^https:\/\/(?:github\.com|sentry\.io)\//.test(safeUrl)) return node('span', className, text);
  a.href = safeUrl; a.target = '_blank'; a.rel = 'noopener noreferrer';
  return a;
}
function prettyDate(iso) {
  if (!iso || Number.isNaN(Date.parse(iso))) return 'Sin fecha';
  return new Intl.DateTimeFormat('es-CL', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}
function clear(id) { $(id).replaceChildren(); }
function empty(id, text) { clear(id); $(id).append(node('p', 'empty', text)); }
function badge(text, status) {
  const b = node('span', `pill ${status}`, text);
  return b;
}
function kindStatus(run) {
  if (run.status === 'queued' || run.status === 'pending' || run.status === 'in_progress' || run.status === 'requested' || run.status === 'waiting') return ['En curso', 'progress'];
  const labels = { success: 'Aprobado', failure: 'Falló', cancelled: 'Cancelado', skipped: 'Omitido', timed_out: 'Tiempo agotado', action_required: 'Requiere acción', neutral: 'Neutro' };
  return [labels[run.conclusion] ?? 'Desconocido', run.conclusion === 'success' ? 'success' : run.conclusion === 'failure' || run.conclusion === 'timed_out' ? 'failure' : 'neutral'];
}
function renderPrs(data) {
  clear('pr-list');
  if (!data.sections.pullRequests) return empty('pr-list', 'No hay conexión con la API de pull requests de GitHub.');
  if (!data.pullRequests.length) return empty('pr-list', 'No hay PR abiertos.');
  for (const pr of data.pullRequests) {
    const item = node('div', 'pr-item');
    item.append(node('div', 'item-icon', '⑂'));
    const main = node('div', 'item-main');
    main.append(link(`#${pr.number} · ${pr.title}`, pr.url, 'item-title'));
    const meta = node('div', 'item-meta');
    meta.append(node('span', '', prettyDate(pr.updatedAt)));
    meta.append(badge(pr.draft ? 'Draft' : 'Abierto', pr.draft ? 'draft' : 'success'));
    main.append(meta); item.append(main); $('pr-list').append(item);
  }
}
function renderRuns() {
  const data = state.data; if (!data) return;
  const isFailed = state.filter === 'failed';
  const available = data.sections[isFailed ? 'failedRuns' : 'recentRuns'];
  const items = isFailed ? data.failedRuns : data.recentRuns;
  if (!available) return empty('run-list', 'No fue posible consultar las ejecuciones.');
  if (!items.length) return empty('run-list', isFailed ? 'GitHub no devuelve fallos en las últimas ejecuciones consultadas.' : 'No se encontraron ejecuciones recientes.');
  clear('run-list');
  for (const run of items) {
    const el = node('div', 'run-item');
    el.append(link(run.name || 'Workflow sin nombre', run.url, 'run-name'));
    el.append(node('span', 'run-branch', run.branch || '—'));
    const wrapper = node('div'); const [txt, cls] = kindStatus(run); wrapper.append(badge(txt, cls)); el.append(wrapper);
    el.append(node('span', 'run-date', prettyDate(run.createdAt)));
    $('run-list').append(el);
  }
}
function renderApks(data) {
  if (!data.sections.releases && !data.sections.artifacts) return empty('apk-list', 'La API de descargas de GitHub no está disponible.');
  if (!data.apks.length) return empty('apk-list', 'Todavía no hay APK encontradas en releases ni en los artefactos recientes. Revisa el repositorio para descargar desde un workflow anterior.');
  clear('apk-list');
  for (const apk of data.apks) {
    const item = node('div', 'download-item'); item.append(node('div', 'download-icon', '⇩'));
    const detail = node('div', 'download-detail');
    detail.append(node('strong', '', apk.name));
    const size = apk.size == null ? '' : ` · ${(apk.size / 1e6).toFixed(1)} MB`;
    detail.append(node('span', '', `${apk.tag} · ${prettyDate(apk.publishedAt)}${size}`));
    item.append(detail); item.append(link(apk.kind === 'release' ? 'Descargar ↗' : 'Ver run ↗', apk.url, 'download-link'));
    $('apk-list').append(item);
  }
}
function setConnection(kind, message) {
  $('connection').className = `connection ${kind}`;
  $('connection-text').textContent = message;
}
async function loadGithub() {
  $('refresh').disabled = true;
  setConnection('', 'Consultando…');
  try {
    const response = await fetch('/api/status', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    state.data = data;
    $('metric-prs').textContent = data.sections.pullRequests ? data.pullRequests.length : '—';
    $('metric-runs').textContent = data.sections.recentRuns ? data.recentRuns.length : '—';
    $('metric-failures').textContent = data.sections.failedRuns ? data.failedRuns.length : '—';
    $('metric-apks').textContent = data.sections.releases || data.sections.artifacts ? data.apks.length : '—';
    $('metric-prs-note').textContent = 'PR de la consulta más reciente';
    $('last-update').textContent = `Actualizado: ${prettyDate(data.updatedAt)}`;
    renderPrs(data); renderRuns(); renderApks(data);
    $('status-message').hidden = !data.problems.length;
    $('status-message').textContent = data.problems.length ? `Datos parciales: ${data.problems.join(' · ')}` : '';
    setConnection(data.problems.length ? 'error' : 'connected', data.problems.length ? 'Datos parciales' : 'GitHub conectado');
  } catch {
    $('status-message').textContent = 'No se pudieron cargar los datos. Comprueba el despliegue de Vercel o inténtalo de nuevo.';
    $('status-message').hidden = false;
    setConnection('error', 'Sin conexión');
    ['pr-list', 'run-list', 'apk-list'].forEach(id => empty(id, 'No se pudo cargar esta sección.'));
  } finally { $('refresh').disabled = false; }
}
function renderSentry(issues) {
  clear('sentry-body');
  if (!issues.length) return empty('sentry-body', 'No hay incidencias sin resolver entre las últimas consultadas.');
  for (const issue of issues) {
    const item = node('div', 'issue-item');
    item.append(node('div', 'item-icon', '◇'));
    const main = node('div', 'item-main');
    main.append(link(issue.title || `Incidencia ${issue.id}`, issue.url, 'item-title'));
    main.append(node('div', 'item-meta', `${issue.culprit || 'Android'} · ${issue.count} eventos · ${prettyDate(issue.lastSeen)}`));
    item.append(main); $('sentry-body').append(item);
  }
}
async function loadSentry(secret = '') {
  try {
    const headers = secret ? { 'X-Control-Key': secret } : {};
    const res = await fetch('/api/errors', { headers, cache: 'no-store' });
    if (res.status === 401) {
      empty('sentry-body', 'Sentry requiere la clave privada del panel.');
      $('sentry-form').hidden = false;
      return;
    }
    if (!res.ok) throw Error('Servicio no disponible');
    const json = await res.json();
    if (!json.configured) {
      empty('sentry-body', 'Conexión privada pendiente. Configura las variables SENTRY_AUTH_TOKEN, SENTRY_ORG_SLUG, SENTRY_PROJECT_SLUG y CONTROL_CENTER_ACCESS_KEY en Vercel para ver errores reales.');
      $('sentry-form').hidden = true;
      return;
    }
    $('sentry-form').hidden = true;
    $('sentry-key').value = '';
    renderSentry(json.issues);
  } catch {
    empty('sentry-body', 'No fue posible conectar con Sentry. Revisa los permisos del token y vuelve a intentar.');
  }
}
$('refresh').addEventListener('click', () => loadGithub());
for (const [id, value] of [['filter-all', 'all'], ['filter-failed', 'failed']]) {
  $(id).addEventListener('click', () => {
    state.filter = value;
    $('filter-all').classList.toggle('selected', value === 'all');
    $('filter-failed').classList.toggle('selected', value === 'failed');
    $('filter-all').setAttribute('aria-pressed', String(value === 'all'));
    $('filter-failed').setAttribute('aria-pressed', String(value === 'failed'));
    renderRuns();
  });
}
$('sentry-form').addEventListener('submit', event => {
  event.preventDefault();
  const key = $('sentry-key').value;
  if (key) loadSentry(key);
});
loadGithub();
loadSentry();