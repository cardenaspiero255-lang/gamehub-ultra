"""Safety and deterministic planning tests for Kotlin mutation lab."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


path = Path(__file__).with_name("ultra_sentinel_kotlin_mutation.py")
spec = importlib.util.spec_from_file_location("ultra_sentinel_kotlin_mutation", path)
lab = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = lab
spec.loader.exec_module(lab)


class KotlinMutationLabTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.target = self.root / lab.TARGET
        self.target.parent.mkdir(parents=True)
        self.original = "\n".join([m.original for m in lab.MUTATIONS]) + "\n"
        self.target.write_text(self.original, encoding="utf-8")

    def test_each_mutation_unique_and_single_span(self):
        self.assertEqual(len(lab.MUTATIONS), 5)
        self.assertEqual(len(set(x.identifier for x in lab.MUTATIONS)), 5)
        for m in lab.MUTATIONS:
            result = lab.mutated_text(self.original, m)
            self.assertNotEqual(result, self.original)
            self.assertEqual(result.count(m.replacement), 1)
            self.assertEqual(result.count(m.original), 0)

    def test_missing_or_ambiguous_span_refused(self):
        case = lab.MUTATIONS[0]
        with self.assertRaises(ValueError):
            lab.mutated_text("something else", case)
        with self.assertRaises(ValueError):
            lab.mutated_text(case.original * 2, case)

    def test_planning_is_read_only(self):
        before = self.target.read_bytes()
        result = lab.plan(self.root)
        self.assertEqual(result["mutationCount"], 5)
        self.assertEqual(self.target.read_bytes(), before)
        self.assertEqual(result["testClass"], lab.TEST_CLASS)

    def test_missing_source_rejected(self):
        self.target.unlink()
        with self.assertRaises(ValueError):
            lab.plan(self.root)

    def test_source_symlink_rejected(self):
        other = self.root / "other.txt"
        other.write_text(self.original, encoding="utf-8")
        self.target.unlink()
        self.target.symlink_to(other)
        with self.assertRaises(ValueError):
            lab.plan(self.root)

    def test_execute_requires_explicit_ci_approval(self):
        with patch.dict("os.environ", {"CI": "false", "GITHUB_ACTIONS": "false",
                                      "SENTINEL_KOTLIN_MUTATION_APPROVED": "0"}):
            with self.assertRaises(PermissionError):
                lab.execute(self.root, 1, 60)

    def test_allowed_gradle_task_cannot_be_injected(self):
        with patch.object(lab.shutil, "which", return_value="/opt/gradle/8.13/bin/gradle"):
            command = lab.mutation_command(self.root)
        self.assertEqual(command[0], "/opt/gradle/8.13/bin/gradle")
        self.assertEqual(command[-3:], [lab.TASK, "--tests", lab.TEST_CLASS])
        self.assertNotIn("sh -c", " ".join(command))

    def test_missing_approved_gradle_executable_fails_cleanly(self):
        with patch.object(lab.shutil, "which", return_value=None):
            with self.assertRaises(ValueError):
                lab.mutation_command(self.root)

    def test_no_global_mutation_by_default(self):
        self.assertEqual(lab.MAX_MUTANTS, 5)
        self.assertEqual(lab.TARGET.count("ThermalPredictionEngine.kt"), 1)


if __name__ == "__main__":
    unittest.main()
