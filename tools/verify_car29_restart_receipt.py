#!/usr/bin/env python3
"""Verify a SHA-bound CAR-29 cold Android process restart receipt (synthetic or real).

A passing JSON verifier is not evidence that a device ran: the trusted main
workflow must invoke the real driver and upload the receipt produced there.
"""
import json
import re
import sys
from pathlib import Path

SHA40 = re.compile(r"[0-9a-f]{40}\Z")
SHA64 = re.compile(r"[0-9a-f]{64}\Z")
EXPECTED_LOGS = frozenset({
    "prepare-instrumentation.log",
    "verify-instrumentation.log",
    "pre-restart-activity.log",
    "restart-activity.log",
})
JUNIT_TEST = ("com.cardenaspiero255.gamehubultra."
              "GameHubRollbackPersistenceE2ETest#"
              "sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart")


class InvalidReceipt(ValueError):
    pass


def verify(path: Path, expected_sha: str) -> None:
    if not isinstance(expected_sha, str) or not SHA40.fullmatch(expected_sha):
        raise InvalidReceipt("Missing immutable trusted SHA")
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 32768:
        raise InvalidReceipt("Missing or untrusted receipt file")
    try:
        receipt = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeError, json.JSONDecodeError, OSError) as exc:
        raise InvalidReceipt("Invalid receipt JSON") from exc
    if not isinstance(receipt, dict):
        raise InvalidReceipt("Invalid receipt document")
    for name, expected in (
        ("schema", "gamehub.car29.android.e2e.v1"),
        ("gitSha", expected_sha),
        ("junitTest", JUNIT_TEST),
        ("result", "PASS"),
        ("scope", "isolated emulator; no physical device certification"),
    ):
        if receipt.get(name) != expected:
            raise InvalidReceipt("Wrong or incomplete receipt field: " + name)
    for name in ("androidForceStopAndColdStart",
                 "persistentSessionReplayAndRollbackVerifiedAfterColdStart"):
        if receipt.get(name) is not True:
            raise InvalidReceipt("Cold restart proof missing: " + name)
    a, b = (receipt.get("processBeforeForceStopPid"),
            receipt.get("processAfterColdStartPid"))
    if type(a) is not int or type(b) is not int or min(a, b) <= 0 or a == b:
        raise InvalidReceipt("Missing genuinely different positive process IDs")
    sha = receipt.get("junitSha256")
    if not isinstance(sha, str) or not SHA64.fullmatch(sha):
        raise InvalidReceipt("JUnit evidence SHA-256 missing")
    logs = receipt.get("phaseLogSha256")
    if not isinstance(logs, dict) or set(logs) != EXPECTED_LOGS:
        raise InvalidReceipt("Missing required phase log hashes")
    if any(not isinstance(v, str) or not SHA64.fullmatch(v) for v in logs.values()):
        raise InvalidReceipt("Invalid phase log SHA-256")


def main(argv=None):
    args = list(sys.argv[1:] if argv is None else argv)
    if len(args) != 2:
        print("NOT_CERTIFIED: expected receipt path and SHA", file=sys.stderr)
        return 2
    try:
        verify(Path(args[0]), args[1])
    except (InvalidReceipt, OSError) as exc:
        print("NOT_CERTIFIED: " + str(exc), file=sys.stderr)
        return 1
    print("VERIFIED: trusted main receipt with exact SHA, JUnit and real restart evidence")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
