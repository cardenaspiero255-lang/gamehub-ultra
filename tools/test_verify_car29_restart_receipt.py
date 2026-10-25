"""Fail-closed contract for genuine CAR-29 Android cold-restart receipts."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_car29_restart_receipt.py")
SHA = "a" * 40


def proof():
    return {
        "schema": "gamehub.car29.android.e2e.v1",
        "gitSha": SHA,
        "junitTest": ("com.cardenaspiero255.gamehubultra."
                      "GameHubRollbackPersistenceE2ETest#"
                      "sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart"),
        "junitSha256": "b" * 64,
        "phaseLogSha256": {
            "prepare-instrumentation.log": "c" * 64,
            "verify-instrumentation.log": "d" * 64,
            "pre-restart-activity.log": "e" * 64,
            "restart-activity.log": "f" * 64,
        },
        "processBeforeForceStopPid": 123,
        "processAfterColdStartPid": 456,
        "androidForceStopAndColdStart": True,
        "persistentSessionReplayAndRollbackVerifiedAfterColdStart": True,
        "result": "PASS",
        "scope": "isolated emulator; no physical device certification",
    }


class ReceiptVerifierTest(unittest.TestCase):
    def invoke(self, data, *, sha=SHA):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "proof.json"
            path.write_text(json.dumps(data))
            return subprocess.run([sys.executable, str(SCRIPT), str(path), sha],
                                  capture_output=True, text=True)

    def test_realistic_synthetic_receipt_passes(self):
        result = self.invoke(proof())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("VERIFIED", result.stdout)

    def test_wrong_sha_or_result_fails(self):
        for change in ({"gitSha": "0"*40}, {"result": "SKIPPED"},
                       {"androidForceStopAndColdStart": False},
                       {"persistentSessionReplayAndRollbackVerifiedAfterColdStart": False}):
            with self.subTest(change=change):
                self.assertNotEqual(self.invoke(proof() | change).returncode, 0)

    def test_pid_reuse_missing_junit_or_bad_hash_fails(self):
        for change in ({"processAfterColdStartPid": 123},
                       {"processBeforeForceStopPid": "123"},
                       {"junitSha256": "0"*63},
                       {"junitTest": "other"}):
            with self.subTest(change=change):
                self.assertNotEqual(self.invoke(proof() | change).returncode, 0)

    def test_bogus_phase_logs_fail(self):
        bad = proof()
        bad["phaseLogSha256"].pop("restart-activity.log")
        self.assertNotEqual(self.invoke(bad).returncode, 0)

    def test_invalid_expected_sha_fails(self):
        self.assertNotEqual(self.invoke(proof(), sha="not-a-hash").returncode, 0)


if __name__ == "__main__":
    unittest.main()
