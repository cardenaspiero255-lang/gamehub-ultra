#!/usr/bin/env python3
"""Verify a single opt-in Android Sentry SDK event via exact server read-back.

An SDK-generated ID or a successful flush is not proof of ingestion.
The verifier only sends a GET to sentry.io; event payloads are never logged.
Execute with credentials on trusted main or an authorized local machine, never
against untrusted pull-request source with GitHub Actions secrets.
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

SLUG = re.compile(r"[a-z0-9][a-z0-9-]{0,49}\Z")
EVENT = re.compile(r"[a-f0-9]{32}\Z")
RELEASE = re.compile(r"gamehub-ultra@[a-f0-9]{40}\Z")
MAX_BYTES = 256 * 1024

class VerificationError(RuntimeError):
    pass

class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None

def check_identity(org, project, event_id, release):
    if not SLUG.fullmatch(org) or not SLUG.fullmatch(project):
        raise VerificationError("Invalid project identity")
    if not EVENT.fullmatch(event_id):
        raise VerificationError("Invalid exact Sentry event ID")
    if not RELEASE.fullmatch(release):
        raise VerificationError("Invalid exact gamehub-ultra release")

def verify_event(org, project, event_id, release, token, *, opener=None,
                 attempts=8, delay=5, sleeper=time.sleep):
    check_identity(org, project, event_id, release)
    if not isinstance(token, str) or len(token) < 12 or "\r" in token or "\n" in token:
        raise VerificationError("SENTRY_AUTH_TOKEN is missing or invalid")
    if attempts < 1 or attempts > 20 or delay < 0 or delay > 30:
        raise VerificationError("Invalid bounded retry policy")
    url = f"https://sentry.io/api/0/projects/{org}/{project}/events/{event_id}/"
    opener = opener or urllib.request.build_opener(NoRedirects())
    for attempt in range(attempts):
        req = urllib.request.Request(url, method="GET", headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json",
            "User-Agent": "gamehub-ultra-e2e-readback/1",
        })
        try:
            with opener.open(req, timeout=15) as response:
                if response.status != 200:
                    raise VerificationError("Unexpected HTTP response from Sentry")
                payload_bytes = response.read(MAX_BYTES + 1)
                if len(payload_bytes) > MAX_BYTES:
                    raise VerificationError("Sentry response exceeded bounded size")
                body = json.loads(payload_bytes)
                if not isinstance(body, dict):
                    raise VerificationError("Sentry event response is not an object")
                if body.get("eventID") != event_id:
                    raise VerificationError("Sentry returned a different event ID")
                stored_release = body.get("release")
                if isinstance(stored_release, dict):
                    stored_release = stored_release.get("version")
                if stored_release != release:
                    raise VerificationError("Sentry event does not match the intended release")
                return {"verified": True, "org": org, "project": project,
                        "event_id": event_id, "release": release,
                        "evidence": "Sentry API exact event/release read-back"}
        except urllib.error.HTTPError as exc:
            if exc.code == 404 and attempt + 1 < attempts:
                sleeper(delay)
                continue
            if exc.code in (401, 403):
                raise VerificationError(f"Sentry event read-back denied: HTTP {exc.code}") from None
            if 300 <= exc.code < 400:
                raise VerificationError("Sentry attempted an unexpected redirect") from None
            if exc.code == 404:
                raise VerificationError("Exact event not ingested before verification deadline") from None
            raise VerificationError(f"Sentry event read-back failed: HTTP {exc.code}") from None
        except (urllib.error.URLError, TimeoutError):
            raise VerificationError("Sentry event read-back network failure") from None
        except (UnicodeError, json.JSONDecodeError, ValueError):
            raise VerificationError("Sentry event response could not be decoded") from None
    raise VerificationError("Exact event not verified")

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--org", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--event-id", required=True)
    parser.add_argument("--release", required=True)
    args = parser.parse_args(argv)
    try:
        verify_event(args.org, args.project, args.event_id,
                     args.release, os.getenv("SENTRY_AUTH_TOKEN", ""))
        print("VERIFIED: exact Sentry SDK event and release persisted for expected project")
        if os.getenv("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
                out.write("Sentry SDK delivery E2E: VERIFIED (exact event ID/release; no raw event data).\n")
        return 0
    except VerificationError as exc:
        print("NOT_CERTIFIED: " + str(exc), file=sys.stderr)
        return 1

if __name__ == "__main__":
    sys.exit(main())
