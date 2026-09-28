#!/usr/bin/env python3
"""Discover Sentry project metadata and register trusted GameHub Ultra releases.

This script runs only on trusted non-PR GitHub Actions events. Secrets are never
printed. Use --discover-only before the distributable Android build to export
SENTRY_ORG/SENTRY_PROJECT and enable the official Sentry Gradle mapping upload.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API_ROOT = "https://sentry.io/api/0"
MAX_PAGES = 500
DISCOVER_ONLY = "--discover-only" in sys.argv


class SentryAuthRejected(RuntimeError):
    """Authentication or authorization rejection from the Sentry API."""


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
        if exc.code in (401, 403):
            raise SentryAuthRejected(
                f"Sentry API rejected the configured auth token with HTTP {exc.code}."
            ) from exc
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
        return checked_url(segment[start + 1:end])
    return None


def paginated(path):
    url = checked_url(path)
    for _ in range(MAX_PAGES):
        data, headers = api_request(url, include_headers=True)
        if not isinstance(data, list):
            raise RuntimeError("Expected a paginated Sentry list response.")
        yield from data
        url = next_page_url(headers)
        if not url:
            return
    raise RuntimeError(f"Sentry pagination exceeded {MAX_PAGES} pages.")


def discover_project(project_id):
    configured_org = os.environ.get("SENTRY_ORG", "").strip()
    configured_project = os.environ.get("SENTRY_PROJECT", "").strip()
    if configured_org and configured_project:
        return configured_org, configured_project

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

    matches = list(dict.fromkeys(matches))
    if len(matches) != 1:
        raise RuntimeError(
            "Could not uniquely discover the Sentry project for this DSN; "
            f"found {len(matches)} matches."
        )
    return matches[0]


TOKEN = os.environ.get("SENTRY_AUTH_TOKEN", "").strip()
DSN = os.environ.get("SENTRY_DSN", "").strip()
RELEASE = os.environ.get("SENTRY_RELEASE", "").strip()
REPOSITORY = os.environ.get("GH_REPOSITORY", "").strip()
SHA = os.environ.get("GH_SHA", "").strip()

if not TOKEN or not DSN:
    warning("Sentry auth token or DSN is unavailable; Sentry release work skipped.")
    sys.exit(0)

parsed_dsn = urllib.parse.urlparse(DSN)
if parsed_dsn.scheme != "https" or not parsed_dsn.path.strip("/"):
    raise SystemExit("SENTRY_DSN is not a valid HTTPS project DSN.")
project_id = parsed_dsn.path.rstrip("/").split("/")[-1]
if not project_id.isdigit():
    raise SystemExit("Could not derive the numeric Sentry project id from SENTRY_DSN.")

try:
    org_slug, project_slug = discover_project(project_id)
except SentryAuthRejected as exc:
    notice(
        f"{exc} Trusted release registration was skipped cleanly; "
        "runtime telemetry through SENTRY_DSN remains enabled."
    )
    sys.exit(0)

if DISCOVER_ONLY:
    github_env = os.environ.get("GITHUB_ENV", "").strip()
    if not github_env:
        raise SystemExit("GITHUB_ENV is unavailable; cannot export Sentry build metadata.")
    with open(github_env, "a", encoding="utf-8") as out:
        out.write(f"SENTRY_ORG={org_slug}\n")
        out.write(f"SENTRY_PROJECT={project_slug}\n")
        out.write("SENTRY_ENABLE_MAPPING_UPLOAD=true\n")
    notice(
        f"Discovered Sentry project {org_slug}/{project_slug}; "
        "trusted release mapping upload enabled."
    )
    sys.exit(0)

if not RELEASE or not REPOSITORY or not SHA:
    raise SystemExit("Missing trusted GitHub/Sentry release metadata.")

repo_linked = False
try:
    linked = api_request(
        f"/projects/{urllib.parse.quote(org_slug)}/"
        f"{urllib.parse.quote(project_slug)}/repo/"
    )
    repo_linked = isinstance(linked, list) and any(
        item.get("name") == REPOSITORY or item.get("externalSlug") == REPOSITORY
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

try:
    release = api_request(
        f"/organizations/{urllib.parse.quote(org_slug)}/releases/",
        method="POST",
        payload=payload,
    )
except SentryAuthRejected as exc:
    notice(
        f"{exc} Trusted release registration was skipped cleanly; "
        "runtime telemetry through SENTRY_DSN remains enabled."
    )
    sys.exit(0)

commit_count = release.get("commitCount", 0) if isinstance(release, dict) else 0
notice(
    f"Registered Sentry release {RELEASE} for project {project_slug}; "
    f"GitHub commit linking={'enabled' if repo_linked else 'not confirmed'}, "
    f"commitCount={commit_count}."
)
