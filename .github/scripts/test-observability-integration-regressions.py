#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import io
import json
import os
import tempfile
import unittest
import urllib.error
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SONAR_SCRIPT = ROOT / ".github/scripts/discover_sonar_project.py"
SENTRY_SCRIPT = ROOT / ".github/scripts/register_sentry_release.py"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load module from {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return json.dumps(self._payload).encode("utf-8")


class ObservabilityIntegrationRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sonar = load_module("gamehub_discover_sonar_project_test", SONAR_SCRIPT)
        cls.sentry = load_module("gamehub_register_sentry_release_test", SENTRY_SCRIPT)

    def test_sonar_output_records_use_real_newlines(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw) / "github-output.txt"
            self.sonar.write_outputs(
                {"host": "https://sonarcloud.io", "found": "false"},
                output_path=output,
            )
            self.assertEqual(
                output.read_text(encoding="utf-8"),
                "host=https://sonarcloud.io\nfound=false\n",
            )

    def test_sonar_missing_token_is_cleanly_disabled(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw) / "github-output.txt"
            env = {
                "GITHUB_OUTPUT": str(output),
                "REPO_NAME": "gamehub-ultra",
                "REPO_OWNER": "cardenaspiero255-lang",
                "SONAR_TOKEN": "",
            }
            self.assertEqual(self.sonar.main(env=env), 0)
            self.assertEqual(output.read_text(encoding="utf-8"), "found=false\n")

    def test_sonar_host_outage_fails_closed(self):
        def outage(_request, timeout=45):
            raise urllib.error.URLError("temporary outage")

        with self.assertRaises(RuntimeError):
            self.sonar.select_host(
                ("https://sonarcloud.io", "https://sonarqube.us"),
                token="token",
                urlopen=outage,
            )

    def test_sonar_rejected_token_is_distinct_from_outage(self):
        def rejected(_request, timeout=45):
            return FakeResponse({"valid": False})

        self.assertIsNone(
            self.sonar.select_host(
                ("https://sonarcloud.io", "https://sonarqube.us"),
                token="token",
                urlopen=rejected,
            )
        )

    def test_sonar_empty_membership_does_not_guess_github_owner_as_org(self):
        calls = []

        def urlopen(request, timeout=45):
            url = request.full_url
            calls.append(url)
            if url.endswith("/api/authentication/validate"):
                return FakeResponse({"valid": True})
            if "/api/organizations/search" in url:
                return FakeResponse(
                    {
                        "organizations": [],
                        "paging": {"pageIndex": 1, "pageSize": 50, "total": 0},
                    }
                )
            raise AssertionError(f"Unexpected Sonar request: {url}")

        result = self.sonar.discover(
            token="token",
            repo_name="gamehub-ultra",
            repo_owner="cardenaspiero255-lang",
            urlopen=urlopen,
        )
        self.assertFalse(result["found"])
        self.assertFalse(any("/api/components/search" in url for url in calls))

    def test_sonar_project_search_outage_fails_closed(self):
        def urlopen(request, timeout=45):
            url = request.full_url
            if url.endswith("/api/authentication/validate"):
                return FakeResponse({"valid": True})
            if "/api/organizations/search" in url:
                return FakeResponse(
                    {
                        "organizations": [{"key": "example-org"}],
                        "paging": {"pageIndex": 1, "pageSize": 50, "total": 1},
                    }
                )
            if "/api/components/search" in url:
                raise urllib.error.URLError("temporary outage")
            raise AssertionError(f"Unexpected Sonar request: {url}")

        with self.assertRaises(RuntimeError):
            self.sonar.discover(
                token="token",
                repo_name="gamehub-ultra",
                repo_owner="cardenaspiero255-lang",
                urlopen=urlopen,
            )

    def test_sentry_preserves_authentication_error_during_project_scan(self):
        original_paginated = self.sentry.paginated

        def fake_paginated(path):
            if path.startswith("/organizations/?"):
                yield {"slug": "example-org"}
                return
            if "/projects/" in path:
                raise RuntimeError(
                    'Sentry API returned HTTP 401 for /api/0/projects/: {"detail":"Invalid token"}'
                )
            raise AssertionError(f"Unexpected Sentry path: {path}")

        try:
            self.sentry.paginated = fake_paginated
            with mock.patch.dict(os.environ, {}, clear=True):
                with self.assertRaises(RuntimeError) as caught:
                    self.sentry.discover_project("123")
            self.assertIn("HTTP 401", str(caught.exception))
        finally:
            self.sentry.paginated = original_paginated

    def test_sentry_invalid_token_disables_release_enrichment_cleanly(self):
        with tempfile.TemporaryDirectory() as raw:
            env = {
                "SENTRY_AUTH_TOKEN": "revoked-token",
                "SENTRY_DSN": "https://public@example.invalid/123",
                "GITHUB_ENV": str(Path(raw) / "github-env.txt"),
            }
            error = RuntimeError(
                'Sentry API returned HTTP 401 for /api/0/organizations/: {"detail":"Invalid token"}'
            )
            with mock.patch.dict(os.environ, env, clear=True):
                with mock.patch.object(self.sentry, "discover_project", side_effect=error):
                    stdout = io.StringIO()
                    with redirect_stdout(stdout):
                        self.assertEqual(self.sentry.main(), 0)
            self.assertIn("invalid or revoked", stdout.getvalue())


if __name__ == "__main__":
    unittest.main(verbosity=2)
