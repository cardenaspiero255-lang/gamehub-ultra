#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"
SMOKE = ROOT / ".github/workflows/supabase-research-smoke.yml"
COVERAGE = ROOT / ".github/workflows/coverage.yml"
COVERAGE_POST = ROOT / ".github/workflows/coverage-post-processing.yml"
SHADOW_METRICS = ROOT / ".github/workflows/ci-metrics-shadow.yml"
CHECKER = ROOT / ".github/scripts/check-ci-safety-contract.py"
CONTRACT = ROOT / ".github/scripts/test-ci-safety-contract.sh"


def run_contract(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["bash", str(root / ".github/scripts/test-ci-safety-contract.sh")],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def current_contract_must_pass() -> None:
    result = run_contract(ROOT)
    if result.returncode != 0:
        raise SystemExit(
            "Current optimized workflows must satisfy the CI contract.\n"
            f"--- contract output ---\n{result.stdout}"
        )



def coverage_retry_behavior_cases() -> None:
    """Exercise the actual YAML run block with a fake Gradle and zero network access."""
    import os

    extractor = r'''
require "yaml"
workflow = YAML.safe_load(File.read(ARGV.fetch(0)), aliases: true)
step = workflow.fetch("jobs").fetch("coverage-shard").fetch("steps")
  .find { |candidate| candidate["name"] == "Run packed coverage shards" }
abort("packed coverage step not found") unless step
print(step.fetch("run"))
'''
    parsed = subprocess.run(
        ["ruby", "-e", extractor, str(COVERAGE)],
        text=True, capture_output=True, check=False,
    )
    if parsed.returncode != 0:
        raise SystemExit(f"Cannot load coverage runner for regression test: {parsed.stderr}")

    fake_gradle = r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_GRADLE_CALLS"
attempt="$(wc -l < "$MOCK_GRADLE_CALLS")"
case "$MOCK_CASE" in
  transient)
    if [ "$attempt" -eq 1 ]; then
      echo "Could not get resource 'https://repo.maven.apache.org/maven2/org/robolectric/robolectric/4.17/robolectric-4.17.pom'." >&2
      echo "Received status code 429 from server: Too Many Requests" >&2
      exit 42
    fi
    ;;
  always429)
    echo "Could not get resource 'https://repo.maven.apache.org/maven2/example.pom'." >&2
    echo "Received status code 429 from server: Too Many Requests" >&2
    exit 42
    ;;
  buildfailure)
    echo "Compilation failed" >&2
    exit 24
    ;;
  other429)
    echo "Could not get resource 'https://unrelated.example/artifact'." >&2
    echo "Received status code 429 from server: Too Many Requests" >&2
    exit 19
    ;;
  *)
    exit 99
    ;;
esac
exit 0
'''
    fake_sleep = r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_SLEEP_CALLS"
'''
    for scenario, expected_status, expected_attempts, expected_sleeps in (
        ("transient", 0, 2, 1),
        ("always429", 42, 3, 2),
        ("buildfailure", 24, 1, 0),
        ("other429", 19, 1, 0),
    ):
        with tempfile.TemporaryDirectory(prefix="gamehub-coverage-retry-") as raw:
            root = Path(raw)
            (root / "bin").mkdir()
            for name, code in (("gradle", fake_gradle), ("sleep", fake_sleep)):
                target = root / "bin" / name
                target.write_text(code, encoding="utf-8")
                target.chmod(0o755)
            (root / "coverage-test-classes.txt").write_text(
                "com.example.TestStub\n", encoding="utf-8"
            )
            calls = root / "gradle-calls"
            sleeps = root / "sleep-calls"
            env = dict(os.environ)
            env.update({
                "RUNNER_TEMP": str(root),
                "COVERAGE_RUNNER_INDEX": "4",
                "MOCK_GRADLE_CALLS": str(calls),
                "MOCK_SLEEP_CALLS": str(sleeps),
                "MOCK_CASE": scenario,
                "PATH": str(root / "bin") + os.pathsep + env["PATH"],
            })
            result = subprocess.run(
                ["bash", "-c", parsed.stdout], cwd=ROOT, env=env,
                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                timeout=15, check=False,
            )
            call_lines = calls.read_text(encoding="utf-8").splitlines() if calls.exists() else []
            sleep_lines = sleeps.read_text(encoding="utf-8").splitlines() if sleeps.exists() else []
            if (
                result.returncode != expected_status
                or len(call_lines) != expected_attempts
                or len(sleep_lines) != expected_sleeps
                or not all(
                    ":app:testDebugUnitTest" in line and "--tests com.example.TestStub" in line
                    for line in call_lines
                )
            ):
                raise SystemExit(
                    f"Coverage retry scenario {scenario} failed: status={result.returncode}, "
                    f"attempts={len(call_lines)}, sleeps={len(sleep_lines)}; "
                    f"expected=({expected_status}, {expected_attempts}, {expected_sleeps})"
                    f"\n{result.stdout[-1500:]}"
                )
    print("Coverage Maven-429 retries verified; real Gradle failures remain blocking.")


