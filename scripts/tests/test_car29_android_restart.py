"""CAR-29: exercise process-restart evidence against an isolated fake ADB."""
import os
import pathlib
import subprocess
import tempfile
import unittest

DRIVER = pathlib.Path(__file__).resolve().parents[1] / "car29-verify-android-e2e.sh"


class RestartContract(unittest.TestCase):
    def invoke_restart(self, new_pid):
        contents = DRIVER.read_text(encoding="utf-8")
        begin = contents.index('echo "CAR29: force-stopping Android process')
        end = contents.index('echo "CAR29: verifying the original persisted fixture', begin)
        block = contents[begin:end]
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "output").mkdir()
            state = root / "pid.txt"
            state.write_text("123\n")
            adb = root / "adb"
            adb.write_text('''#!/usr/bin/env bash
if [[ "$1 $2" == "shell pidof" ]]; then cat "$CAR29_PID_FILE"; exit 0; fi
if [[ "$1 $2 $3" == "shell am force-stop" ]]; then : > "$CAR29_PID_FILE"; exit 0; fi
if [[ "$1 $2 $3" == "shell am start" ]]; then
  if [[ ! -s "$CAR29_PID_FILE" ]]; then echo "$CAR29_NEXT_PID" > "$CAR29_PID_FILE"; fi
  echo "Status: ok"
  exit 0
fi
exit 3
''')
            adb.chmod(0o755)
            env = os.environ | {
                "PATH": f"{directory}:{os.environ['PATH']}",
                "CAR29_PID_FILE": str(state),
                "CAR29_NEXT_PID": str(new_pid),
                "PACKAGE": "com.cardenaspiero255.gamehubultra",
                "OUT_DIR": str(root / "output"),
            }
            return subprocess.run(
                ["bash", "-euo", "pipefail", "-c", block],
                cwd=root, env=env, text=True, capture_output=True
            )

    def test_rejects_reused_pid_after_force_stop(self):
        result = self.invoke_restart(123)
        self.assertNotEqual(
            0, result.returncode,
            "Reusing the previous PID cannot attest a new application process"
        )

    def test_accepts_genuinely_changed_pid(self):
        result = self.invoke_restart(456)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
