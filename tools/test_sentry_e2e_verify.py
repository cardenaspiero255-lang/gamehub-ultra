import json
import sys
import unittest
import urllib.error
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sentry_e2e_verify as e2e

EVENT = "a" * 32
RELEASE = "gamehub-ultra@" + "b" * 40
TOKEN = "private-example-token-do-not-log"

class FakeResponse:
    def __init__(self, body, status=200):
        self.status = status
        self.payload = body
    def __enter__(self): return self
    def __exit__(self, *_): pass
    def read(self, n): return self.payload[:n]

class FakeOpener:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.requests = []
    def open(self, request, timeout):
        self.requests.append((request, timeout))
        item = next(self.responses)
        if isinstance(item, Exception): raise item
        return FakeResponse(json.dumps(item).encode())

def http_error(code):
    return urllib.error.HTTPError("https://sentry.io/api/0/", code, "unsanitized-token-placeholder", {}, None)

class VerifySentryE2ETest(unittest.TestCase):
    def test_exact_event_and_release_match(self):
        opener = FakeOpener([{"eventID": EVENT, "release": {"version": RELEASE}}])
        proof = e2e.verify_event("gamehub-ultra-1t", "gamehub-ultra", EVENT, RELEASE, TOKEN, opener=opener)
        self.assertTrue(proof["verified"])
        req, timeout = opener.requests[0]
        self.assertEqual(req.full_url, f"https://sentry.io/api/0/projects/gamehub-ultra-1t/gamehub-ultra/events/{EVENT}/")
        self.assertEqual(req.get_method(), "GET")
        self.assertEqual(timeout, 15)

    def test_404_then_matching_200(self):
        opener = FakeOpener([http_error(404), {"eventID": EVENT, "release": RELEASE}])
        sleeps = []
        proof = e2e.verify_event("org", "project", EVENT, RELEASE, TOKEN, opener=opener, attempts=2, sleeper=sleeps.append)
        self.assertTrue(proof["verified"])
        self.assertEqual(sleeps, [5])

    def test_wrong_id_wrong_release_and_missing_release_fail_closed(self):
        for body in [{"eventID": "0" * 32, "release": RELEASE},
                     {"eventID": EVENT, "release": "other"},
                     {"eventID": EVENT}]:
            with self.subTest(body=body):
                with self.assertRaises(e2e.VerificationError):
                    e2e.verify_event("org", "project", EVENT, RELEASE, TOKEN, opener=FakeOpener([body]))

    def test_auth_and_missing_event_fail_without_echoing_secrets(self):
        for status in [401, 403, 404, 302]:
            with self.subTest(status=status):
                with self.assertRaises(e2e.VerificationError) as error:
                    e2e.verify_event("org", "project", EVENT, RELEASE, TOKEN, opener=FakeOpener([http_error(status)]), attempts=1)
                self.assertNotIn(TOKEN, str(error.exception))
                self.assertNotIn("unsanitized", str(error.exception))

    def test_rejects_invalid_ids_and_redirect_hosts_before_requests(self):
        for org in ["org/foo", "CAPS", "", "../bad", "test%2f"]:
            with self.subTest(org=org):
                with self.assertRaises(e2e.VerificationError):
                    e2e.verify_event(org, "project", EVENT, RELEASE, TOKEN, opener=FakeOpener([]))
        for event in ["not-a-sha", "a" * 31, "a" * 32 + "/x"]:
            with self.assertRaises(e2e.VerificationError):
                e2e.verify_event("org", "project", event, RELEASE, TOKEN, opener=FakeOpener([]))
        with self.assertRaises(e2e.VerificationError):
            e2e.verify_event("org", "project", EVENT, "wrong-release", TOKEN, opener=FakeOpener([]))

    def test_rejects_large_and_invalid_json(self):
        for raw in [b"[1]", b"{"]:
            class RawOpener:
                def open(self, req, timeout): return FakeResponse(raw)
            with self.assertRaises(e2e.VerificationError):
                e2e.verify_event("org", "project", EVENT, RELEASE, TOKEN, opener=RawOpener())
        class HugeOpener:
            def open(self, req, timeout): return FakeResponse(b"x" * (e2e.MAX_BYTES + 5))
        with self.assertRaises(e2e.VerificationError):
            e2e.verify_event("org", "project", EVENT, RELEASE, TOKEN, opener=HugeOpener())


    def test_trusted_environment_handoff_to_server_readback(self):
        from unittest.mock import patch
        with patch.dict("os.environ", {
            "SENTRY_E2E_EVENT_ID": EVENT,
            "SENTRY_RELEASE": RELEASE,
            "SENTRY_AUTH_TOKEN": TOKEN,
        }):
            with patch.object(e2e, "verify_event", return_value={"verified": True}) as verify:
                self.assertEqual(e2e.main(["--org", "org", "--project", "project"]), 0)
                verify.assert_called_once_with("org", "project", EVENT, RELEASE, TOKEN)

    def test_missing_environment_identity_fails_closed(self):
        from unittest.mock import patch
        with patch.dict("os.environ", {"SENTRY_AUTH_TOKEN": TOKEN}, clear=True):
            self.assertEqual(e2e.main(["--org", "org", "--project", "project"]), 1)

if __name__ == "__main__": unittest.main()
