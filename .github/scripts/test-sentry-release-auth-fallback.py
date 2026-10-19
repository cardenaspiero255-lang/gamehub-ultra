#!/usr/bin/env python3
from __future__ import annotations

import io
import os
import runpy
import sys
import unittest
import urllib.error
import urllib.request
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/register_sentry_release.py"


class FakeResponse:
    def __init__(self, body, headers=None):
        self._body = body
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self):
        return self._body


class ProjectRejectingOpener:
    def __init__(self):
        self.urls = []

    def open(self, request, timeout=60):
        url = getattr(request, "full_url", str(request))
        self.urls.append(url)
        if "/api/0/organizations/?" in url:
            return FakeResponse(b'[{"slug":"test-org"}]')
        if "/api/0/organizations/test-org/projects/" in url:
            raise urllib.error.HTTPError(
                url,
                401,
                "Unauthorized",
                {},
                io.BytesIO(b'{"detail":"Invalid token"}'),
            )
        raise AssertionError(f"Unexpected Sentry URL in test: {url}")


class SentryReleaseAuthFallbackTest(unittest.TestCase):
    def test_invalid_auth_token_blocks_release_registration_without_exposing_credentials(self):
        env = {
            "SENTRY_AUTH_TOKEN": "invalid-test-token",
            "SENTRY_DSN": "https://public@example.ingest.sentry.io/123456",
            "GITHUB_ENV": os.devnull,
        }
        output = io.StringIO()
        opener = ProjectRejectingOpener()

        with mock.patch.dict(os.environ, env, clear=True), \
             mock.patch.object(sys, "argv", [str(SCRIPT), "--discover-only"]), \
             mock.patch.object(urllib.request, "build_opener", return_value=opener), \
             redirect_stdout(output):
            with self.assertRaises(SystemExit) as raised:
                runpy.run_path(str(SCRIPT), run_name="__main__")
            self.assertEqual(raised.exception.code, 3)

        self.assertGreaterEqual(len(opener.urls), 2)
        self.assertTrue(
            any("/api/0/organizations/test-org/projects/" in url for url in opener.urls)
        )
        text = output.getvalue()
        self.assertIn("Sentry release registration not certified", text)
        self.assertIn("::error::", text)
        self.assertNotIn("invalid-test-token", text)
        self.assertNotIn("Traceback", text)


    def test_sentry_auth_failure_is_reported_but_never_accepted_as_verified_e2e(self):
        script = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("Sentry release registration not certified", script)
        self.assertIn("sys.exit(3)", script)
        self.assertNotIn("Trusted release registration was skipped cleanly", script)


if __name__ == "__main__":
    unittest.main()
