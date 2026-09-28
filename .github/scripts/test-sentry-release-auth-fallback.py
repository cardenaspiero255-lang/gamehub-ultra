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


class RejectingOpener:
    def __init__(self):
        self.calls = 0

    def open(self, request, timeout=60):
        self.calls += 1
        url = getattr(request, "full_url", str(request))
        raise urllib.error.HTTPError(
            url,
            401,
            "Unauthorized",
            {},
            io.BytesIO(b'{"detail":"Invalid token"}'),
        )


class SentryReleaseAuthFallbackTest(unittest.TestCase):
    def test_invalid_auth_token_is_a_clean_best_effort_skip(self):
        env = {
            "SENTRY_AUTH_TOKEN": "invalid-test-token",
            "SENTRY_DSN": "https://public@example.ingest.sentry.io/123456",
            "GITHUB_ENV": os.devnull,
        }
        output = io.StringIO()
        opener = RejectingOpener()

        with mock.patch.dict(os.environ, env, clear=True), \
             mock.patch.object(sys, "argv", [str(SCRIPT), "--discover-only"]), \
             mock.patch.object(urllib.request, "build_opener", return_value=opener), \
             redirect_stdout(output):
            try:
                runpy.run_path(str(SCRIPT), run_name="__main__")
            except SystemExit as exc:
                self.assertEqual(exc.code, 0)

        self.assertGreaterEqual(opener.calls, 1)
        text = output.getvalue()
        self.assertIn("Sentry", text)
        self.assertNotIn("::error::", text)
        self.assertNotIn("Traceback", text)


if __name__ == "__main__":
    unittest.main()
