#!/usr/bin/env python3
"""Discover the GameHub Ultra SonarQube Cloud project without hiding outages."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

ALLOWED_HOSTS = (
    "https://sonarcloud.io",
    "https://sonarqube.us",
)
DEFAULT_TIMEOUT_SECONDS = 45
MAX_PAGES = 100


class SonarRequestError(RuntimeError):
    """A Sonar API request failed before a trustworthy answer was obtained."""

    def __init__(self, message: str, *, status: int | None = None):
        super().__init__(message)
        self.status = status


def notice(message: str) -> None:
    print(f"::notice::{message}")


def error(message: str) -> None:
    print(f"::error::{message}")


def write_outputs(
    values: Mapping[str, str],
    *,
    output_path: str | Path | None = None,
) -> None:
    """Append newline-terminated GitHub Actions output records."""
    destination = Path(output_path or os.environ.get("GITHUB_OUTPUT", "").strip())
    if not str(destination):
        raise RuntimeError("GITHUB_OUTPUT is unavailable; cannot publish Sonar discovery outputs.")
    with destination.open("a", encoding="utf-8") as out:
        for key, value in values.items():
            out.write(f"{key}={value}\n")


def get_json(
    url: str,
    *,
    token: str,
    urlopen: Callable[..., Any] = urllib.request.urlopen,
) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "User-Agent": "gamehub-ultra-sonar-discovery",
        },
    )
    try:
        with urlopen(request, timeout=DEFAULT_TIMEOUT_SECONDS) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raise SonarRequestError(
            f"Sonar API request failed with HTTP {exc.code}: "
            f"{urllib.parse.urlparse(url).path}",
            status=exc.code,
        ) from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise SonarRequestError(
            f"Sonar API request failed before a response was received: "
            f"{urllib.parse.urlparse(url).path} ({type(exc).__name__})"
        ) from exc

    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise SonarRequestError(
            f"Sonar API returned invalid JSON for {urllib.parse.urlparse(url).path}."
        ) from exc
    if not isinstance(data, dict):
        raise SonarRequestError(
            f"Sonar API returned an unexpected payload for "
            f"{urllib.parse.urlparse(url).path}."
        )
    return data


def get_all_pages(
    base_url: str,
    item_key: str,
    *,
    token: str,
    urlopen: Callable[..., Any] = urllib.request.urlopen,
    max_pages: int = MAX_PAGES,
) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        separator = "&" if "?" in base_url else "?"
        data = get_json(
            f"{base_url}{separator}p={page}",
            token=token,
            urlopen=urlopen,
        )
        batch = data.get(item_key, [])
        if not isinstance(batch, list):
            raise RuntimeError(
                f"Unexpected Sonar response: {item_key!r} is not a list."
            )
        items.extend(item for item in batch if isinstance(item, dict))

        paging = data.get("paging") or {}
        total = int(paging.get("total") or 0)
        page_size = int(paging.get("pageSize") or 50)
        if not batch:
            break
        if total and page * page_size >= total:
            break
        if not total and len(batch) < page_size:
            break
    else:
        raise RuntimeError(
            f"Sonar pagination exceeded {max_pages} pages for {item_key}."
        )
    return items


def select_host(
    hosts: Sequence[str],
    *,
    token: str,
    urlopen: Callable[..., Any] = urllib.request.urlopen,
) -> str | None:
    """Return a host that accepts the token, or None for confirmed rejection.

    Transient/network/server failures are not treated as invalid credentials.
    They fail closed if no other configured host returns a trustworthy answer.
    """
    request_failures: list[str] = []
    for host in hosts:
        try:
            data = get_json(
                f"{host}/api/authentication/validate",
                token=token,
                urlopen=urlopen,
            )
        except SonarRequestError as exc:
            if exc.status in (401, 403):
                continue
            request_failures.append(f"{host}: {exc}")
            continue

        if data.get("valid") is True:
            return host

    if request_failures:
        raise RuntimeError(
            "Sonar host validation was inconclusive because one or more "
            "requests failed: " + "; ".join(request_failures)
        )
    return None


def discover(
    *,
    token: str,
    repo_name: str,
    repo_owner: str,
    project_override: str = "",
    org_override: str = "",
    host_override: str = "",
    urlopen: Callable[..., Any] = urllib.request.urlopen,
) -> dict[str, str]:
    """Discover an accessible Sonar project with fail-closed request handling."""
    host_override = host_override.strip().rstrip("/")
    if host_override and host_override not in ALLOWED_HOSTS:
        raise RuntimeError(
            "SONAR_HOST_URL must be https://sonarcloud.io or https://sonarqube.us."
        )
    hosts = (host_override,) if host_override else ALLOWED_HOSTS

    selected_host = select_host(hosts, token=token, urlopen=urlopen)
    if not selected_host:
        return {"found": "false"}

    project_key = project_override.strip()
    organization = org_override.strip()
    if project_key and organization:
        return {
            "host": selected_host,
            "region_arg": (
                "-Dsonar.region=us"
                if selected_host == "https://sonarqube.us"
                else ""
            ),
            "found": "true",
            "project_key": project_key,
            "organization": organization,
        }

    if organization:
        organizations = [organization]
    else:
        org_items = get_all_pages(
            f"{selected_host}/api/organizations/search?member=true&ps=50",
            "organizations",
            token=token,
            urlopen=urlopen,
        )
        organizations = [
            item.get("key", "")
            for item in org_items
            if isinstance(item.get("key"), str) and item.get("key")
        ]

    # A successful membership query with zero organizations is authoritative.
    # Do not guess that the GitHub owner is also a Sonar organization: that
    # produced the false HTTP error seen in run #16.
    if not organizations:
        return {"host": selected_host, "found": "false"}

    query = urllib.parse.quote(project_key or repo_name)
    matches: list[tuple[str, str]] = []
    for candidate_org in organizations:
        organization_param = urllib.parse.quote(candidate_org)
        components = get_all_pages(
            f"{selected_host}/api/components/search"
            f"?qualifiers=TRK&q={query}&ps=50"
            f"&organization={organization_param}",
            "components",
            token=token,
            urlopen=urlopen,
        )

        if project_key:
            exact = [
                component
                for component in components
                if component.get("key", "") == project_key
            ]
        else:
            exact = [
                component
                for component in components
                if str(component.get("name", "")).lower() == repo_name.lower()
                or str(component.get("key", "")).lower().endswith(
                    "_" + repo_name.lower()
                )
                or str(component.get("key", "")).lower().endswith(
                    ":" + repo_name.lower()
                )
            ]

        for component in exact:
            component_key = str(component.get("key", "")).strip()
            component_org = str(
                component.get("organization", "") or candidate_org
            ).strip()
            if component_key and component_org:
                matches.append((component_org, component_key))

    matches = list(dict.fromkeys(matches))
    if len(matches) > 1:
        candidates = ", ".join(f"{org}/{key}" for org, key in matches)
        raise RuntimeError(
            "Multiple Sonar projects match this repository name. "
            "Refusing to guess the target project. Set repository variables "
            "SONAR_PROJECT_KEY and SONAR_ORGANIZATION explicitly. "
            f"Candidates: {candidates}"
        )
    if not matches:
        return {"host": selected_host, "found": "false"}

    organization, project_key = matches[0]
    return {
        "host": selected_host,
        "region_arg": (
            "-Dsonar.region=us"
            if selected_host == "https://sonarqube.us"
            else ""
        ),
        "found": "true",
        "project_key": project_key,
        "organization": organization,
    }


def main(
    *,
    env: Mapping[str, str] | None = None,
    urlopen: Callable[..., Any] = urllib.request.urlopen,
) -> int:
    env = os.environ if env is None else env
    output_path = env.get("GITHUB_OUTPUT", "").strip()
    token = env.get("SONAR_TOKEN", "").strip()
    repo_name = env.get("REPO_NAME", "").strip()
    repo_owner = env.get("REPO_OWNER", "").strip()

    if not output_path:
        error("GITHUB_OUTPUT is unavailable; Sonar discovery cannot publish results.")
        return 1
    if not token:
        write_outputs({"found": "false"}, output_path=output_path)
        notice("SONAR_TOKEN is unavailable; Sonar analysis is disabled for this run.")
        return 0
    if not repo_name or not repo_owner:
        error("Repository metadata is unavailable for Sonar discovery.")
        return 1

    try:
        result = discover(
            token=token,
            repo_name=repo_name,
            repo_owner=repo_owner,
            project_override=env.get("SONAR_PROJECT_KEY_OVERRIDE", ""),
            org_override=env.get("SONAR_ORGANIZATION_OVERRIDE", ""),
            host_override=env.get("SONAR_HOST_URL_OVERRIDE", ""),
            urlopen=urlopen,
        )
        write_outputs(result, output_path=output_path)
    except Exception as exc:
        error(str(exc))
        return 1

    if result.get("found") == "true":
        notice(
            f"Sonar project discovered for {repo_owner}/{repo_name}: "
            f"{result['organization']}/{result['project_key']}"
        )
    elif result.get("host"):
        notice(
            "SONAR_TOKEN is valid, but no accessible GameHub Ultra Sonar "
            "project was found; analysis is disabled for this run."
        )
    else:
        notice(
            "SONAR_TOKEN was rejected by the configured SonarQube Cloud "
            "hosts; analysis is disabled for this run."
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
