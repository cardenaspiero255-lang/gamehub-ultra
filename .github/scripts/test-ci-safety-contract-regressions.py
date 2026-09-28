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



def quality_step_bounds(android: str) -> tuple[int, int]:
    marker = "      - name: Run fast quality gates\n"
    start = android.find(marker)
    while start > 0 and android[start - 1] != "\n":
        start = android.find(marker, start + len(marker))
    if start < 0:
        raise SystemExit("Fixture drift: quality step not found")
    next_step = android.find("\n      - name: ", start + len(marker))
    if next_step < 0:
        raise SystemExit("Fixture drift: quality step terminator not found")
    return start, next_step + 1



def commented_quality_marker_before_active_step(android: str, coverage: str):
    marker = "      - name: Run fast quality gates\n"
    if marker not in android:
        raise SystemExit("Fixture drift: quality step not found")
    android = android.replace(marker, f"# {marker}{marker}", 1)
    start, _ = quality_step_bounds(android)
    if not android.startswith(marker, start):
        raise SystemExit("quality_step_bounds selected a commented marker")
    return android, coverage

def hide_quality_step_inside_run_heredoc(android: str, coverage: str):
    start, end = quality_step_bounds(android)
    fake = """      - shell: bash
        run: |
          cat <<'FAKE_GATE'
          - name: Run fast quality gates
            shell: bash
            run: |
              gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --build-cache --parallel
          FAKE_GATE
"""
    return android[:start] + fake + android[end:], coverage

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
    start, end = quality_step_bounds(android)
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
    return android[:start] + replacement + android[end:], coverage

def remove_configuration_cache_reuse_assertion(android: str, coverage: str):
    needle = '          grep -Fq "Reusing configuration cache." "$CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: Configuration Cache reuse assertion not found")
    return android.replace(needle, "", 1), coverage


def main() -> None:
    """Run CI-contract mutations and verify the optimized quality graph shape."""
    run_current_contract_must_pass()
    android = ANDROID.read_text(encoding="utf-8")
    coverage = COVERAGE.read_text(encoding="utf-8")
    commented_quality_marker_before_active_step(android, coverage)
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

    # Phase 2 block 4 starts by proving the current workflow still executes the
    # full quality graph twice. This deliberately fails until the implementation
    # replaces the duplicate execution with a configuration-only reuse probe.
    android = ANDROID.read_text(encoding="utf-8")
    quality_start, quality_end = quality_step_bounds(android)
    quality_script = android[quality_start:quality_end]
    quality_graph_occurrences = quality_script.count(":app:testDebugUnitTest")
    quality_probe_occurrences = quality_script.count("--dry-run")
    executable_quality_graphs = quality_graph_occurrences - quality_probe_occurrences
    if executable_quality_graphs != 1 or quality_probe_occurrences != 1:
        raise SystemExit(
            "Phase 2 block 4: expected one executable quality task graph and "
            "one --dry-run cache probe; "
            f"found {executable_quality_graphs} executable and "
            f"{quality_probe_occurrences} probes"
        )
    coverage = COVERAGE.read_text(encoding="utf-8")
    if ":app:createDebugUnitTestCoverageReport" in coverage and ":app:testDebugUnitTest" in quality_script:
        raise SystemExit(
            "Phase 2 block 5: unit tests are still executed independently by "
            "quality and coverage instead of sharing a verified test result"
        )

    print("CI safety contract regression tests passed.")


if __name__ == "__main__":
    main()
