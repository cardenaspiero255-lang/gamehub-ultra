"""CAR-29 regression contract: cold Android restart must run on an actual isolated emulator.

This suite is intentionally independent of Android SDK and secrets. Passing
here establishes only the workflow's safety contract, not physical E2E proof.
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/car29-rollback-cold-restart-e2e.yml"


class ColdRestartWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_only_main_and_explicit_dispatch_are_trusted(self):
        src = self.workflow
        self.assertIn("workflow_dispatch:", src)
        self.assertIn("branches: [main]", src)
        self.assertIn("if: github.ref == 'refs/heads/main'", src)
        self.assertNotRegex(src, r"(?m)^\s*(?:pull_request|pull_request_target|workflow_run|issue_comment):")
        self.assertNotIn("continue-on-error: true", src)

    def test_minimal_permissions_no_secrets_and_trusted_checkout(self):
        src = self.workflow
        self.assertRegex(src, r"(?m)^permissions:\n  contents: read$")
        self.assertIn("persist-credentials: false", src)
        self.assertIn("actions/checkout@", src)
        self.assertNotIn("secrets.", src)
        self.assertNotRegex(src, r"(?m)^\s+(?:contents|actions|pull-requests): write$")
        self.assertNotRegex(src, r"(?m)^\s+uses: .+@(?:main|master|v\d+)(?:\s|$)")

    def test_must_reject_physical_devices_and_check_exact_proof(self):
        src = self.workflow
        self.assertIn("getprop ro.kernel.qemu", src)
        self.assertIn("grep -Fxq 1", src)
        self.assertIn("scripts/car29-verify-android-e2e.sh", src)
        self.assertIn("python3 -m unittest discover -s scripts/tests", src)
        self.assertIn("CAR29-E2E-receipt.json", src)
        self.assertIn("ref: ${{ github.sha }}", src)
        self.assertIn("git rev-parse HEAD", src)

    def test_missing_skipped_or_wrong_sha_receipt_cannot_certify(self):
        src = self.workflow
        self.assertNotIn("python3 - <<", src)
        self.assertNotIn("runner.temp", src)
        self.assertIn("tools/verify_car29_restart_receipt.py", src)
        self.assertIn("if-no-files-found: error", src)
        self.assertIn("if: success()", src)


if __name__ == "__main__":
    unittest.main()
