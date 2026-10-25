"""RED/GREEN regression for the trusted Sentry API-35 emulator boot path.

The earlier real run 40164135930 timed out in adb wait-for-device (124), before
any Android Sentry event could be captured. These are source contracts only;
real success still needs a trusted-main emulator run and exact Sentry readback.
"""
from pathlib import Path
import re
import unittest

WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/sentry-sdk-delivery-e2e.yml"


class SentryBootSafetyContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = WORKFLOW.read_text(encoding="utf-8")
        cls.boot = cls.src.split("      - name: Boot isolated API35 emulator", 1)[1].split(
            "      - name: Capture controlled SDK event on Android", 1)[0]

    def test_trusted_main_only_and_no_credentials_in_boot(self):
        self.assertIn("if: github.ref == 'refs/heads/main'", self.src)
        self.assertNotRegex(self.src, r"(?m)^\s+(?:pull_request|pull_request_target):")
        self.assertNotIn("secrets.", self.boot)
        self.assertNotIn("continue-on-error: true", self.src)

    def test_runtime_is_installed_by_existing_verified_installer(self):
        self.assertIn("bash .github/scripts/install-android-runtime-sdk.sh", self.boot)
        self.assertIn("system-images/android-35/google_apis/x86_64", self.boot)
        self.assertIn("test -s", self.boot)
        self.assertIn("test -x", self.boot)

    def test_kvm_avd_live_process_and_boot_health_are_mandatory(self):
        for item in ("/dev/kvm", "EMULATOR_BIN", "EMULATOR_PID", "kill -0",
                     "config.ini", "-list-avds", "adb get-state",
                     "sys.boot_completed", "timeout 600 bash -c"):
            self.assertIn(item, self.boot)
        self.assertNotIn("timeout 360 adb wait-for-device", self.boot)
        self.assertIn("grep -Fxq 35", self.boot)

    def test_failure_is_not_recast_as_sentry_delivery(self):
        self.assertIn("SentryDeliveryE2ETest", self.src)
        self.assertIn("tools/sentry_e2e_verify.py", self.src)
        self.assertNotIn("|| true\n          test \"$ready\"", self.boot)


if __name__ == "__main__":
    unittest.main()
