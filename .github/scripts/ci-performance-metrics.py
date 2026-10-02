#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

API_ROOT = "https://api.github.com"
MAX_HISTORY_JOB_REQUESTS = 100
METRICS_JOB_NAMES = {"metrics", "ci-metrics", "performance-metrics"}
class GitHubApiError(RuntimeError):
    """GitHub API request failed with a safe diagnostic."""

    def __init__(self, status: int, detail: str):
        self.status = status
        super().__init__(f"GitHub Actions metrics request failed with HTTP {status}: {detail}")


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

    aggregate = next((job for job in core_jobs if str(job.get("name") or "").strip() == "build"), None)
    if aggregate is not None:
        aggregate_start = _parse_time(aggregate.get("started_at"))
        aggregate_end = _parse_time(aggregate.get("completed_at"))
        dependencies = [
            job
            for job in core_jobs
            if str(job.get("name") or "").strip() in {"quality", "device-validation"}
        ]
        dependency_ends = [_parse_time(job.get("completed_at")) for job in dependencies]
        dependency_ends = [value for value in dependency_ends if value is not None]
        if aggregate_start is not None and aggregate_end is not None and dependency_ends:
            dependency_ready = max(dependency_ends)
            queue_seconds = max(0.0, (aggregate_start - dependency_ready).total_seconds())
            runtime_seconds = max(0.0, (aggregate_end - aggregate_start).total_seconds())
            metrics["aggregate_gate_queue_seconds"] = round(queue_seconds, 3)
            metrics["aggregate_gate_runtime_seconds"] = round(runtime_seconds, 3)
            metrics["aggregate_gate_overhead_seconds"] = round(
                queue_seconds + runtime_seconds, 3
            )

    starts = [_parse_time(job.get("started_at")) for job in core_jobs]
    ends = [_parse_time(job.get("completed_at")) for job in core_jobs]
    starts = [value for value in starts if value is not None]
    ends = [value for value in ends if value is not None]
    if starts and ends:
        metrics["workflow_wall_clock_seconds"] = round(
            (max(ends) - min(starts)).total_seconds(), 3
        )
    return metrics


def _job_topology(jobs: list[dict[str, Any]]) -> tuple[str, ...]:
    names = []
    for job in jobs:
        name = str(job.get("name") or "").strip()
        if not name or name in METRICS_JOB_NAMES:
            continue
        names.append(name)
    return tuple(sorted(names))


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
    history_partial: bool = False,
) -> str:
    lines = [
        f"## CI performance — {workflow_name}",
        "",
        "Phase 2 measurement is observational only; no validation gate is skipped.",
        "",
    ]
    if history_partial:
        lines.extend(
            [
                "History status: PARTIAL — the historical job-request budget was reached; "
                "percentiles use the compatible samples collected so far.",
                "",
            ]
        )
    lines.extend(
        [
            "| Metric | Current (s) | Samples | p50 | p90 | p95 |",
            "|---|---:|---:|---:|---:|---:|",
        ]
    )
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


