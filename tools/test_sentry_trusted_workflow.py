"""Security contract for the main-only Android/Sentry readback workflow."""
import pathlib
import re
import unittest
PATH = pathlib.Path(__file__).resolve().parents[1]/".github/workflows/sentry-sdk-delivery-e2e.yml"
class TrustedSentryWorkflowContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = PATH.read_text(encoding="utf8")
    def test_trusted_main_only_and_read_only_permissions(self):
        src=self.src
        self.assertIn("workflow_dispatch:",src)
        self.assertIn("branches: [main]",src)
        self.assertIn("if: github.ref == 'refs/heads/main'",src)
        self.assertRegex(src,r"(?m)^permissions:\n  contents: read$")
        self.assertNotRegex(src,r"(?m)^\s+(?:pull_request|pull_request_target|workflow_run):")
        self.assertNotRegex(src,r"(?m)^\s+(?:contents|actions|pull-requests): write")
        self.assertIn("persist-credentials: false",src)
    def test_sdk_event_and_api_readback_are_both_mandatory(self):
        src=self.src
        self.assertIn("SentryDeliveryE2ETest",src)
        self.assertIn("-e sentry_e2e true",src)
        self.assertIn("grep -q '^OK (1 test)'",src)
        self.assertIn("EVENT_ID=[a-f0-9]{32}",src)
        self.assertIn("python3 tools/sentry_e2e_verify.py --org gamehub-ultra-1t --project gamehub-ultra",src)
        self.assertIn("SENTRY_E2E_EVENT_ID: ${{ steps.capture.outputs.event_id }}",src)
        self.assertIn("SENTRY_RELEASE: gamehub-ultra@${{ github.sha }}",src)
        self.assertNotIn("continue-on-error: true",src)
    def test_secrets_only_on_trusted_build_and_readback(self):
        src=self.src
        self.assertEqual(src.count("secrets.SENTRY_DSN"),1)
        self.assertEqual(src.count("secrets.SENTRY_AUTH_TOKEN"),1)
        self.assertIn("rm -f \"$RUNNER_TEMP/sentry-instrumentation.txt\"",src)
        self.assertNotRegex(src,r"\b(?:cat|echo)\s+[\"']?\$SENTRY_(?:AUTH_TOKEN|DSN)")
if __name__=="__main__": unittest.main()
