"""RED/GREEN acceptance tests for phase 9 release security preflight."""
import hashlib
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from f9_security_preflight import run_audit  # intentionally absent in RED phase

ANDROID = 'http://schemas.android.com/apk/res/android'

SAFE_MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-permission android:name="android.permission.RECORD_AUDIO"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
  <application android:allowBackup="false" android:usesCleartextTraffic="false">
    <activity android:name=".MainActivity" android:exported="true"><intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter></activity>
    <service android:name=".voice.UltraWakeService" android:exported="false" android:foregroundServiceType="microphone" />
    <service android:name=".quicksettings.Tile" android:exported="true" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"/>
  </application>
</manifest>'''

SAFE_GRADLE = '''android {
  compileSdk = 36
  defaultConfig {
    targetSdk = 36
    versionCode = 3
    versionName = "0.3.0"
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
    }
  }
}'''

CI_WORKFLOW = '      - name: Create ephemeral release signing key\n        run: keytool -genkeypair'


class Phase9PreflightTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        (self.root / 'app/src/main').mkdir(parents=True)
        (self.root / '.github/workflows').mkdir(parents=True)
        self.manifest = self.root / 'app/src/main/AndroidManifest.xml'
        self.gradle = self.root / 'app/build.gradle.kts'
        self.ci = self.root / '.github/workflows/android.yml'
        self.manifest.write_text(SAFE_MANIFEST, encoding='utf-8')
        self.gradle.write_text(SAFE_GRADLE, encoding='utf-8')
        self.ci.write_text(CI_WORKFLOW, encoding='utf-8')
        self.apk = self.root / 'app-debug.apk'
        self.apk.write_bytes(b'SYNTHETIC TEST APK, NOT AN ANDROID APK')
        self.sha = 'a' * 40
        self.provenance = self.root / 'phase3-artifact-provenance.env'
        self.provenance.write_text(
            'run_id=123456\nsha=' + self.sha + '\napk_sha256=' +
            hashlib.sha256(self.apk.read_bytes()).hexdigest() + '\n', encoding='utf-8'
        )

    def run_check(self, channel='internal-debug', sha=None):
        return run_audit(self.root, self.apk, self.provenance,
                         sha or self.sha, channel=channel)

    def codes(self, report):
        return {item['code'] for item in report['findings'] if item['level'] == 'BLOCKER'}

    def test_valid_debug_provenance_allows_internal_preflight_only(self):
        result = self.run_check()
        self.assertEqual('STATIC_PRECHECK_ONLY', result['decision'])
        self.assertTrue(result['provenance_verified'])
        self.assertEqual(self.sha, result['source_sha'])
        self.assertFalse(result['physical_certified'])
        self.assertFalse(result['release_certified'])

    def test_sha_mismatch_fails_closed(self):
        result = self.run_check(sha='b'*40)
        self.assertIn('PROVENANCE_SHA_MISMATCH', self.codes(result))
        self.assertEqual('BLOCKED', result['decision'])

    def test_modified_apk_fails_hash_check(self):
        self.apk.write_bytes(b'TAMPERED SYNTHETIC APK')
        self.assertIn('APK_HASH_MISMATCH', self.codes(self.run_check()))

    def test_missing_apk_provenance_is_not_pass(self):
        self.provenance.unlink()
        self.assertIn('PROVENANCE_MISSING', self.codes(self.run_check()))

    def test_exported_unprotected_service_blocks(self):
        self.manifest.write_text(SAFE_MANIFEST.replace(
            '<service android:name=".voice.UltraWakeService"',
            '<service android:name=".UntrustedService" android:exported="true" />\n    <service android:name=".voice.UltraWakeService"'
        ), encoding='utf-8')
        self.assertIn('UNPROTECTED_EXPORTED_COMPONENT', self.codes(self.run_check()))

    def test_cleartext_true_blocks(self):
        self.manifest.write_text(SAFE_MANIFEST.replace('android:usesCleartextTraffic="false"',
                                                       'android:usesCleartextTraffic="true"'), encoding='utf-8')
        self.assertIn('CLEARTEXT_TRAFFIC', self.codes(self.run_check()))

    def test_backup_enabled_blocks(self):
        self.manifest.write_text(SAFE_MANIFEST.replace('android:allowBackup="false"',
                                                       'android:allowBackup="true"'), encoding='utf-8')
        self.assertIn('BACKUP_ENABLED', self.codes(self.run_check()))

    def test_unprotected_exported_launcher_not_exempt_without_launcher_filter(self):
        self.manifest.write_text(SAFE_MANIFEST.replace(
            '<action android:name="android.intent.action.MAIN" />',
            '<action android:name="android.intent.action.VIEW" />'), encoding='utf-8')
        self.assertIn('UNPROTECTED_EXPORTED_COMPONENT', self.codes(self.run_check()))

    def test_microphone_foreground_service_cannot_be_exported(self):
        self.manifest.write_text(SAFE_MANIFEST.replace(
            'android:name=".voice.UltraWakeService" android:exported="false"',
            'android:name=".voice.UltraWakeService" android:exported="true"'), encoding='utf-8')
        self.assertIn('UNPROTECTED_EXPORTED_COMPONENT', self.codes(self.run_check()))

    def test_missing_required_permission_blocks(self):
        self.manifest.write_text(SAFE_MANIFEST.replace(
            '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>', ''), encoding='utf-8')
        self.assertIn('MICROPHONE_FGS_PERMISSION_MISSING', self.codes(self.run_check()))

    def test_release_without_minification_blocks(self):
        self.gradle.write_text(SAFE_GRADLE.replace('isMinifyEnabled = true', 'isMinifyEnabled = false'), encoding='utf-8')
        self.assertIn('RELEASE_MINIFICATION_DISABLED', self.codes(self.run_check()))

    def test_target_sdk_lower_than_36_blocks_play_release(self):
        self.gradle.write_text(SAFE_GRADLE.replace('targetSdk = 36', 'targetSdk = 35'), encoding='utf-8')
        self.assertIn('PLAY_TARGET_API_TOO_OLD', self.codes(self.run_check('public-release')))

    def test_ci_ephemeral_key_never_qualifies_for_public_release(self):
        result = self.run_check('public-release')
        self.assertEqual('BLOCKED', result['decision'])
        self.assertIn('EPHEMERAL_CI_SIGNING', self.codes(result))
        self.assertIn('PUBLIC_RELEASE_NOT_ATTESTED', self.codes(result))

    def test_corrupt_manifest_fails_closed(self):
        self.manifest.write_text('<manifest><application', encoding='utf-8')
        self.assertIn('MANIFEST_UNREADABLE', self.codes(self.run_check()))

    def test_missing_source_files_fail_closed(self):
        self.gradle.unlink()
        self.ci.unlink()
        result = self.run_check()
        self.assertIn('GRADLE_UNREADABLE', self.codes(result))
        self.assertIn('CI_WORKFLOW_UNREADABLE', self.codes(result))

    def test_invalid_sha_never_counted_as_attested(self):
        result = self.run_check(sha='not-a-git-sha')
        self.assertEqual('BLOCKED', result['decision'])
        self.assertIn('EXPECTED_SHA_INVALID', self.codes(result))


if __name__ == '__main__':
    unittest.main()
