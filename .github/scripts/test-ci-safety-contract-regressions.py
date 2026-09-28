#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"
COVERAGE = ROOT / ".github/workflows/coverage.yml"
CONTRACT = ROOT / ".github/scripts/test-ci-safety-contract.sh"
CHECKER = ROOT / ".github/scripts/check-ci-safety-contract.py"



def run_current_contract_must_pass() -> None:
    result = subprocess.run(
        ["bash", str(CONTRACT)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(
            "Current valid workflows must satisfy the CI safety contract.\n"
            f"--- contract output ---\n{result.stdout}"
        )

def run_mutation(label: str, mutate) -> None:
    with tempfile.TemporaryDirectory(prefix="gamehub-ci-contract-") as raw:
        temp = Path(raw)
        (temp / ".github/workflows").mkdir(parents=True)
        (temp / ".github/scripts").mkdir(parents=True)

        android = ANDROID.read_text(encoding="utf-8")
        coverage = COVERAGE.read_text(encoding="utf-8")
        android, coverage = mutate(android, coverage)

        (temp / ".github/workflows/android.yml").write_text(android, encoding="utf-8")
        (temp / ".github/workflows/coverage.yml").write_text(coverage, encoding="utf-8")
        target = temp / ".github/scripts/test-ci-safety-contract.sh"
        checker = temp / ".github/scripts/check-ci-safety-contract.py"
        shutil.copy2(CONTRACT, target)
        shutil.copy2(CHECKER, checker)

        result = subprocess.run(
            ["bash", str(target)],
            cwd=temp,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        if result.returncode == 0:
            raise SystemExit(
                f"CI contract regression was not rejected: {label}\n"
                f"--- contract output ---\n{result.stdout}"
            )


def remove_unit_test_but_leave_comment(android: str, coverage: str):
    needle = "            :app:testDebugUnitTest \\\n"
    if needle not in android:
        raise SystemExit("Fixture drift: Android unit-test command not found")
    return android.replace(needle, "            # :app:testDebugUnitTest \\\n", 2), coverage


def make_quality_advisory(android: str, coverage: str):
    needle = """      - name: Run fast quality gates
        shell: bash
"""
    if needle not in android:
        raise SystemExit("Fixture drift: quality step not found")
    replacement = """      - name: Run fast quality gates
        continue-on-error: true
        shell: bash
"""
    return android.replace(needle, replacement, 1), coverage


def make_coverage_advisory(android: str, coverage: str):
    needle = """      - name: Generate debug unit-test coverage
        shell: bash
"""
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage step not found")
    replacement = """      - name: Generate debug unit-test coverage
        continue-on-error: true
        shell: bash
"""
    return android, coverage.replace(needle, replacement, 1)


def comment_out_coverage_command(android: str, coverage: str):
    needle = "        run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage command not found")
    return android, coverage.replace(
        needle,
        "        # run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n",
        1,
    )



def hide_quality_step_inside_run_heredoc(android: str, coverage: str):
    quality = """      - name: Run fast quality gates
        shell: bash
        run: |
          set -euo pipefail
          CONFIG_CACHE_LOG="$RUNNER_TEMP/configuration-cache-reuse.log"
          gradle \
            :app:testDebugUnitTest \
            :app:assembleDebug \
            :app:lintDebug \
            --build-cache \
            --parallel \
            --configuration-cache \
            --configuration-cache-problems=fail \
            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"

          gradle \
            :app:testDebugUnitTest \
            :app:assembleDebug \
            :app:lintDebug \
            --build-cache \
            --parallel \
            --configuration-cache \
            --configuration-cache-problems=fail \
            --stacktrace 2>&1 | tee "$CONFIG_CACHE_LOG"
          grep -Fq "Reusing configuration cache." "$CONFIG_CACHE_LOG"
"""
    if quality not in android:
        raise SystemExit("Fixture drift: quality step block not found")

    # A nameless GitHub Actions step is valid. The old regex scanner can walk
    # into its run block and mistake the heredoc text below for a real step.
    fake = """      - shell: bash
        run: |
          cat <<'FAKE_GATE'
          - name: Run fast quality gates
            shell: bash
            run: |
              gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --build-cache --parallel
          FAKE_GATE
"""
    return android.replace(quality, fake, 1), coverage


def make_quality_advisory_with_expression(android: str, coverage: str):
    needle = """      - name: Run fast quality gates
        shell: bash
"""
    if needle not in android:
        raise SystemExit("Fixture drift: quality step not found")
    replacement = """      - name: Run fast quality gates
        continue-on-error: ${{ true }}
        shell: bash
"""
    return android.replace(needle, replacement, 1), coverage


def remove_unit_test_but_echo_name(android: str, coverage: str):
    task = "            :app:testDebugUnitTest \\\n"
    if task not in android:
        raise SystemExit("Fixture drift: Android unit-test command not found")
    android = android.replace(task, "", 2)

    command = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"\n'
    if command not in android:
        raise SystemExit("Fixture drift: quality Gradle command terminator not found")
    replacement = command + "          echo ':app:testDebugUnitTest'\n"
    return android.replace(command, replacement, 1), coverage



def mask_quality_gradle_with_or_true(android: str, coverage: str):
    needle = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log" || true\n'
    return android.replace(needle, replacement, 1), coverage


def mask_quality_gradle_with_fused_or_true(android: str, coverage: str):
    needle = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log" ||true\n'
    return android.replace(needle, replacement, 1), coverage


def mask_quality_gradle_plain_or_true(android: str, coverage: str):
    needle = """      - name: Run fast quality gates
        shell: bash
        run: |
          set -euo pipefail
          CONFIG_CACHE_LOG="$RUNNER_TEMP/configuration-cache-reuse.log"
          gradle \
            :app:testDebugUnitTest \
            :app:assembleDebug \
            :app:lintDebug \
            --build-cache \
            --parallel \
            --configuration-cache \
            --configuration-cache-problems=fail \
            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"

          gradle \
            :app:testDebugUnitTest \
            :app:assembleDebug \
            :app:lintDebug \
            --build-cache \
            --parallel \
            --configuration-cache \
            --configuration-cache-problems=fail \
            --stacktrace 2>&1 | tee "$CONFIG_CACHE_LOG"
          grep -Fq "Reusing configuration cache." "$CONFIG_CACHE_LOG"
"""
    if needle not in android:
        raise SystemExit("Fixture drift: quality step block not found")
    replacement = """      - name: Run fast quality gates
        shell: bash
        run: |
          set -euo pipefail
          gradle \\
            :app:testDebugUnitTest \\
            :app:assembleDebug \\
            :app:lintDebug \\
            --build-cache \\
            --parallel \\
            --stacktrace || true
"""
    return android.replace(needle, replacement, 1), coverage


def remove_configuration_cache_reuse_assertion(android: str, coverage: str):
    needle = '          grep -Fq "Reusing configuration cache." "$CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: Configuration Cache reuse assertion not found")
    return android.replace(needle, "", 1), coverage


def main() -> None:
    run_current_contract_must_pass()
    run_mutation("Android unit tests removed but text left in a comment", remove_unit_test_but_leave_comment)
    run_mutation("quality gate made advisory with continue-on-error", make_quality_advisory)
    run_mutation("coverage gate made advisory with continue-on-error", make_coverage_advisory)
    run_mutation("coverage command commented out", comment_out_coverage_command)
    run_mutation("fake quality step hidden inside run heredoc", hide_quality_step_inside_run_heredoc)
    run_mutation("unit test removed from Gradle but echoed later", remove_unit_test_but_echo_name)
    run_mutation("quality gate made advisory with expression", make_quality_advisory_with_expression)
    run_mutation("quality Gradle pipeline masked with || true", mask_quality_gradle_with_or_true)
    run_mutation("quality Gradle pipeline masked with fused ||true", mask_quality_gradle_with_fused_or_true)
    run_mutation("quality Gradle command masked with plain || true", mask_quality_gradle_plain_or_true)
    run_mutation("Configuration Cache reuse assertion removed", remove_configuration_cache_reuse_assertion)
    print("CI safety contract regression tests passed.")


if __name__ == "__main__":
    main()
