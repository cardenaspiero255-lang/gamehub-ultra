#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

API_ROOT = "https://api.github.com"
METRICS_JOB_NAMES = {"metrics", "ci-metrics", "performance-metrics"}
TRACKED_STEPS = {
    "Run fast quality gates",
    "Generate debug unit-test coverage",
    "Build telemetry-disabled release APK and AAB for validation",
    "Wait for API 35 emulator readiness",
    "Validate release APK install-update-uninstall on API 35",
    "Run baseline profile and macrobenchmarks on API 35",
    "Build distributable release APK and AAB with Sentry",
    "Upload debug APK",
    "Upload benchmark reports",
    "Upload installable release outputs",
    "Upload coverage to Codecov",
}


def notice(message: str) -> None:
    print(f"::notice::{message}")


def _parse_time(raw: str | None) -> datetime | None:
    if not raw:
        return None
    return datetime.fromisoformat(raw.replace("Z", "+00:00"))


def _duration_seconds(start: str | None, end: str | None) -> float | None:
    started = _parse_time(start)
    completed = _parse_time(end)
    if started is None or completed is None or completed < started:
        return None
    return round((completed - started).total_seconds(), 3)


def _slug(value: str) -> str:
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "_", value)
    return value.strip("_")


def percentiles(values: Iterable[float]) -> dict[str, float]:
    ordered = sorted(float(v) for v in values)
    if not ordered:
        return {"p50": 0.0, "p90": 0.0, "p95": 0.0}

    def percentile(q: float) -> float:
        if len(ordered) == 1:
            return ordered[0]
        index = (len(ordered) - 1) * q
        lower = math.floor(index)
        upper = math.ceil(index)
        if lower == upper:
            return ordered[lower]
        fraction = index - lower
        return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction

    return {
        "p50": round(percentile(0.50), 3),
        "p90": round(percentile(0.90), 3),
        "p95": round(percentile(0.95), 3),
    }


def extract_metrics(jobs: list[dict[str, Any]]) -> dict[str, float]:
    metrics: dict[str, float] = {}
    core_jobs: list[dict[str, Any]] = []

    for job in jobs:
        name = str(job.get("name") or "").strip()
        if not name or name in METRICS_JOB_NAMES:
            continue
        duration = _duration_seconds(job.get("started_at"), job.get("completed_at"))
        if duration is None:
            continue
        core_jobs.append(job)
        metrics[f"job.{_slug(name)}.seconds"] = duration

        for step in job.get("steps") or []:
            step_name = str(step.get("name") or "").strip()
            if step_name not in TRACKED_STEPS:
                continue
            step_duration = _duration_seconds(
                step.get("started_at"), step.get("completed_at")
            )
            if step_duration is None:
                continue
            metrics[
                f"step.{_slug(name)}.{_slug(step_name)}.seconds"
            ] = step_duration

    starts = [_parse_time(job.get("started_at")) for job in core_jobs]
    ends = [_parse_time(job.get("completed_at")) for job in core_jobs]
    starts = [value for value in starts if value is not None]
    ends = [value for value in ends if value is not None]
    if starts and ends:
        metrics["workflow_wall_clock_seconds"] = round(
            (max(ends) - min(starts)).total_seconds(), 3
        )
    return metrics


def summarize_history(
    history: list[dict[str, float]],
) -> dict[str, dict[str, float | int]]:
    names = sorted({name for row in history for name in row})
    summary: dict[str, dict[str, float | int]] = {}
    for name in names:
        values = [row[name] for row in history if name in row]
        if not values:
            continue
        summary[name] = {
            "samples": len(values),
            **percentiles(values),
        }
    return summary


