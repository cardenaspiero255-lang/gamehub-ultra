"""RED/GREEN tests: source-only F9 checks are not APK release attestation."""
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from f9_source_audit import run_source_audit
from test_f9_security_preflight import SAFE_MANIFEST, SAFE_GRADLE, CI_WORKFLOW


class Phase9SourceAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=pathlib.Path(self.temp.name)
        (self.root/'app/src/main').mkdir(parents=True)
        (self.root/'.github/workflows').mkdir(parents=True)
        self.manifest=self.root/'app/src/main/AndroidManifest.xml'
        self.gradle=self.root/'app/build.gradle.kts'
        self.ci=self.root/'.github/workflows/android.yml'
        self.manifest.write_text(SAFE_MANIFEST,encoding='utf8')
        self.gradle.write_text(SAFE_GRADLE,encoding='utf8')
        self.ci.write_text(CI_WORKFLOW,encoding='utf8')

    def test_source_only_cannot_claim_binary_or_physical_evidence(self):
        result=run_source_audit(self.root)
        self.assertEqual('SOURCE_CHECK_ONLY',result['decision'])
        self.assertFalse(result['provenance_verified'])
        self.assertFalse(result['physical_certified'])
        self.assertFalse(result['release_certified'])

    def test_exposed_unprotected_receiver_blocks(self):
        self.manifest.write_text(SAFE_MANIFEST.replace(
            '<service android:name=".voice.UltraWakeService"',
            '<receiver android:name=".Unprotected" android:exported="true" />\n    <service android:name=".voice.UltraWakeService"'
        ),encoding='utf8')
        result=run_source_audit(self.root)
        self.assertEqual('BLOCKED',result['decision'])
        self.assertIn('UNPROTECTED_EXPORTED_COMPONENT',
                      [f['code'] for f in result['findings']])

    def test_ci_ephemeral_signing_blocks_public_release_source_audit(self):
        result=run_source_audit(self.root,'public-release')
        self.assertEqual('BLOCKED',result['decision'])
        self.assertIn('EPHEMERAL_CI_SIGNING',
                      [f['code'] for f in result['findings']])
        self.assertIn('PUBLIC_RELEASE_NOT_ATTESTED',
                      [f['code'] for f in result['findings']])

    def test_missing_manifest_blocks_not_skips(self):
        self.manifest.unlink()
        result=run_source_audit(self.root)
        self.assertEqual('BLOCKED',result['decision'])
        self.assertIn('MANIFEST_UNREADABLE',
                      [f['code'] for f in result['findings']])


if __name__=='__main__':
    unittest.main()
