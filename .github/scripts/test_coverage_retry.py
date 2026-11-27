import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("is_transient_maven_429.py")
SPEC = importlib.util.spec_from_file_location("is_transient_maven_429", SCRIPT)


class Maven429RetryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(cls.module)

    def test_recognizes_robolectric_repo1_maven_429_from_ci_log(self):
        log = (
            "Failed to fetch maven artifact org.robolectric:android-all-instrumented\n"
            "Server returned HTTP response code: 429 for URL: "
            "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented.jar"
        )
        self.assertTrue(self.module.is_transient_maven_429(log))

    def test_recognizes_alternate_maven_central_host_and_gradle_message(self):
        log = "Received status code 429 from server: Too Many Requests https://repo.maven.apache.org/maven2/x.jar"
        self.assertTrue(self.module.is_transient_maven_429(log))

    def test_does_not_retry_test_assertion_failures(self):
        log = "AssertionError: expected true but was false; test failed"
        self.assertFalse(self.module.is_transient_maven_429(log))

    def test_does_not_retry_non_429_maven_errors(self):
        log = "HTTP response code: 503 for https://repo1.maven.org/maven2/x.jar"
        self.assertFalse(self.module.is_transient_maven_429(log))

    def test_does_not_retry_unrelated_429(self):
        log = "HTTP response code: 429 for https://api.example.com/endpoint"
        self.assertFalse(self.module.is_transient_maven_429(log))

    def test_does_not_combine_maven_url_and_unrelated_429_from_different_lines(self):
        log = (
            "Resolving dependency from https://repo1.maven.org/maven2/x.jar\\n"
            "HTTP response code: 429 for https://api.example.com/endpoint"
        )
        self.assertFalse(self.module.is_transient_maven_429(log))


if __name__ == "__main__":
    unittest.main()
