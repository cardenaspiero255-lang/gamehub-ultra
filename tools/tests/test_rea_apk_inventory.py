"""Independent synthetic APK tests, no GameSir code/binaries in fixtures."""
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'rea_apk_inventory.py'


class APKInventoryTest(unittest.TestCase):
    def make_apk(self, path, entries):
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED) as z:
            for name, content in entries.items():
                z.writestr(name, content)

    def run_tool(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], capture_output=True, text=True)

    def test_inventory_reports_only_allowlisted_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'sample.apk'
            self.make_apk(apk, {
                'AndroidManifest.xml': b'opaque-binary-xml',
                'classes.dex': b'dex-a',
                'classes2.dex': b'dex-b',
                'lib/arm64-v8a/libengine.so': b'elf',
                'lib/armeabi-v7a/libcompat.so': b'elf',
                'assets/user-private-token-secret.txt': b'secret',
                'res/drawable/logo.png': b'png',
                'META-INF/CERT.SF': b'sig',
            })
            result = self.run_tool('--apk', apk)
            self.assertEqual(result.returncode, 0, result.stderr)
            doc = json.loads(result.stdout)
            self.assertEqual(doc['status'], 'STATIC_METADATA_ONLY')
            self.assertEqual(doc['dex_count'], 2)
            self.assertEqual(doc['native_abi_counts'], {'arm64-v8a': 1, 'armeabi-v7a': 1})
            self.assertEqual(doc['entry_count'], 8)
            self.assertEqual(doc['asset_count'], 1)
            self.assertEqual(doc['resource_count'], 1)
            self.assertTrue(doc['has_android_manifest'])
            self.assertEqual(len(doc['apk_sha256']), 64)
            self.assertNotIn('secret', result.stdout)
            self.assertNotIn('libengine.so', result.stdout)
            self.assertNotIn(str(apk), result.stdout)

    def test_rejects_invalid_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad = Path(tmp) / 'bad.apk'
            bad.write_bytes(b'not zip')
            result = self.run_tool('--apk', bad)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('NOT_ANALYZED', result.stderr)

    def test_rejects_zip_traversal_without_extracting(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'escape.apk'
            self.make_apk(apk, {'AndroidManifest.xml': b'abc', 'classes.dex': b'dex', '../unsafe': b'bad'})
            result = self.run_tool('--apk', apk)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('NOT_ANALYZED', result.stderr)
            self.assertFalse((Path(tmp).parent / 'unsafe').exists())

    def test_rejects_missing_expected_apk_structure(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'missing.apk'
            self.make_apk(apk, {'assets/plain.txt': b'text'})
            result = self.run_tool('--apk', apk)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('NOT_ANALYZED', result.stderr)

    def test_refuses_to_replace_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'ok.apk'
            out = Path(tmp) / 'report.json'
            self.make_apk(apk, {'AndroidManifest.xml': b'a', 'classes.dex': b'b'})
            out.write_text('PRESERVE')
            result = self.run_tool('--apk', apk, '--out', out)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(out.read_text(), 'PRESERVE')

    def test_json_report_file_created_exactly_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'ok.apk'
            out = Path(tmp) / 'report.json'
            self.make_apk(apk, {'AndroidManifest.xml': b'a', 'classes.dex': b'b'})
            first = self.run_tool('--apk', apk, '--out', out)
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(json.loads(out.read_text())['status'], 'STATIC_METADATA_ONLY')
            self.assertNotEqual(self.run_tool('--apk', apk, '--out', out).returncode, 0)


if __name__ == '__main__':
    unittest.main()
