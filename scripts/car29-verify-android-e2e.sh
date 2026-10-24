#!/usr/bin/env bash
# Standalone CAR-29 evidence driver. Do not run on a personal/production device:
# the instrumentation test mutates the app's test data and launches MainActivity.
set -euo pipefail

PACKAGE=com.cardenaspiero255.gamehubultra
TEST_PACKAGE="${PACKAGE}.test"
TEST_CLASS="${PACKAGE}.GameHubRollbackPersistenceE2ETest#sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart"
RUNNER="${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
OUT_DIR="${CAR29_PROOF_DIR:-build/car29-android-e2e}"
mkdir -p "$OUT_DIR"
command -v adb >/dev/null || { echo "Android platform-tools adb is required" >&2; exit 2; }
command -v gradle >/dev/null || { echo "Gradle (same as Android CI workflow) is required" >&2; exit 2; }
command -v python3 >/dev/null || { echo "Python 3 is required" >&2; exit 2; }

if [ "$(adb devices | sed -n '/device$/p' | wc -l | tr -d ' ')" -ne 1 ]; then
  echo "Exactly one authorized emulator must be connected" >&2
  exit 2
fi
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]; then
  echo "REFUSING to run a destructive test on a physical Android device" >&2
  exit 2
fi
# The receipt refers to this exact tree, not to an untracked local variant.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "CAR29 requires a clean tracked source tree for a SHA-bound receipt" >&2
  exit 2
fi
COMMIT="$(git rev-parse HEAD)"
gradle :app:connectedDebugAndroidTest --stacktrace

# AGP instrumentation tasks can uninstall their runner APK during teardown.
# Reinstall the *same locally built* debug artifacts for the two-phase proof.
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
test -s "$APP_APK" || { echo "Missing debug APK" >&2; exit 1; }
test -s "$TEST_APK" || { echo "Missing androidTest APK" >&2; exit 1; }
adb install -r "$APP_APK"
adb install -r "$TEST_APK"

if ! adb shell pm path "$PACKAGE" | grep -q base.apk; then
  echo "Target debug application is not installed" >&2
  exit 1
fi
if ! adb shell pm path "$TEST_PACKAGE" | grep -q base.apk; then
  echo "Target instrumentation APK is not installed" >&2
  exit 1
fi

echo "CAR29: preparing durable session and rollback fixture"
adb shell am instrument -w -r -e class "$TEST_CLASS" -e car29Phase prepare "$RUNNER" \
  | tee "$OUT_DIR/prepare-instrumentation.log"
if ! grep -Eq 'OK \(1 test\)' "$OUT_DIR/prepare-instrumentation.log" ||
   ! grep -Eq 'INSTRUMENTATION_CODE: -1' "$OUT_DIR/prepare-instrumentation.log"; then
  echo "CAR29 prepare instrumentation was not a passing single test" >&2
  exit 1
fi

echo "CAR29: force-stopping Android process and restarting the real activity"
adb shell am force-stop "$PACKAGE"
if [ -n "$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')" ]; then
  echo "Target process survived force-stop" >&2
  exit 1
fi
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee "$OUT_DIR/restart-activity.log"
COLD_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')"
if [ -z "$COLD_PID" ]; then
  echo "Target activity failed to start a new Android process" >&2
  exit 1
fi

echo "CAR29: verifying the original persisted fixture from a new instrumentation process"
adb shell am instrument -w -r -e class "$TEST_CLASS" -e car29Phase verify "$RUNNER" \
  | tee "$OUT_DIR/verify-instrumentation.log"
if ! grep -Eq 'OK \(1 test\)' "$OUT_DIR/verify-instrumentation.log" ||
   ! grep -Eq 'INSTRUMENTATION_CODE: -1' "$OUT_DIR/verify-instrumentation.log"; then
  echo "CAR29 verify instrumentation was not a passing single test" >&2
  exit 1
fi

python3 - "$COMMIT" "$OUT_DIR" <<'PY'
import hashlib
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

commit, output = sys.argv[1:]
out = pathlib.Path(output)
root = pathlib.Path("app/build/outputs/androidTest-results")
classname = "com.cardenaspiero255.gamehubultra.GameHubRollbackPersistenceE2ETest"
method = "sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart"
cases = []
for path in root.rglob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    for node in suite.iter("testcase"):
        if node.get("classname") == classname and node.get("name") == method:
            cases.append((path, node))
if len(cases) != 1:
    raise SystemExit(f"CAR29 exact JUnit result missing/duplicated: {len(cases)}")
path, case = cases[0]
if any(case.find(tag) is not None for tag in ("skipped", "failure", "error")):
    raise SystemExit("CAR29 exact JUnit result failed or skipped")
checks = {}
for name in ("prepare-instrumentation.log", "verify-instrumentation.log", "restart-activity.log"):
    checks[name] = hashlib.sha256((out / name).read_bytes()).hexdigest()
receipt = {
    "schema": "gamehub.car29.android.e2e.v1",
    "gitSha": commit,
    "junitTest": classname + "#" + method,
    "junitSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    "phaseLogSha256": checks,
    "androidForceStopAndColdStart": True,
    "persistentSessionReplayAndRollbackVerifiedAfterColdStart": True,
    "result": "PASS",
    "scope": "isolated emulator; no physical device certification",
}
proof = out / "CAR29-E2E-receipt.json"
proof.write_text(json.dumps(receipt, sort_keys=True, indent=2) + "\n")
print("CAR29 PASS: " + str(proof))
PY
