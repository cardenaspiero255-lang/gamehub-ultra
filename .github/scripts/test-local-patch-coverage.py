#!/usr/bin/env python3
from __future__ import annotations

import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import local_patch_coverage as gate


class LocalPatchCoverageTest(unittest.TestCase):
    def test_parse_added_lines_tracks_only_new_side_lines(self) -> None:
        diff = """diff --git a/app/src/main/java/com/example/Foo.kt b/app/src/main/java/com/example/Foo.kt
--- a/app/src/main/java/com/example/Foo.kt
+++ b/app/src/main/java/com/example/Foo.kt
@@ -8,2 +8,3 @@
 old
+newA
+newB
 keep
"""
        self.assertEqual(
            gate.parse_added_lines(diff),
            {"app/src/main/java/com/example/Foo.kt": {9, 10}},
        )

    def test_patch_coverage_ignores_non_executable_added_lines(self) -> None:
        report = ET.fromstring(
            """<report>
              <package name="com/example">
                <sourcefile name="Foo.kt">
                  <line nr="9" mi="0" ci="4" mb="0" cb="0"/>
                  <line nr="10" mi="3" ci="0" mb="0" cb="0"/>
                </sourcefile>
              </package>
            </report>"""
        )
        stats = gate.calculate_patch_line_coverage(
            report,
            {
                "app/src/main/java/com/example/Foo.kt": {9, 10, 11},
                ".github/workflows/coverage.yml": {1},
            },
        )
        self.assertEqual(stats.executable, 2)
        self.assertEqual(stats.covered, 1)
        self.assertAlmostEqual(stats.percent, 50.0)

    def test_unmapped_executable_looking_addition_fails_closed(self) -> None:
        report = ET.fromstring('<report><package name="com/example"><sourcefile name="Foo.kt"><line nr="9" mi="0" ci="1"/></sourcefile></package></report>')
        stats = gate.calculate_patch_line_coverage(report, {"app/src/main/java/com/example/Foo.kt": {9, 10}}, {"app/src/main/java/com/example/Foo.kt": "\n" * 8 + "val covered = 1\nval missing = expensiveCall()\n"})
        self.assertEqual(stats.unmapped_files, ("app/src/main/java/com/example/Foo.kt:10",))
        self.assertFalse(gate.meets_threshold(stats, 90.0))


    def test_unmapped_kotlin_declarations_are_not_treated_as_executable(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = """val covered = 1
private fun adviseInternal(
    question: String,
    context: Context,
): Result {
    return Result()
}
fun interface Recommender {
    fun recommend(
        observation: Observation,
    ): Recommendation
}
private companion object {
    const val LIMIT = 6
}
),
): String? =
"""
        stats = gate.calculate_patch_line_coverage(
            report,
            {
                "app/src/main/java/com/example/Foo.kt": {
                    1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17
                }
            },
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(stats.unmapped_files, ())


    def test_jacoco_omitted_line_in_mapped_file_is_not_assumed_executable(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="9" mi="0" ci="1"/></sourcefile></package></report>'
        )
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": {9, 10}},
            {"app/src/main/java/com/example/Foo.kt": "\n" * 8 + "val covered = 1\nval declarationOnly = 2\n"},
        )
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)
        self.assertEqual(stats.unmapped_files, ())


    def test_bare_kotlin_when_else_arrow_is_not_executable(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": {1, 2}},
            {
                "app/src/main/java/com/example/Foo.kt":
                    "val covered = expensiveCall()\nelse ->\n"
            },
        )
        self.assertEqual(stats.unmapped_files, ())
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)


    def test_kotlin_continuations_and_typed_literal_declarations_are_not_false_unmapped(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = """val covered = expensiveCall()
readiness =
beforeStart = {
"com.example.ACTION"
private var monitorJob: Job? = null
private var lastReport: Report? = null
"""
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": set(range(1, 7))},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(stats.unmapped_files, ())
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)


    def test_mapped_compose_body_remains_blocking_patch_coverage(self) -> None:
        source = """@Composable
internal fun ExampleCard(
    enabled: Boolean,
) {
    Text("hello")
    if (enabled) {
        Text("enabled")
    }
}
"""
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="5" mi="4" ci="0"/>'
            '<line nr="6" mi="2" ci="0"/>'
            '<line nr="7" mi="4" ci="0"/>'
            '</sourcefile></package></report>'
        )
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": set(range(1, 9))},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(stats.executable, 3)
        self.assertEqual(stats.covered, 0)
        self.assertAlmostEqual(stats.percent, 0.0)

    def test_enum_when_branch_label_omitted_by_jacoco_is_not_false_unmapped(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = "val covered = expensiveCall()\nSTOP_TTS ->\n"
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": {1, 2}},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(stats.unmapped_files, ())
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)


    def test_kotlin_structural_if_and_enum_continuations_are_not_false_unmapped(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = """val covered = expensiveCall()
if (
PackageManager.PERMISSION_GRANTED
LEGACY_MOVE_TO_FOREGROUND,
SessionCoachGamePresence.ACTIVE,
realCall()
"""
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": set(range(1, 7))},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(
            stats.unmapped_files,
            ("app/src/main/java/com/example/Foo.kt:6",),
        )
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)


    def test_multiline_kotlin_when_condition_arrow_is_structural(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = """val covered = expensiveCall()
) ->
"""
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": {1, 2}},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(stats.unmapped_files, ())
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)



    def test_kotlin_named_lambda_parameter_header_is_structural(self) -> None:
        report = ET.fromstring(
            '<report><package name="com/example"><sourcefile name="Foo.kt">'
            '<line nr="1" mi="0" ci="1"/></sourcefile></package></report>'
        )
        source = """val covered = expensiveCall()
save = { packageName, config, onSaved ->
realCall()
"""
        stats = gate.calculate_patch_line_coverage(
            report,
            {"app/src/main/java/com/example/Foo.kt": {1, 2, 3}},
            {"app/src/main/java/com/example/Foo.kt": source},
        )
        self.assertEqual(
            stats.unmapped_files,
            ("app/src/main/java/com/example/Foo.kt:3",),
        )
        self.assertEqual(stats.executable, 1)
        self.assertEqual(stats.covered, 1)


    def test_threshold_is_blocking_below_minimum(self) -> None:
        stats = gate.PatchCoverage(executable=10, covered=8)
        self.assertFalse(gate.meets_threshold(stats, 90.0))
        self.assertTrue(gate.meets_threshold(gate.PatchCoverage(10, 9), 90.0))

    def test_no_executable_patch_lines_passes(self) -> None:
        stats = gate.PatchCoverage(executable=0, covered=0)
        self.assertTrue(gate.meets_threshold(stats, 90.0))


if __name__ == "__main__":
    unittest.main()