def android_dependency_retry_behavior_cases() -> None:
    """Exercise the actual Android test step with fake Gradle and no network."""
    import os

    extractor = r'''
require "yaml"
workflow = YAML.safe_load(File.read(ARGV.fetch(0)), aliases: true)
step = workflow.fetch("jobs").fetch("android-test-shard").fetch("steps")
  .find { |candidate| candidate["name"] == "Run packed Android unit shards" }
abort("packed Android unit step not found") unless step
print(step.fetch("run"))
'''
    parsed = subprocess.run(
        ["ruby", "-e", extractor, str(ANDROID)],
        text=True, capture_output=True, check=False,
    )
    if parsed.returncode != 0:
        raise SystemExit(f"Cannot parse Android CI test runner: {parsed.stderr}")

    fake_gradle = r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_GRADLE_CALLS"
attempt="$(wc -l < "$MOCK_GRADLE_CALLS")"
case "$MOCK_CASE" in
  missing_robolectric)
    if [ "$attempt" -eq 1 ]; then
      echo "Could not find org.robolectric:robolectric:4.17." >&2
      exit 42
    fi
    ;;
  missing_mockito)
    if [ "$attempt" -eq 1 ]; then
      echo "Could not find org.mockito:mockito-core:5.14.2." >&2
      exit 42
    fi
    ;;
  transient429)
    if [ "$attempt" -eq 1 ]; then
      echo "Could not get resource 'https://repo.maven.apache.org/maven2/example.pom'." >&2
      echo "Received status code 429 from server: Too Many Requests" >&2
      exit 42
    fi
    ;;
  always_missing)
    echo "Could not find org.robolectric:robolectric:4.17." >&2
    exit 36
    ;;
  build_failure)
    echo "Compilation failed" >&2
    exit 24
    ;;
  unrelated_missing)
    echo "Could not find org.example:unknown:1.0." >&2
    exit 19
    ;;
  *)
    exit 99
    ;;
esac
exit 0
'''
    fake_sleep = r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_SLEEP_CALLS"
'''
    for scenario, expected_status, expected_attempts, expected_sleeps in (
        ("missing_robolectric", 0, 2, 1),
        ("missing_mockito", 0, 2, 1),
        ("transient429", 0, 2, 1),
        ("always_missing", 36, 3, 2),
        ("build_failure", 24, 1, 0),
        ("unrelated_missing", 19, 1, 0),
    ):
        with tempfile.TemporaryDirectory(prefix="gamehub-android-dep-retry-") as raw:
            root = Path(raw)
            (root / "bin").mkdir()
            for name, code in (("gradle", fake_gradle), ("sleep", fake_sleep)):
                executable = root / "bin" / name
                executable.write_text(code, encoding="utf-8")
                executable.chmod(0o755)
            (root / "android-test-classes.txt").write_text(
                "com.example.TestStub\n", encoding="utf-8"
            )
            calls = root / "gradle-calls"
            sleeps = root / "sleep-calls"
            env = dict(os.environ)
            env.update({
                "RUNNER_TEMP": str(root),
                "ANDROID_RUNNER_INDEX": "2",
                "MOCK_GRADLE_CALLS": str(calls),
                "MOCK_SLEEP_CALLS": str(sleeps),
                "MOCK_CASE": scenario,
                "PATH": str(root / "bin") + os.pathsep + env["PATH"],
            })
            result = subprocess.run(
                ["bash", "-c", parsed.stdout], cwd=ROOT, env=env,
                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                timeout=15, check=False,
            )
            invocations = calls.read_text(encoding="utf-8").splitlines() if calls.exists() else []
            sleep_lines = sleeps.read_text(encoding="utf-8").splitlines() if sleeps.exists() else []
            correct_args = all(
                ":app:testDebugUnitTest" in cmd
                and "--tests com.example.TestStub" in cmd
                and "--configuration-cache-problems=fail" in cmd
                and ("--refresh-dependencies" in cmd) == (i > 0)
                for i, cmd in enumerate(invocations)
            )
            if (
                result.returncode != expected_status
                or len(invocations) != expected_attempts
                or len(sleep_lines) != expected_sleeps
                or not correct_args
            ):
                raise SystemExit(
                    f"Android dependency retry {scenario} failed: "
                    f"status={result.returncode}, attempts={len(invocations)}, "
                    f"sleeps={len(sleep_lines)}, args={correct_args}; "
                    f"expected=({expected_status}, {expected_attempts}, {expected_sleeps})"
                    f"\n{result.stdout[-1200:]}"
                )
    print("Android dependency retries verified; unrelated/test failures remain blocking.")