def _safe_http_error_detail(exc: urllib.error.HTTPError) -> str:
    try:
        raw = exc.read().decode("utf-8", errors="replace")
    except Exception:
        raw = ""
    detail = ""
    if raw:
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = None
        if isinstance(payload, dict):
            detail = str(payload.get("message") or "").strip()
        if not detail:
            detail = re.sub(r"\s+", " ", raw).strip()
    if not detail:
        detail = str(exc.reason or "request rejected")
    return detail[:300]


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
    except urllib.error.HTTPError as exc:
        raise GitHubApiError(exc.code, _safe_http_error_detail(exc)) from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        reason = getattr(exc, "reason", None)
        detail = str(reason or type(exc).__name__)
        raise RuntimeError(
            f"GitHub Actions metrics request failed before a response: {detail}"
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




def validate_parent_provenance(
    run: dict[str, Any],
    *,
    expected_run_id: int | None = None,
    expected_head_sha: str | None = None,
    expected_workflow: str | None = None,
    require_completed: bool = False,
) -> None:
    """Fail closed when a post-run collector is not bound to the expected parent."""
    actual_run_id = int(run.get("id") or 0)
    if expected_run_id is not None and actual_run_id != expected_run_id:
        raise RuntimeError(
            f"Parent run id mismatch: expected {expected_run_id}, got {actual_run_id}"
        )

    actual_head_sha = str(run.get("head_sha") or "").strip()
    if expected_head_sha is not None and actual_head_sha != expected_head_sha:
        raise RuntimeError("Parent head SHA does not match the workflow_run event")

    actual_workflow = str(run.get("name") or "").strip()
    if expected_workflow is not None and actual_workflow != expected_workflow:
        raise RuntimeError(
            f"Parent workflow mismatch: expected {expected_workflow!r}, got {actual_workflow!r}"
        )

    if require_completed and str(run.get("status") or "").strip() != "completed":
        raise RuntimeError("Parent workflow run is not completed")


def _required_jobs_are_completed(
    jobs: list[dict[str, Any]], required_job_names: Iterable[str]
) -> bool:
    for required_name in required_job_names:
        matches = [
            job
            for job in jobs
            if str(job.get("name") or "").strip() == required_name
        ]
        if not matches:
            return False
        for job in matches:
            if not job.get("completed_at"):
                return False
            status = str(job.get("status") or "").strip()
            if status and status != "completed":
                return False
    return True


def validate_required_jobs(
    jobs: list[dict[str, Any]], required_job_names: Iterable[str]
) -> None:
    names = tuple(dict.fromkeys(name.strip() for name in required_job_names if name.strip()))
    if not names:
        raise RuntimeError("At least one required metrics job must be configured")
    if not _required_jobs_are_completed(jobs, names):
        raise RuntimeError(
            "Required parent jobs are missing or incomplete: " + ", ".join(names)
        )

def _run_cohort(run: dict[str, Any]) -> tuple[str, str]:
    event = str(run.get("event") or "").strip()
    if event == "pull_request":
        pull_requests = run.get("pull_requests") or []
        if isinstance(pull_requests, list):
            for pull_request in pull_requests:
                if not isinstance(pull_request, dict):
                    continue
                base = pull_request.get("base") or {}
                if isinstance(base, dict):
                    ref = str(base.get("ref") or "").strip()
                    if ref:
                        return event, f"base:{ref}"
        return event, "base:unknown"

    head_branch = str(run.get("head_branch") or "").strip()
    return event, f"head:{head_branch or 'unknown'}"


def _iter_recent_completed_runs(
    repository: str,
    workflow_id: int,
    token: str,
    current_run: dict[str, Any],
):
    current_run_id = int(current_run.get("id") or 0)
    current_cohort = _run_cohort(current_run)
    per_page = 100

    for page in range(1, 21):
        data = _get_json(
            f"{API_ROOT}/repos/{repository}/actions/workflows/{workflow_id}/runs"
            f"?status=completed&per_page={per_page}&page={page}",
            token,
        )
        runs = data.get("workflow_runs") or []
        if not isinstance(runs, list):
            raise RuntimeError("GitHub workflow runs payload is not a list")

        for run in runs:
            if not isinstance(run, dict):
                continue
            if int(run.get("id") or 0) == current_run_id:
                continue
            if run.get("conclusion") != "success":
                continue
            if _run_cohort(run) != current_cohort:
                continue
            yield run

        if len(runs) < per_page:
            break


def _fetch_recent_completed_runs(
    repository: str,
    workflow_id: int,
    token: str,
    history_limit: int,
    current_run: dict[str, Any],
) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for run in _iter_recent_completed_runs(
        repository=repository,
        workflow_id=workflow_id,
        token=token,
        current_run=current_run,
    ):
        selected.append(run)
        if len(selected) >= history_limit:
            break
    return selected


def collect(
    *,
    repository: str,
    run_id: int,
    token: str,
    history_limit: int,
    history_job_request_budget: int = MAX_HISTORY_JOB_REQUESTS,
    current_job_wait_seconds: int = 60,
    current_job_poll_seconds: int = 2,
    expected_run_id: int | None = None,
    expected_head_sha: str | None = None,
    expected_workflow: str | None = None,
    require_completed: bool = False,
    required_job_names: Iterable[str] | None = None,
) -> dict[str, Any]:
    current_run = _fetch_run(repository, run_id, token)
    validate_parent_provenance(
        current_run,
        expected_run_id=expected_run_id,
        expected_head_sha=expected_head_sha,
        expected_workflow=expected_workflow,
        require_completed=require_completed,
    )
    workflow_id = int(current_run.get("workflow_id") or 0)
    if workflow_id <= 0:
        raise RuntimeError("Current workflow id is unavailable")

    if required_job_names is None:
        workflow_name = str(current_run.get("name") or "").strip()
        required_jobs = (
            ("coverage",)
            if workflow_name == "Unit Test Coverage"
            else ("build",)
        )
    else:
        required_jobs = tuple(
            dict.fromkeys(name.strip() for name in required_job_names if name.strip())
        )
    if not required_jobs:
        raise RuntimeError("No required jobs configured for metrics collection")

    deadline = time.monotonic() + max(0, current_job_wait_seconds)
    while True:
        current_jobs = _fetch_jobs(repository, run_id, token)
        if _required_jobs_are_completed(current_jobs, required_jobs):
            break
        if require_completed:
            validate_required_jobs(current_jobs, required_jobs)
        if time.monotonic() >= deadline:
            raise RuntimeError(
                "Required parent jobs did not complete before metrics sampling timeout: "
                + ", ".join(required_jobs)
            )
        time.sleep(max(1, current_job_poll_seconds))

    current_metrics = extract_metrics(current_jobs)
    current_topology = _job_topology(current_jobs)

    history_rows: list[dict[str, float]] = []
    history_job_requests = 0
    history_partial = False
    safe_budget = max(1, history_job_request_budget)

    for run in _iter_recent_completed_runs(
        repository=repository,
        workflow_id=workflow_id,
        token=token,
        current_run=current_run,
    ):
        if history_job_requests >= safe_budget:
            history_partial = True
            break

        historical_id = int(run.get("id") or 0)
        if historical_id <= 0:
            continue

        history_job_requests += 1
        historical_jobs = _fetch_jobs(repository, historical_id, token)
        if _job_topology(historical_jobs) == current_topology:
            history_rows.append(extract_metrics(historical_jobs))
            if len(history_rows) >= history_limit:
                break
        if history_job_requests >= safe_budget:
            history_partial = True
            break

    return {
        "schema_version": 1,
        "repository": repository,
        "workflow_id": workflow_id,
        "workflow_name": str(current_run.get("name") or "unknown"),
        "run_id": run_id,
        "provenance": {
            "run_id": int(current_run.get("id") or run_id),
            "head_sha": str(current_run.get("head_sha") or ""),
            "workflow": str(current_run.get("name") or "unknown"),
            "status": str(current_run.get("status") or ""),
        },
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "current": current_metrics,
        "history_summary": summarize_history(history_rows),
        "history_runs": len(history_rows),
        "history_partial": history_partial,
        "history_job_requests": history_job_requests,
        "history_job_request_budget": safe_budget,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True, type=int)
    parser.add_argument("--expected-run-id", type=int)
    parser.add_argument("--expected-head-sha")
    parser.add_argument("--expected-workflow")
    parser.add_argument("--require-completed", action="store_true")
    parser.add_argument("--require-job", action="append", dest="required_jobs")
    parser.add_argument("--fail-on-unavailable", action="store_true")
    parser.add_argument("--token", default=os.environ.get("GITHUB_TOKEN", ""))
    parser.add_argument("--history", type=int, default=20)
    parser.add_argument(
        "--history-job-request-budget",
        type=int,
        default=MAX_HISTORY_JOB_REQUESTS,
    )
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
        return 1 if args.fail_on_unavailable else 0

    exit_code = 0
    try:
        payload = collect(
            repository=args.repository,
            run_id=args.run_id,
            token=args.token,
            history_limit=max(1, min(args.history, 50)),
            history_job_request_budget=max(
                1,
                min(args.history_job_request_budget, 500),
            ),
            expected_run_id=args.expected_run_id,
            expected_head_sha=args.expected_head_sha,
            expected_workflow=args.expected_workflow,
            require_completed=args.require_completed,
            required_job_names=args.required_jobs,
        )
        markdown = render_markdown(
            payload["workflow_name"],
            payload["current"],
            payload["history_summary"],
            history_partial=bool(payload.get("history_partial")),
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
        if args.fail_on_unavailable:
            exit_code = 1

    json_path.write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    summary_path.write_text(markdown, encoding="utf-8")
    print(markdown)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
