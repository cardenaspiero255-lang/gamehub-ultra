#!/usr/bin/env python3
"""Select a trusted, completed main-branch Coverage run for the exact SHA."""
from __future__ import annotations

import json
import os
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def select_run(runs: list[dict], sha: str, repository: str) -> int | None:
    matches = []
    for item in runs:
        origin = item.get("head_repository") or {}
        if (item.get("head_sha") != sha or item.get("head_branch") != "main" or
            origin.get("full_name") != repository or
            item.get("event") not in {"push", "workflow_dispatch"}):
            continue
        matches.append(item)
    if not matches:
        return None
    # CAR-29: historical success cannot overrule a newer failed or pending run.
    newest = max(matches, key=lambda item: int(item["id"]))
    if newest.get("status") != "completed":
        return None
    if newest.get("conclusion") != "success":
        raise RuntimeError("Latest exact-SHA Coverage failed or was cancelled; refusing Sonar import")
    return int(newest["id"])


def main() -> int:
    token = os.environ.get("GH_COVERAGE_TOKEN", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    sha = os.environ.get("GITHUB_SHA", "")
    output = os.environ.get("GITHUB_OUTPUT", "")
    if (not token or not output or len(sha) != 40 or not repository or "/" not in repository):
        raise SystemExit("Missing trusted GitHub metadata for exact-SHA Coverage lookup")
    base = f"https://api.github.com/repos/{repository}/actions/workflows/coverage.yml/runs"
    params = urlencode({"head_sha": sha, "branch": "main", "per_page": 100})
    deadline = time.monotonic() + 1140
    while True:
        req = Request(f"{base}?{params}", headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "gamehub-ultra-sonar-coverage-provenance",
        })
        with urlopen(req, timeout=30) as response:
            payload = json.load(response)
        runs = payload.get("workflow_runs")
        if not isinstance(runs, list):
            raise RuntimeError("GitHub response missing workflow_runs")
        run_id = select_run(runs, sha, repository)
        if run_id is not None:
            with open(output, "a", encoding="utf-8") as out:
                out.write(f"run_id={run_id}\n")
            print(f"Exact-SHA trusted main Coverage complete, run #{run_id}")
            return 0
        if time.monotonic() >= deadline:
            raise RuntimeError("Timed out waiting for successful exact-SHA main Coverage")
        print("Waiting for verified main Coverage of the current commit...", flush=True)
        time.sleep(15)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"::error::{type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(1)
