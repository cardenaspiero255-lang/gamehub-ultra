"""Verify Supabase deployment configuration using the Python standard library.

This test runs offline in GitHub Actions. No Supabase credentials are accessed.
"""
from pathlib import Path
import tomllib
import unittest

SUPABASE = Path(__file__).resolve().parents[2]
CONFIG = SUPABASE / "config.toml"
EXPECTED_ENTRYPOINT = SUPABASE / "functions/car-autopilot/index.mjs"
MIGRATION = SUPABASE / "migrations/20261013180000_car_autopilot_dispatch.sql"


class DeploymentContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parsed = tomllib.loads(CONFIG.read_text(encoding="utf-8"))

    def test_webhook_is_the_only_jwt_exception(self):
        functions = self.parsed.get("functions")
        self.assertIsInstance(functions, dict)
        self.assertIn("car-autopilot", functions)
        self.assertIs(functions["car-autopilot"].get("verify_jwt"), False)
        for name, spec in functions.items():
            if name != "car-autopilot":
                self.assertIsNot(spec.get("verify_jwt"), False, name)

    def test_entrypoint_resolves_to_real_local_file(self):
        value = self.parsed["functions"]["car-autopilot"].get("entrypoint")
        self.assertEqual(value, "./functions/car-autopilot/index.mjs")
        entrypoint = (SUPABASE / value).resolve(strict=True)
        self.assertEqual(entrypoint, EXPECTED_ENTRYPOINT.resolve(strict=True))
        self.assertIn("Deno.serve(", entrypoint.read_text(encoding="utf-8"))

    def test_reviewed_sql_migration_is_present(self):
        self.assertTrue(MIGRATION.is_file())
        sql = MIGRATION.read_text(encoding="utf-8")
        self.assertIn("pg_advisory_xact_lock", sql)
        self.assertIn("security invoker", sql.lower())
        self.assertIn("enable row level security", sql.lower())


if __name__ == "__main__":
    unittest.main()
