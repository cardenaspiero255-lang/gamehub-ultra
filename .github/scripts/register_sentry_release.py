#!/usr/bin/env python3
"""Register a trusted GameHub Ultra build as a Sentry release.

Runs only on trusted non-PR GitHub Actions events. Secrets are never printed.
The DSN project id is used to discover the matching Sentry project.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API_ROOT = "https://sentry.io/api/0"
MAX_PAGES = 500


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(NoRedirectHandler())


def notice(message):
    print(f"::notice::{message}")


def warning(message):
    print(f"::warning::{message}")


def checked_url(path_or_url):
    if path_or_url.startswith("https://"):
        url = path_or_url
    else:
        url = f"{API_ROOT}{path_or_url}"

    parsed = urllib.parse.urlparse(url)
    if (
        parsed.scheme != "https"
        or parsed.netloc != "sentry.io"
        or not parsed.path.startswith("/api/0/")
    ):
        raise RuntimeError("Refusing unexpected Sentry API URL.")
    return url


def api_request(path_or_url, *, method="GET", payload=None, include_headers=False):
    url = checked_url(path_or_url)
    body = None
    headers = {
        "Authorization": f"Bearer {TOKEN}",
        "Accept": "application/json",
        "User-Agent": "gamehub-ultra-sentry-release",
    }
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=60) as response:
            raw = response.read()
            data = json.loads(raw.decode("utf-8")) if raw else {}
            if include_headers:
                return data, dict(response.headers.items())
            return data
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Sentry API returned HTTP {exc.code} for {urllib.parse.urlparse(url).path}: "
            f"{detail[:1000]}"
        ) from exc


def next_page_url(headers):
    link = headers.get("Link") or headers.get("link") or ""
    for part in link.split(","):
        segment = part.strip()
        if 'rel="next"' not in segment:
            continue
        if 'results="false"' in segment:
            return None
        start = segment.find("<")
        end = segment.find(">", start + 1)
        if start < 0 or end < 0:
            continue
        candidate = segment[start + 1:end]
        return checked_url(candidate)
    return None


def paginated(path):
    url = checked_url(path)
    for _ in range(MAX_PAGES):
        data, headers = api_request(url, include_headers=True)
        if not isinstance(data, list):
            raise RuntimeError("Expected a paginated Sentry list response.")
        for item in data:
            yield item
        url = next_page_url(headers)
        if not url:
            return
    raise RuntimeError(f"Sentry pagination exceeded {MAX_PAGES} pages.")


TOKEN = os.environ.get("SENTRY_AUTH_TOKEN", "").strip()
DSN = os.environ.get("SENTRY_DSN", "").strip()
RELEASE = os.environ.get("SENTRY_RELEASE", "").strip()
REPOSITORY = os.environ.get("GH_REPOSITORY", "").strip()
SHA = os.environ.get("GH_SHA", "").strip()

if not TOKEN or not DSN:
    warning("Sentry auth token or DSN is unavailable; release registration skipped.")
    sys.exit(0)
if not RELEASE or not REPOSITORY or not SHA:
    raise SystemExit("Missing trusted GitHub/Sentry release metadata.")

parsed_dsn = urllib.parse.urlparse(DSN)
if parsed_dsn.scheme != "https" or not parsed_dsn.path.strip("/"):
    raise SystemExit("SENTRY_DSN is not a valid HTTPS project DSN.")
project_id = parsed_dsn.path.rstrip("/").split("/")[-1]
if not project_id.isdigit():
    raise SystemExit("Could not derive the numeric Sentry project id from SENTRY_DSN.")

matches = []
for organization in paginated("/organizations/?per_page=100"):
    org_slug = organization.get("slug")
    if not org_slug:
        continue
    try:
        projects_path = (
            f"/organizations/{urllib.parse.quote(org_slug)}/projects/?per_page=100"
        )
        for project in paginated(projects_path):
            if str(project.get("id", "")) == project_id:
                project_slug = project.get("slug", "")
                if project_slug:
                    matches.append((org_slug, project_slug))
                break
    except RuntimeError as exc:
        warning(f"Could not inspect Sentry organization {org_slug!r}: {exc}")

if len(matches) != 1:
    warning(
        "Could not uniquely discover the Sentry project for this DSN; "
        f"found {len(matches)} matches. Release registration skipped."
    )
    sys.exit(0)

org_slug, project_slug = matches[0]

repo_linked = False
try:
    linked = api_request(
        f"/projects/{urllib.parse.quote(org_slug)}/"
        f"{urllib.parse.quote(project_slug)}/repo/"
    )
    repo_linked = isinstance(linked, list) and any(
        (item.get("name") or item.get("externalSlug") or "") == REPOSITORY
        for item in linked
        if isinstance(item, dict)
    )
except RuntimeError as exc:
    warning(f"Could not verify Sentry/GitHub repository link: {exc}")

payload = {
    "version": RELEASE,
    "projects": [project_slug],
    "ref": SHA,
    "url": f"https://github.com/{REPOSITORY}/commit/{SHA}",
    "status": "open",
}
if repo_linked:
    payload["refs"] = [{"repository": REPOSITORY, "commit": SHA}]
else:
    warning(
        "Sentry project was found, but the GitHub repository link was not "
        "confirmed. Creating the release without commit refs."
    )

release = api_request(
    f"/organizations/{urllib.parse.quote(org_slug)}/releases/",
    method="POST",
    payload=payload,
)

commit_count = release.get("commitCount", 0) if isinstance(release, dict) else 0
notice(
    f"Registered Sentry release {RELEASE} for project {project_slug}; "
    f"GitHub commit linking={'enabled' if repo_linked else 'not confirmed'}, "
    f"commitCount={commit_count}."
)
