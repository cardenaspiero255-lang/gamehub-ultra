import importlib.util
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("verify_sonar_jacoco", ROOT / "verify_sonar_jacoco.py")


def make_report(path, *, covered=3, missed=2, with_line=True):
    root = ET.Element("report", {"name": "app"})
    pkg = ET.SubElement(root, "package", {"name": "com/example"})
    src = ET.SubElement(pkg, "sourcefile", {"name": "Demo.kt"})
    if with_line:
        for i in range(covered):
            ET.SubElement(src, "line", {"nr": str(i + 1), "mi": "0", "ci": "2"})
        for i in range(missed):
            ET.SubElement(src, "line", {"nr": str(covered + i + 1), "mi": "2", "ci": "0"})
    ET.SubElement(root, "counter", {"type": "LINE", "missed": str(missed), "covered": str(covered)})
    path.write_bytes(ET.tostring(root, encoding="utf-8"))


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.p = Path(self.tmp.name) / "report.xml"

    def check(self):
        spec = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(spec)
        return spec.verify(self.p)

    def test_accepts_genuine_jacoco_line_counters(self):
        make_report(self.p)
        self.assertEqual(self.check(), (3, 5))

    def test_missing_report_fails_closed(self):
        with self.assertRaises(ValueError):
            self.check()

    def test_empty_or_unparseable_report_fails(self):
        self.p.write_text("<broken>")
        with self.assertRaises(ValueError):
            self.check()

    def test_coverage_with_zero_executed_lines_is_not_success(self):
        make_report(self.p, covered=0, missed=0)
        with self.assertRaises(ValueError):
            self.check()

    def test_metadata_only_report_is_rejected(self):
        make_report(self.p, with_line=False)
        with self.assertRaises(ValueError):
            self.check()

    def test_xml_counter_mismatch_is_rejected(self):
        make_report(self.p)
        tree = ET.parse(self.p)
        tree.getroot().find("counter").set("covered", "200")
        tree.write(self.p)
        with self.assertRaises(ValueError):
            self.check()


if __name__ == "__main__":
    unittest.main()
