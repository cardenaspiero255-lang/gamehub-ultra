#!/usr/bin/env python3
"""Register a trusted GameHub Ultra build as a Sentry release.

This runs only on trusted non-PR GitHub Actions events. It never prints the
Sentry token or DSN. The DSN's numeric project id is used only to discover the
matching Sentry project, so no SENTRY_ORG/SENTRY_PROJECT repository variables
are required.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API_ROOT = "https://sentry.io/api/0"


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(NoRedirectHandler())


def notice(message):
    print(f"::notice::{message}")


def warning(message):
    print(f"::warning::{message}")


def api_request(path, *, method="GET", payload=None):
    url = f"{API_ROOT}{path}"
    if not url.startswith("https://sentry.io/"):
        raise RuntimeError("Refusing unexpected Sentry API host.")

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
            return json.loads(raw.decode("utf-8")) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Sentry API returned HTTP {exc.code} for {path}: {detail[:1000]}"
        ) from exc


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

organizations = api_request("/organizations/?per_page=100")
matches = []
for organization in organizations:
    org_slug = organization.get("slug")
    if not org_slug:
        continue
    try:
        projects = api_request(
            f"/organizations/{urllib.parse.quote(org_slug)}/projects/?per_page=100"
        )
    except RuntimeError as exc:
        warning(f"Could not inspect Sentry organization {org_slug!r}: {exc}")
        continue

    for project in projects:
        if str(project.get("id", "")) == project_id:
            matches.append((org_slug, project.get("slug", "")))

matches = [(org, project) for org, project in matches if project]
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
    repo_linked = any(
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

commit_count = release.get("commitCount", 0)
notice(
    f"Registered Sentry release {RELEASE} for project {project_slug}; "
    f"GitHub commit linking={'enabled' if repo_linked else 'not confirmed'}, "
    f"commitCount={commit_count}."
)
