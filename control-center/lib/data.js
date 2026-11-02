export const REPO = 'cardenaspiero255-lang/gamehub-ultra';
const API = `https://api.github.com/repos/${REPO}`;
const WEB = `https://github.com/${REPO}`;

export async function parseGithubResponse(response) {
  if (!response.ok) throw new Error(`GitHub HTTP ${response.status}`);
  try { return await response.json(); }
  catch { throw new Error('GitHub devolvió una respuesta inválida'); }
}

const str = (value, max = 160) => String(value ?? '').slice(0, max);
const num = (value) => Number.isSafeInteger(Number(value)) && Number(value) >= 0 ? Number(value) : null;
const iso = (value) => typeof value === 'string' && !Number.isNaN(Date.parse(value)) ? value : null;

function runInfo(run) {
  const id = num(run.id);
  if (!id) return null;
  return {
    id, name: str(run.name, 100), status: str(run.status, 40),
    conclusion: run.conclusion == null ? null : str(run.conclusion, 40),
    createdAt: iso(run.created_at), branch: str(run.head_branch, 100),
    sha: str(run.head_sha, 12), url: `${WEB}/actions/runs/${id}`,
  };
}

function releaseAssets(releases) {
  const apks = [];
  for (const release of Array.isArray(releases) ? releases : []) {
    for (const asset of Array.isArray(release.assets) ? release.assets : []) {
      if (!/\.apk$/i.test(asset.name ?? '')) continue;
      const download = asset.browser_download_url;
      if (typeof download !== 'string' || !download.startsWith(`${WEB}/releases/download/`)) continue;
      apks.push({
        id: num(asset.id) ?? `${str(release.tag_name)}-${str(asset.name)}`,
        name: str(asset.name, 140), tag: str(release.tag_name, 60),
        size: num(asset.size), publishedAt: iso(release.published_at),
        url: download, kind: 'release',
      });
    }
  }
  return apks;
}

function artifactApks(artifacts) {
  return (Array.isArray(artifacts) ? artifacts : [])
    .filter(a => !a.expired && /apk|android.*debug|android.*release/i.test(a.name ?? ''))
    .filter(a => num(a.workflow_run?.id))
    .slice(0, 5)
    .map(a => ({ id: num(a.id), name: str(a.name, 140), tag: 'Artefacto de CI',
      publishedAt: iso(a.created_at), size: num(a.size_in_bytes),
      url: `${WEB}/actions/runs/${num(a.workflow_run.id)}`, kind: 'artifact',
    }));
}

/** Only public repository metadata is returned; tokens stay server-side. */
export async function createGithubDashboard({ fetcher = fetch, token = process.env.GITHUB_TOKEN } = {}) {
  const headers = { 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'gamehub-ultra-control-center' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const query = async (path) => parseGithubResponse(await fetcher(`${API}${path}`, { headers, signal: AbortSignal.timeout(9000) }));
  const sections = [
    ['pullRequests', '/pulls?state=open&sort=updated&direction=desc&per_page=12'],
    ['recentRuns', '/actions/runs?per_page=25'],
    ['failedRuns', '/actions/runs?status=failure&per_page=8'],
    ['releases', '/releases?per_page=6'],
    ['artifacts', '/actions/artifacts?per_page=45'],
  ];
  const results = await Promise.allSettled(sections.map(([, path]) => query(path)));
  const available = Object.fromEntries(sections.map(([key], index) => [key, results[index].status === 'fulfilled']));
  const value = Object.fromEntries(sections.map(([key], index) => [key, results[index].status === 'fulfilled' ? results[index].value : null]));
  const problems = sections.flatMap(([key], index) => results[index].status === 'rejected'
    ? [`No se pudo consultar ${key}: ${str(results[index].reason?.message ?? 'servicio no disponible', 100)}`]
    : []);
  const prs = Array.isArray(value.pullRequests) ? value.pullRequests : [];
  const runs = Array.isArray(value.recentRuns?.workflow_runs) ? value.recentRuns.workflow_runs : [];
  const failures = Array.isArray(value.failedRuns?.workflow_runs) ? value.failedRuns.workflow_runs : [];
  const apkReleases = releaseAssets(value.releases);
  return {
    repo: REPO, url: WEB, updatedAt: new Date().toISOString(),
    sections: available, problems,
    pullRequests: prs.slice(0, 10).filter(p => num(p.number)).map(p => ({
      number: num(p.number), title: str(p.title, 160), draft: Boolean(p.draft),
      updatedAt: iso(p.updated_at), sha: str(p.head?.sha, 10),
      url: `${WEB}/pull/${num(p.number)}`,
    })),
    recentRuns: runs.slice(0, 12).map(runInfo).filter(Boolean),
    failedRuns: failures.slice(0, 6).map(runInfo).filter(Boolean),
    apks: (apkReleases.length ? apkReleases : artifactApks(value.artifacts?.artifacts)).slice(0, 6),
  };
}