def reject_mutation(
    label: str,
    *,
    android_replace: tuple[str, str] | None = None,
    smoke_replace: tuple[str, str] | None = None,
    coverage_replace: tuple[str, str] | None = None,
    post_replace: tuple[str, str] | None = None,
) -> None:
    with tempfile.TemporaryDirectory(prefix="gamehub-ci-contract-") as raw:
        temp = Path(raw)
        (temp / ".github/workflows").mkdir(parents=True)
        (temp / ".github/scripts").mkdir(parents=True)

        android = ANDROID.read_text(encoding="utf-8")
        smoke = SMOKE.read_text(encoding="utf-8")
        coverage = COVERAGE.read_text(encoding="utf-8")
        post = COVERAGE_POST.read_text(encoding="utf-8")

        for replacement, target_name in (
            (android_replace, "android"),
            (smoke_replace, "smoke"),
            (coverage_replace, "coverage"),
            (post_replace, "post"),
        ):
            if replacement is None:
                continue
            old, new = replacement
            source = {
                "android": android,
                "smoke": smoke,
                "coverage": coverage,
                "post": post,
            }[target_name]
            if old not in source:
                raise SystemExit(
                    f"Fixture drift for {label}: {target_name} needle missing"
                )
            source = source.replace(old, new, 1)
            if target_name == "android":
                android = source
            elif target_name == "smoke":
                smoke = source
            elif target_name == "coverage":
                coverage = source
            else:
                post = source

        (temp / ".github/workflows/android.yml").write_text(
            android,
            encoding="utf-8",
        )
        (temp / ".github/workflows/supabase-research-smoke.yml").write_text(
            smoke,
            encoding="utf-8",
        )
        (temp / ".github/workflows/coverage.yml").write_text(
            coverage,
            encoding="utf-8",
        )
        (temp / ".github/workflows/coverage-post-processing.yml").write_text(
            post,
            encoding="utf-8",
        )
        shutil.copy2(
            SHADOW_METRICS,
            temp / ".github/workflows/ci-metrics-shadow.yml",
        )
        shutil.copy2(
            CHECKER,
            temp / ".github/scripts/check-ci-safety-contract.py",
        )
        shutil.copy2(
            CONTRACT,
            temp / ".github/scripts/test-ci-safety-contract.sh",
        )

        result = run_contract(temp)
        if result.returncode == 0:
            raise SystemExit(
                f"Unsafe CI mutation was accepted: {label}"
            )


