#!/usr/bin/env bash
set -euo pipefail

ANDROID=".github/workflows/android.yml"
COVERAGE=".github/workflows/coverage.yml"

fail() {
  echo "::error::$1"
  exit 1
}

test -s "$ANDROID" || fail "Missing Android workflow"
test -s "$COVERAGE" || fail "Missing coverage workflow"

require_android() {
  local pattern="$1"
  local label="$2"
  grep -Fq -- "$pattern" "$ANDROID" || fail "Android CI contract lost: $label"
}

require_coverage() {
  local pattern="$1"
  local label="$2"
  grep -Fq -- "$pattern" "$COVERAGE" || fail "Coverage CI contract lost: $label"
}

# Required correctness gates. Their implementation may be optimized, but none
# of these validation guarantees may disappear.
require_android "Run fast quality gates" "fast quality gates"
require_android ":app:assembleDebug" "debug APK compilation"
require_android ":app:lintDebug" "Android lint"
require_android ":app:assembleRelease" "release APK build"
require_android ":app:bundleRelease" "release AAB build"
require_android "Validate release APK install-update-uninstall on API 35" "API 35 install/update/uninstall"
require_android "android-35" "API 35 emulator"
require_android "connectedNonMinifiedReleaseAndroidTest" "instrumented performance validation"
require_android "BaselineProfile" "baseline profile validation"
require_android "Macrobenchmark" "macrobenchmark validation"
require_android "GameHubMacrobenchmark" "macrobenchmark result verification"
require_android "Reject known CAR-29 emulator noise" "emulator-noise regression gate"
require_android "Build distributable release APK and AAB with Sentry" "trusted Sentry release"
require_android "Register Sentry release and GitHub commit" "Sentry release registration"
require_android "Upload installable release outputs" "release artifact upload"
require_android "cancel-in-progress: true" "stale-run cancellation"

require_coverage ":app:createDebugUnitTestCoverageReport" "unit tests with coverage"
require_coverage "report.xml" "coverage XML verification"
require_coverage "codecov/codecov-action@" "Codecov upload"
require_coverage "fail_ci_if_error: true" "strict Codecov upload when configured"
require_coverage "cancel-in-progress: true" "stale coverage cancellation"

# Required gates are identified by exact commands/verification steps above.
# Auxiliary diagnostics and Sentry discovery may remain best-effort without
# weakening the required validation path.

echo "CI safety contract verified: no validation gate was removed."
