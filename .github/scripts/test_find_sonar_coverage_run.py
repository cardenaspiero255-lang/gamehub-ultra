import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("find_sonar_coverage_run", Path(__file__).with_name("find_sonar_coverage_run.py"))


def run(rid, sha="a"*40, event="push", branch="main", repo="acme/app", status="completed", conclusion="success"):
    return {"id": rid, "head_sha": sha, "event": event, "head_branch": branch,
            "head_repository": {"full_name": repo}, "status": status, "conclusion": conclusion}


class TestSelect(unittest.TestCase):
    def choose(self, runs, sha="a"*40, repo="acme/app"):
        m = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(m)
        return m.select_run(runs, sha, repo)

    def test_selects_completed_successful_exact_main_sha(self):
        self.assertEqual(self.choose([run(1), run(2, sha="b"*40)]), 1)

    def test_rejects_untrusted_pr_forks_and_wrong_branch(self):
        self.assertIsNone(self.choose([run(1, repo="evil/fork"), run(2, event="pull_request"), run(3, branch="topic")]))

    def test_waits_for_in_progress_matching_run(self):
        self.assertIsNone(self.choose([run(4, status="in_progress", conclusion=None)]))

    def test_matching_failure_is_fatal(self):
        with self.assertRaises(RuntimeError):
            self.choose([run(4, conclusion="failure")])

    def test_rejects_malformed_missing_identity(self):
        self.assertIsNone(self.choose([dict(run(4), head_repository=None)]))

    def test_stale_success_must_not_mask_newer_failed_coverage(self):
        with self.assertRaises(RuntimeError):
            self.choose([run(9, conclusion="failure"), run(5)])

    def test_stale_success_must_not_mask_current_in_progress_coverage(self):
        self.assertIsNone(self.choose([run(9, status="in_progress", conclusion=None), run(5)]))

    def test_prioritizes_latest_completed_success(self):
        self.assertEqual(self.choose([run(5), run(9)]), 9)


if __name__ == "__main__":
    unittest.main()
