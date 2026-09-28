#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import unittest
import urllib.error
from io import BytesIO
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / ".github/scripts/ci-performance-metrics.py"

spec = importlib.util.spec_from_file_location("ci_metrics", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError(f"Unable to load {MODULE_PATH}")
metrics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metrics)


class CiPerformanceMetricsTests(unittest.TestCase):
    def test_percentiles_are_stable(self):
        values = [10, 20, 30, 40, 50]
        self.assertEqual(
            metrics.percentiles(values),
            {"p50": 30.0, "p90": 46.0, "p95": 48.0},
        )

    def test_extract_metrics_excludes_metrics_job_and_tracks_steps(self):
        jobs = [
            {
                "name": "quality",
                "started_at": "2026-09-28T05:00:00Z",
                "completed_at": "2026-09-28T05:02:00Z",
                "steps": [
                    {
                        "name": "Run fast quality gates",
                        "started_at": "2026-09-28T05:00:30Z",
                        "completed_at": "2026-09-28T05:01:50Z",
                    }
                ],
            },
            {
                "name": "device-validation",
                "started_at": "2026-09-28T05:00:05Z",
                "completed_at": "2026-09-28T05:10:00Z",
                "steps": [
                    {
                        "name": "Wait for API 35 emulator readiness",
                        "started_at": "2026-09-28T05:04:00Z",
                        "completed_at": "2026-09-28T05:05:00Z",
                    },
                    {
                        "name": "Validate release APK install-update-uninstall on API 35",
                        "started_at": "2026-09-28T05:05:00Z",
                        "completed_at": "2026-09-28T05:06:00Z",
                    },
                    {
                        "name": "Run baseline profile and macrobenchmarks on API 35",
                        "started_at": "2026-09-28T05:06:00Z",
                        "completed_at": "2026-09-28T05:09:30Z",
                    },
                ],
            },
            {
                "name": "metrics",
                "started_at": "2026-09-28T05:10:01Z",
                "completed_at": None,
                "steps": [],
            },
        ]
        result = metrics.extract_metrics(jobs)
        self.assertEqual(result["workflow_wall_clock_seconds"], 600.0)
        self.assertEqual(result["job.quality.seconds"], 120.0)
        self.assertEqual(result["job.device_validation.seconds"], 595.0)
        self.assertEqual(
            result[
                "step.device_validation.wait_for_api_35_emulator_readiness.seconds"
            ],
            60.0,
        )
        self.assertNotIn("job.metrics.seconds", result)

    def test_summarize_history_computes_percentiles_per_metric(self):
        history = [
            {"workflow_wall_clock_seconds": 100.0, "job.quality.seconds": 20.0},
            {"workflow_wall_clock_seconds": 120.0, "job.quality.seconds": 24.0},
            {"workflow_wall_clock_seconds": 140.0, "job.quality.seconds": 28.0},
        ]
        result = metrics.summarize_history(history)
        self.assertEqual(result["workflow_wall_clock_seconds"]["samples"], 3)
        self.assertEqual(result["workflow_wall_clock_seconds"]["p50"], 120.0)
        self.assertEqual(result["job.quality.seconds"]["p95"], 27.6)

    def test_markdown_contains_current_and_percentiles(self):
        current = {
            "workflow_wall_clock_seconds": 120.0,
            "job.quality.seconds": 30.0,
        }
        summary = {
            "workflow_wall_clock_seconds": {
                "samples": 4,
                "p50": 110.0,
                "p90": 125.0,
                "p95": 127.5,
            }
        }
        text = metrics.render_markdown("Android build", current, summary)
        self.assertIn("CI performance — Android build", text)
        self.assertIn("workflow_wall_clock_seconds", text)
        self.assertIn("p50", text)
        self.assertIn("p90", text)
        self.assertIn("p95", text)

    def test_recent_history_filters_to_comparable_run_cohort_and_paginates(self):
        current_run = {
            "id": 999,
            "event": "pull_request",
            "head_branch": "feature/current",
            "pull_requests": [{"base": {"ref": "main"}}],
        }
        first_page = {
            "workflow_runs": [
                {
                    "id": 1,
                    "event": "push",
                    "head_branch": "main",
                    "conclusion": "success",
                    "pull_requests": [],
                },
                {
                    "id": 2,
                    "event": "pull_request",
                    "head_branch": "feature/old",
                    "conclusion": "cancelled",
                    "pull_requests": [{"base": {"ref": "main"}}],
                },
            ]
            + [
                {
                    "id": 100 + index,
                    "event": "pull_request",
                    "head_branch": f"feature/rejected-{index}",
                    "conclusion": "failure",
                    "pull_requests": [{"base": {"ref": "main"}}],
                }
                for index in range(98)
            ]
        }
        second_page = {
            "workflow_runs": [
                {
                    "id": 200,
                    "event": "pull_request",
                    "head_branch": "feature/a",
                    "conclusion": "success",
                    "pull_requests": [{"base": {"ref": "main"}}],
                },
                {
                    "id": 201,
                    "event": "pull_request",
                    "head_branch": "feature/b",
                    "conclusion": "success",
                    "pull_requests": [{"base": {"ref": "main"}}],
                },
            ]
        }
        with mock.patch.object(
            metrics,
            "_get_json",
            side_effect=[first_page, second_page],
        ) as get_json:
            selected = metrics._fetch_recent_completed_runs(
                "owner/repo",
                123,
                "token",
                2,
                current_run,
            )
        self.assertEqual([run["id"] for run in selected], [200, 201])
        self.assertEqual(get_json.call_count, 2)
        self.assertIn("page=2", get_json.call_args_list[1].args[0])

    def test_push_history_matches_same_branch(self):
        current_run = {
            "id": 999,
            "event": "push",
            "head_branch": "main",
            "pull_requests": [],
        }
        page = {
            "workflow_runs": [
                {
                    "id": 10,
                    "event": "push",
                    "head_branch": "release",
                    "conclusion": "success",
                    "pull_requests": [],
                },
                {
                    "id": 11,
                    "event": "push",
                    "head_branch": "main",
                    "conclusion": "success",
                    "pull_requests": [],
                },
            ]
        }
        with mock.patch.object(metrics, "_get_json", return_value=page):
            selected = metrics._fetch_recent_completed_runs(
                "owner/repo",
                123,
                "token",
                1,
                current_run,
            )
        self.assertEqual([run["id"] for run in selected], [11])

    def test_http_error_preserves_status_without_token(self):
        error = urllib.error.HTTPError(
            "https://api.github.com/repos/owner/repo/actions/runs/1",
            403,
            "Forbidden",
            {},
            BytesIO(b'{"message":"rate limit exceeded"}'),
        )
        with mock.patch.object(metrics.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(metrics.GitHubApiError) as caught:
                metrics._get_json(
                    "https://api.github.com/repos/owner/repo/actions/runs/1",
                    "super-secret-token",
                )
        self.assertEqual(caught.exception.status, 403)
        self.assertIn("HTTP 403", str(caught.exception))
        self.assertIn("rate limit exceeded", str(caught.exception))
        self.assertNotIn("super-secret-token", str(caught.exception))

    def test_collect_skips_history_from_different_job_topology(self):
        current_run = {
            "id": 999,
            "workflow_id": 123,
            "name": "Android build",
            "event": "pull_request",
            "head_branch": "feature/current",
            "pull_requests": [{"base": {"ref": "main"}}],
        }
        current_jobs = [
            {
                "name": "quality",
                "started_at": "2026-09-28T05:00:00Z",
                "completed_at": "2026-09-28T05:01:00Z",
                "steps": [],
            },
            {
                "name": "device-validation",
                "started_at": "2026-09-28T05:00:00Z",
                "completed_at": "2026-09-28T05:05:00Z",
                "steps": [],
            },
            {
                "name": "build",
                "started_at": "2026-09-28T05:05:00Z",
                "completed_at": "2026-09-28T05:05:03Z",
                "steps": [],
            },
        ]
        old_topology_jobs = [
            {
                "name": "build",
                "started_at": "2026-09-27T05:00:00Z",
                "completed_at": "2026-09-27T05:10:00Z",
                "steps": [],
            }
        ]
        matching_jobs = [
            {
                "name": "quality",
                "started_at": "2026-09-27T06:00:00Z",
                "completed_at": "2026-09-27T06:01:10Z",
                "steps": [],
            },
            {
                "name": "device-validation",
                "started_at": "2026-09-27T06:00:00Z",
                "completed_at": "2026-09-27T06:05:20Z",
                "steps": [],
            },
            {
                "name": "build",
                "started_at": "2026-09-27T06:05:20Z",
                "completed_at": "2026-09-27T06:05:23Z",
                "steps": [],
            },
        ]
        candidates = [{"id": 1}, {"id": 2}]
        with mock.patch.object(metrics, "_fetch_run", return_value=current_run), \
             mock.patch.object(
                 metrics,
                 "_fetch_recent_completed_runs",
                 return_value=candidates,
             ), \
             mock.patch.object(
                 metrics,
                 "_fetch_jobs",
                 side_effect=[current_jobs, old_topology_jobs, matching_jobs],
             ):
            result = metrics.collect(
                repository="owner/repo",
                run_id=999,
                token="token",
                history_limit=1,
            )
        self.assertEqual(result["history_runs"], 1)
        self.assertEqual(
            result["history_summary"]["job.build.seconds"]["p50"],
            3.0,
        )
        self.assertEqual(result["current"]["job.build.seconds"], 3.0)

    def test_non_json_http_error_detail_is_single_line(self):
        error = urllib.error.HTTPError(
            "https://api.github.com/repos/owner/repo/actions/runs/1",
            502,
            "Bad Gateway",
            {},
            BytesIO(b"<html>\n  <body> upstream failed </body>\n</html>"),
        )
        detail = metrics._safe_http_error_detail(error)
        self.assertEqual(
            detail,
            "<html> <body> upstream failed </body> </html>",
        )
        self.assertNotIn("\n", detail)

    def test_collect_continues_paging_after_topology_mismatches(self):
        current_run = {
            "id": 999,
            "workflow_id": 123,
            "name": "Android build",
            "event": "pull_request",
            "head_branch": "feature/current",
            "pull_requests": [{"base": {"ref": "main"}}],
        }
        current_jobs = [
            {
                "name": "quality",
                "started_at": "2026-09-28T05:00:00Z",
                "completed_at": "2026-09-28T05:01:00Z",
                "steps": [],
            },
            {
                "name": "device-validation",
                "started_at": "2026-09-28T05:00:00Z",
                "completed_at": "2026-09-28T05:05:00Z",
                "steps": [],
            },
            {
                "name": "build",
                "started_at": "2026-09-28T05:05:00Z",
                "completed_at": "2026-09-28T05:05:03Z",
                "steps": [],
            },
        ]
        old_topology_jobs = [
            {
                "name": "build",
                "started_at": "2026-09-27T05:00:00Z",
                "completed_at": "2026-09-27T05:10:00Z",
                "steps": [],
            }
        ]
        matching_jobs = [
            {
                "name": "quality",
                "started_at": "2026-09-26T05:00:00Z",
                "completed_at": "2026-09-26T05:01:10Z",
                "steps": [],
            },
            {
                "name": "device-validation",
                "started_at": "2026-09-26T05:00:00Z",
                "completed_at": "2026-09-26T05:05:20Z",
                "steps": [],
            },
            {
                "name": "build",
                "started_at": "2026-09-26T05:05:20Z",
                "completed_at": "2026-09-26T05:05:23Z",
                "steps": [],
            },
        ]
        first_page = {
            "workflow_runs": [
                {
                    "id": index + 1,
                    "event": "pull_request",
                    "head_branch": f"legacy-{index}",
                    "conclusion": "success",
                    "pull_requests": [{"base": {"ref": "main"}}],
                }
                for index in range(100)
            ]
        }
        second_page = {
            "workflow_runs": [
                {
                    "id": 101,
                    "event": "pull_request",
                    "head_branch": "compatible",
                    "conclusion": "success",
                    "pull_requests": [{"base": {"ref": "main"}}],
                }
            ]
        }

        with mock.patch.object(metrics, "_fetch_run", return_value=current_run), \
             mock.patch.object(
                 metrics,
                 "_get_json",
                 side_effect=[first_page, second_page],
             ) as get_json, \
             mock.patch.object(
                 metrics,
                 "_fetch_jobs",
                 side_effect=[current_jobs] + [old_topology_jobs] * 100 + [matching_jobs],
             ):
            result = metrics.collect(
                repository="owner/repo",
                run_id=999,
                token="token",
                history_limit=1,
            )

        self.assertEqual(result["history_runs"], 1)
        self.assertEqual(
            result["history_summary"]["job.device_validation.seconds"]["p50"],
            320.0,
        )
        self.assertEqual(get_json.call_count, 2)
        self.assertIn("page=2", get_json.call_args_list[1].args[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