def main() -> None:
    current_contract_must_pass()
    coverage_retry_behavior_cases()
    android_dependency_retry_behavior_cases()

    reject_mutation(
        "quality lint made advisory",
        android_replace=(
            "      - name: Run fast quality gates\n        shell: bash\n",
            "      - name: Run fast quality gates\n"
            "        continue-on-error: true\n"
            "        shell: bash\n",
        ),
    )
    reject_mutation(
        "quality retry bound weakened",
        android_replace=(
            "          QUALITY_MAX_ATTEMPTS=3\n",
            "          QUALITY_MAX_ATTEMPTS=1\n",
        ),
    )
    reject_mutation(
        "packed quality worker removed",
        android_replace=(
            "          logical_shards=(0 1 2 3 4 5)\n",
            "          logical_shards=(0 1 2 3 4)\n",
        ),
    )
    reject_mutation(
        "packed quality failure propagation removed",
        android_replace=(
            '          exit "$status"\n',
            '          echo "$status"\n',
        ),
    )
    reject_mutation(
        "performance physical shard removed",
        android_replace=(
            "        shard: [release-apk, ui, performance-a, performance-b]\n",
            "        shard: [release-apk, ui, performance-a]\n",
        ),
    )
    reject_mutation(
        "device physical parallelism reduced",
        android_replace=(
            "      max-parallel: 4\n"
            "      matrix:\n"
            "        # Four logical performance checks are packed into two physical emulator\n"
            "        # runners so Android boot/setup is reused instead of repeated four times.\n"
            "        shard: [release-apk, ui, performance-a, performance-b]\n",
            "      max-parallel: 2\n"
            "      matrix:\n"
            "        # Four logical performance checks are packed into two physical emulator\n"
            "        # runners so Android boot/setup is reused instead of repeated four times.\n"
            "        shard: [release-apk, ui, performance-a, performance-b]\n",
        ),
    )
    reject_mutation(
        "runtime install overlap removed",
        android_replace=(
            "          SDK_PID=$!\n",
            "          SDK_PID=0\n",
        ),
    )
    reject_mutation(
        "release update validation removed",
        android_replace=(
            '              adb install -r "$APK"\n',
            "",
        ),
    )
    reject_mutation(
        "UI instrumentation removed",
        android_replace=(
            "              gradle :app:connectedDebugAndroidTest \\\n",
            "              echo connectedDebugAndroidTest \\\n",
        ),
    )
    reject_mutation(
        "baseline rule disabled",
        android_replace=(
            '                  RULE="BaselineProfile"\n',
            '                  RULE="DisabledBaseline"\n',
        ),
    )
    reject_mutation(
        "macrobenchmark rule disabled",
        android_replace=(
            '                  RULE="Macrobenchmark"\n',
            '                  RULE="DisabledMacro"\n',
        ),
    )
    reject_mutation(
        "packed startup macro removed",
        android_replace=(
            "                  LOGICAL_SHARDS=(baseline macro-startup)\n",
            "                  LOGICAL_SHARDS=(baseline)\n",
        ),
    )
    reject_mutation(
        "packed settings macro removed",
        android_replace=(
            "                  LOGICAL_SHARDS=(macro-library macro-settings)\n",
            "                  LOGICAL_SHARDS=(macro-library)\n",
        ),
    )
    reject_mutation(
        "benchmark skipped-test failure removed",
        android_replace=(
            '              raise SystemExit(f"Required performance tests were skipped: {skipped}")\n',
            "              print(skipped)\n",
        ),
    )
    reject_mutation(
        "release research-key whitespace guard weakened",
        android_replace=(
            '          research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"\n',
            '          research_key_compact="${SUPABASE_PUBLISHABLE_KEY:-}"\n',
        ),
    )
    reject_mutation(
        "release APK artifact research guard removed",
        android_replace=(
            "        if: matrix.shard == 'release-apk' && env.RESEARCH_RELEASE_READY == 'true'\n",
            "        if: matrix.shard == 'release-apk'\n",
        ),
    )
    reject_mutation(
        "release bundle Gradle task removed",
        android_replace=(
            "          gradle :app:bundleRelease \\\n",
            "          echo :app:bundleRelease \\\n",
        ),
    )
    reject_mutation(
        "device fan-in no longer blocks on all shards",
        android_replace=(
            '          test "${{ needs.device-validation-shard.result }}" = "success"\n',
            "",
        ),
    )
    reject_mutation(
        "build fan-in loses release bundle result",
        android_replace=(
            '          test "${{ needs.release-bundle.result }}" = "success"\n',
            "",
        ),
    )
    reject_mutation(
        "debug artifact current-run binding removed",
        android_replace=(
            "          run-id: ${{ github.run_id }}\n",
            "          run-id: 1\n",
        ),
    )
    reject_mutation(
        "smoke physical concurrency raised",
        smoke_replace=(
            "      max-parallel: 4\n",
            "      max-parallel: 8\n",
        ),
    )
    reject_mutation(
        "smoke logical shard count reduced",
        smoke_replace=(
            '      SMOKE_SHARD_COUNT: "64"\n',
            '      SMOKE_SHARD_COUNT: "32"\n',
        ),
    )
    reject_mutation(
        "smoke logical lanes reduced",
        smoke_replace=(
            "          logical_shards_per_runner = 8\n",
            "          logical_shards_per_runner = 4\n",
        ),
    )
    reject_mutation(
        "Android test physical concurrency raised",
        android_replace=(
            "      max-parallel: 4\n",
            "      max-parallel: 8\n",
        ),
    )
    reject_mutation(
        "Android physical unit runner removed",
        android_replace=(
            "        runner: [0, 1, 2, 3, 4, 5, 6, 7]\n",
            "        runner: [0, 1, 2, 3, 4, 5, 6]\n",
        ),
    )
    reject_mutation(
        "Android logical unit shard count reduced",
        android_replace=(
            '      ANDROID_LOGICAL_SHARD_COUNT: "64"\n',
            '      ANDROID_LOGICAL_SHARD_COUNT: "32"\n',
        ),
    )
    reject_mutation(
        "Android unit fork count reduced",
        android_replace=(
            '      GAMEHUB_UNIT_TEST_FORKS: "8"\n',
            '      GAMEHUB_UNIT_TEST_FORKS: "4"\n',
        ),
    )
    reject_mutation(
        "Android unit coverage instrumentation re-enabled",
        android_replace=(
            '      GAMEHUB_ENABLE_UNIT_TEST_COVERAGE: "false"\n',
            '      GAMEHUB_ENABLE_UNIT_TEST_COVERAGE: "true"\n',
        ),
    )
    reject_mutation(
        "coverage physical concurrency regressed to two waves",
        coverage_replace=(
            "      max-parallel: 8\n",
            "      max-parallel: 4\n",
        ),
    )
    reject_mutation(
        "coverage physical concurrency exceeds the runner budget",
        coverage_replace=(
            "      max-parallel: 8\n",
            "      max-parallel: 16\n",
        ),
    )
    reject_mutation(
        "coverage physical runner removed",
        coverage_replace=(
            "        runner: [0, 1, 2, 3, 4, 5, 6, 7]\n",
            "        runner: [0, 1, 2, 3, 4, 5, 6]\n",
        ),
    )
    reject_mutation(
        "coverage logical shard count reduced",
        coverage_replace=(
            '      COVERAGE_LOGICAL_SHARD_COUNT: "64"\n',
            '      COVERAGE_LOGICAL_SHARD_COUNT: "32"\n',
        ),
    )
    reject_mutation(
        "coverage unit instrumentation disabled",
        coverage_replace=(
            '      GAMEHUB_ENABLE_UNIT_TEST_COVERAGE: "true"\n',
            '      GAMEHUB_ENABLE_UNIT_TEST_COVERAGE: "false"\n',
        ),
    )
    reject_mutation(
        "compiled coverage class artifact removed",
        coverage_replace=(
            "      - name: Upload compiled coverage classes\n",
            "      - name: Upload compiled coverage classes disabled\n",
        ),
    )
    reject_mutation(
        "coverage class reuse root removed",
        coverage_replace=(
            '          GAMEHUB_COVERAGE_CLASS_ROOT: ${{ runner.temp }}/coverage-classes\n',
            "",
        ),
    )
    reject_mutation(
        "coverage packed tests made advisory",
        coverage_replace=(
            "      - name: Run packed coverage shards\n        shell: bash\n",
            "      - name: Run packed coverage shards\n"
            "        continue-on-error: true\n"
            "        shell: bash\n",
        ),
    )
    reject_mutation(
        "coverage test command replaced by inert text",
        coverage_replace=(
            "          gradle :app:testDebugUnitTest",
            "          echo :app:testDebugUnitTest",
        ),
    )
    reject_mutation(
        "coverage aggregation command replaced by inert text",
        coverage_replace=(
            "          gradle :app:createShardedDebugUnitTestCoverageReport",
            "          echo :app:createShardedDebugUnitTestCoverageReport",
        ),
    )
    reject_mutation(
        "coverage patch threshold weakened",
        coverage_replace=(
            "            --min-patch-line 90\n",
            "            --min-patch-line 50\n",
        ),
    )
    reject_mutation(
        "Codecov pull-request provenance removed",
        post_replace=(
            "          override_pr: ${{ github.event.workflow_run.pull_requests[0].number || '' }}\n",
            "",
        ),
    )

    print(
        "CI safety regression suite passed: packed parallel gates fail closed."
    )


if __name__ == "__main__":
    main()
