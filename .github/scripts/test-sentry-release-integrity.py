#!/usr/bin/env python3
"""Adversarial Sentry registration tests; all network access is mocked."""
import io
import json
import os
import runpy
import sys
import unittest
import urllib.error
import urllib.request
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

SCRIPT = Path(__file__).with_name("register_sentry_release.py")
SHA = "a" * 40
TOKEN = "private-test-token-must-not-leak"
VERSION = "gamehub-ultra@" + SHA
REPO = "cardenaspiero255-lang/gamehub-ultra"


class FakeResponse:
    def __init__(self, value):
        self.data = json.dumps(value).encode("utf-8")
        self.headers = {}

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self):
        return self.data


class FakeSentry:
    def __init__(self, *, created_version=None, stored_version=None, stored_project=None,
                 fail_status=None):
        self.calls = []
        self.created_version = created_version or VERSION
        self.stored_version = stored_version or VERSION
        self.stored_project = stored_project or "gamehub"
        self.fail_status = fail_status

    def open(self, req, timeout=60):
        url = req.full_url
        method = req.get_method()
        self.calls.append((method, url))
        if url.endswith("/projects/test-org/gamehub/repo/"):
            return FakeResponse([{"name": REPO}])
        if url.endswith("/organizations/test-org/releases/") and method == "POST":
            if self.fail_status is not None:
                raise urllib.error.HTTPError(
                    url, self.fail_status, "server error", {},
                    io.BytesIO(("server said: " + TOKEN).encode("utf-8"))
                )
            return FakeResponse({"version": self.created_version, "commitCount": 1})
        if ("/organizations/test-org/releases/" in url and
                url.endswith("/?project=gamehub") and method == "GET"):
            return FakeResponse({
                "version": self.stored_version,
                "projects": [{"slug": self.stored_project}],
                "ref": SHA,
            })
        raise AssertionError("Unexpected Sentry request: " + method + " " + url)


def execute(fake):
    env = {
        "SENTRY_AUTH_TOKEN": TOKEN,
        "SENTRY_DSN": "https://public@example.ingest.sentry.io/123456",
        "SENTRY_ORG": "test-org",
        "SENTRY_PROJECT": "gamehub",
        "SENTRY_RELEASE": VERSION,
        "GH_REPOSITORY": REPO,
        "GH_SHA": SHA,
    }
    captured = io.StringIO()
    error = None
    with mock.patch.dict(os.environ, env, clear=True), \
         mock.patch.object(sys, "argv", [str(SCRIPT)]), \
         mock.patch.object(urllib.request, "build_opener", return_value=fake), \
         redirect_stdout(captured):
        try:
            runpy.run_path(str(SCRIPT), run_name="__main__")
        except (SystemExit, RuntimeError) as exc:
            error = exc
    return captured.getvalue(), error


class SentryReleaseIntegrityTest(unittest.TestCase):
    def test_registration_requires_sentry_get_readback_and_exact_project(self):
        fake = FakeSentry()
        output, err = execute(fake)
        self.assertIsNone(err, repr(err))
        self.assertIn("Read-back verified", output)
        self.assertTrue(any(method == "GET" and "?project=gamehub" in url
                            for method, url in fake.calls))

    def test_wrong_post_version_cannot_report_success(self):
        output, err = execute(FakeSentry(created_version="wrong-release"))
        self.assertIsNotNone(err)
        self.assertNotIn("Read-back verified", output)
        self.assertNotIn("Registered Sentry release", output)

    def test_wrong_readback_project_cannot_report_success(self):
        output, err = execute(FakeSentry(stored_project="another-project"))
        self.assertIsNotNone(err)
        self.assertNotIn("Read-back verified", output)
        self.assertNotIn("Registered Sentry release", output)

    def test_wrong_readback_version_cannot_report_success(self):
        output, err = execute(FakeSentry(stored_version="wrong-release"))
        self.assertIsNotNone(err)
        self.assertNotIn("Read-back verified", output)
        self.assertNotIn("Registered Sentry release", output)

    def test_non_auth_http_failure_must_not_reflect_response_body_or_token(self):
        output, err = execute(FakeSentry(fail_status=500))
        self.assertIsNotNone(err)
        self.assertNotIn(TOKEN, output)
        self.assertNotIn(TOKEN, str(err))
        self.assertIn("HTTP 500", str(err))
        self.assertNotIn("Registered Sentry release", output)


if __name__ == "__main__":
    unittest.main()