def render_markdown(
    workflow_name: str,
    current: dict[str, float],
    summary: dict[str, dict[str, float | int]],
) -> str:
    lines = [
        f"## CI performance — {workflow_name}",
        "",
        "Phase 2 measurement is observational only; no validation gate is skipped.",
        "",
        "| Metric | Current (s) | Samples | p50 | p90 | p95 |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    metric_names = sorted(set(current) | set(summary))
    for name in metric_names:
        hist = summary.get(name, {})
        current_value = current.get(name)
        lines.append(
            f"| `{name}` | "
            f"{'' if current_value is None else f'{current_value:.3f}'} | "
            f"{hist.get('samples', 0)} | "
            f"{hist.get('p50', '')} | {hist.get('p90', '')} | {hist.get('p95', '')} |"
        )
    lines.append("")
    return "\n".join(lines)


def _get_json(url: str, token: str) -> dict[str, Any]:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "api.github.com":
        raise RuntimeError("Refusing non-GitHub API URL")
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "gamehub-ultra-ci-performance-metrics",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as exc:
        raise RuntimeError(
            f"GitHub Actions metrics request failed: {type(exc).__name__}"
        ) from exc
    if not isinstance(data, dict):
        raise RuntimeError("Unexpected GitHub API payload")
    return data


def _fetch_jobs(repository: str, run_id: int, token: str) -> list[dict[str, Any]]:
    data = _get_json(
        f"{API_ROOT}/repos/{repository}/actions/runs/{run_id}/jobs?per_page=100",
        token,
    )
    jobs = data.get("jobs") or []
    if not isinstance(jobs, list):
        raise RuntimeError("GitHub jobs payload is not a list")
    return [job for job in jobs if isinstance(job, dict)]


def _fetch_run(repository: str, run_id: int, token: str) -> dict[str, Any]:
    return _get_json(f"{API_ROOT}/repos/{repository}/actions/runs/{run_id}", token)


def _fetch_recent_completed_runs(
    repository: str,
    workflow_id: int,
    token: str,
    history_limit: int,
    current_run_id: int,
) -> list[dict[str, Any]]:
    data = _get_json(
        f"{API_ROOT}/repos/{repository}/actions/workflows/{workflow_id}/runs"
        f"?status=completed&per_page={max(1, min(history_limit + 5, 100))}",
        token,
    )
    runs = data.get("workflow_runs") or []
    if not isinstance(runs, list):
        raise RuntimeError("GitHub workflow runs payload is not a list")
    selected = []
    for run in runs:
        if not isinstance(run, dict):
            continue
        if int(run.get("id") or 0) == current_run_id:
            continue
        if run.get("conclusion") != "success":
            continue
        selected.append(run)
        if len(selected) >= history_limit:
            break
    return selected


def collect(
    *, repository: str, run_id: int, token: str, history_limit: int
) -> dict[str, Any]:
    current_run = _fetch_run(repository, run_id, token)
    workflow_id = int(current_run.get("workflow_id") or 0)
    if workflow_id <= 0:
        raise RuntimeError("Current workflow id is unavailable")

    current_metrics = extract_metrics(_fetch_jobs(repository, run_id, token))
    history_rows: list[dict[str, float]] = []
    for run in _fetch_recent_completed_runs(
        repository, workflow_id, token, history_limit, run_id
    ):
        historical_id = int(run.get("id") or 0)
        if historical_id <= 0:
            continue
        history_rows.append(
            extract_metrics(_fetch_jobs(repository, historical_id, token))
        )

    return {
        "schema_version": 1,
        "repository": repository,
        "workflow_id": workflow_id,
        "workflow_name": str(current_run.get("name") or "unknown"),
        "run_id": run_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "current": current_metrics,
        "history_summary": summarize_history(history_rows),
        "history_runs": len(history_rows),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""))
    parser.add_argument("--history", type=int, default=20)
    parser.add_argument("--json-out", required=True)
    parser.add_argument("--summary-out", required=True)
    args = parser.parse_args(argv)

    json_path = Path(args.json_out)
    summary_path = Path(args.summary_out)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.parent.mkdir(parents=True, exist_ok=True)

    if not args.token:
        payload = {
            "schema_version": 1,
            "status": "unavailable",
            "reason": "missing_token",
        }
        json_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        summary_path.write_text(
            "## CI performance\n\nMetrics unavailable: missing GitHub token.\n",
            encoding="utf-8",
        )
        notice("CI performance metrics skipped because GITHUB_TOKEN is unavailable.")
        return 0

    try:
        payload = collect(
            repository=args.repository,
            run_id=args.run_id,
            token=args.token,
            history_limit=max(1, min(args.history, 50)),
        )
        markdown = render_markdown(
            payload["workflow_name"],
            payload["current"],
            payload["history_summary"],
        )
    except Exception as exc:
        payload = {
            "schema_version": 1,
            "status": "unavailable",
            "reason": type(exc).__name__,
            "message": str(exc),
        }
        markdown = (
            "## CI performance\n\n"
            f"Metrics unavailable for this run: {type(exc).__name__}. "
            "Core CI gates are unaffected.\n"
        )
        notice(f"CI performance metrics unavailable: {exc}")

    json_path.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    summary_path.write_text(markdown, encoding="utf-8")
    print(markdown)
    return 0


if __name__ == "__main__":
    sys.exit(main())
