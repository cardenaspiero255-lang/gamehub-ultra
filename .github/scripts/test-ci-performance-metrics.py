#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
