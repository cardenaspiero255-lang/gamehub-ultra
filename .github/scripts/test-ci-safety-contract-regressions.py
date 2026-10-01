#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"
COVERAGE = ROOT / ".github/workflows/coverage.yml"
SHADOW_METRICS = ROOT / ".github/workflows/ci-metrics-shadow.yml"
CONTRACT = ROOT / ".github/scripts/test-ci-safety-contract.sh"
CHECKER = ROOT / ".github/scripts/check-ci-safety-contract.py"




def require_shadow_metrics_contract() -> None:
    """Require a read-only post-run shadow collector with exact parent provenance."""
    if not SHADOW_METRICS.is_file():
        raise SystemExit("Phase 3 block 2: shadow metrics workflow is missing")

    text = SHADOW_METRICS.read_text(encoding="utf-8")
    required_fragments = (
        "workflow_run:",
        "Android build",
        "Unit Test Coverage",
        "types: [completed]",
        "actions: read",
        "contents: read",
        "github.event.workflow_run.id",
        "github.event.workflow_run.head_sha",
        "github.event.workflow_run.name",
        "--expected-run-id",
        "--expected-head-sha",
        "--expected-workflow",
        "--require-completed",
        "--require-job",
    )
    missing = [fragment for fragment in required_fragments if fragment not in text]
    if missing:
        raise SystemExit(
            "Phase 3 block 2: shadow metrics provenance contract is incomplete: "
            f"{missing!r}"
        )
    if "actions/checkout@" in text:
        raise SystemExit(
            "Phase 3 block 2: post-run shadow metrics must not checkout untrusted PR code"
        )
    if "permissions:\n  actions: read\n  contents: read" not in text:
        raise SystemExit(
            "Phase 3 block 2: shadow metrics permissions must remain read-only"
        )


def run_current_contract_must_pass() -> None:
    """Require the unmodified workflows to satisfy the current CI contract."""
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
    """Apply one workflow mutation and require the safety contract to reject it."""
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
    """Replace the active coverage test command with a non-executable comment."""
    needle = "        run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage-owned unit-test command not found")
    replacement = "        # run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n"
    return android, coverage.replace(needle, replacement, 1)


def make_quality_advisory(android: str, coverage: str):
    """Mutate the quality gate so failures are incorrectly treated as advisory."""
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
    """Mutate the coverage gate so failures are incorrectly treated as advisory."""
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
    """Comment out the authoritative coverage command while preserving its text."""
    needle = "        run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage command not found")
    return android, coverage.replace(
        needle,
        "        # run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n",
        1,
    )



def quality_step_bounds(android: str) -> tuple[int, int]:
    """Return source bounds for the active named quality step fixture."""
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
    """Verify step discovery ignores a commented marker before the active step."""
    marker = "      - name: Run fast quality gates\n"
    if marker not in android:
        raise SystemExit("Fixture drift: quality step not found")
    android = android.replace(marker, f"# {marker}{marker}", 1)
    start, _ = quality_step_bounds(android)
    if not android.startswith(marker, start):
        raise SystemExit("quality_step_bounds selected a commented marker")
    return android, coverage

def hide_quality_step_inside_run_heredoc(android: str, coverage: str):
    """Replace the real quality step with misleading YAML text inside a heredoc."""
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
    """Make the quality gate advisory through a workflow expression."""
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
    """Replace coverage execution with an echo that only mentions the task name."""
    command = "        run: gradle :app:createDebugUnitTestCoverageReport --build-cache --parallel --stacktrace\n"
    if command not in coverage:
        raise SystemExit("Fixture drift: coverage-owned unit-test command not found")
    replacement = "        run: echo ':app:createDebugUnitTestCoverageReport'\n"
    return android, coverage.replace(command, replacement, 1)



def mask_quality_gradle_with_or_true(android: str, coverage: str):
    """Mask a piped quality Gradle failure with a spaced logical OR true."""
    needle = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log" || true\n'
    return android.replace(needle, replacement, 1), coverage


def mask_quality_gradle_with_fused_or_true(android: str, coverage: str):
    """Mask a piped quality Gradle failure with a fused logical OR true."""
    needle = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '            --stacktrace 2>&1 | tee "$RUNNER_TEMP/quality-gates.log" ||true\n'
    return android.replace(needle, replacement, 1), coverage


def mask_quality_gradle_plain_or_true(android: str, coverage: str):
    """Mask a direct quality Gradle failure with logical OR true."""
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
    """Remove the assertion proving Configuration Cache reuse."""
    needle = '          grep -Fq "Reusing configuration cache." "$CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: Configuration Cache reuse assertion not found")
    return android.replace(needle, "", 1), coverage


def remove_release_configuration_cache_reuse_assertion(android: str, coverage: str):
    """Remove the release Configuration Cache reuse assertion."""
    needle = '            grep -Fq "Reusing configuration cache." "$RELEASE_CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: release Configuration Cache reuse assertion not found")
    return android.replace(needle, "", 1), coverage


def duplicate_partial_release_graph(android: str, coverage: str):
    """Duplicate release/performance work without repeating assembleRelease."""
    needle = '            grep -Fq "Reusing configuration cache." "$RELEASE_CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: release cache assertion not found")
    duplicate = """            gradle \\\n              :app:bundleRelease \\\n              :app:assembleNonMinifiedRelease \\\n              :baseline-profile:assembleNonMinifiedRelease \\\n              --build-cache \\\n              --parallel \\\n              --configuration-cache \\\n              --configuration-cache-problems=fail \\\n              --stacktrace\n"""
    return android.replace(needle, duplicate + needle, 1), coverage



def duplicate_full_release_graph_without_configuration_cache(android: str, coverage: str):
    """Duplicate the full release graph while omitting Configuration Cache flags."""
    needle = '            grep -Fq "Reusing configuration cache." "$RELEASE_CONFIG_CACHE_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: release cache assertion not found")
    duplicate = """            gradle \\\n              :app:assembleRelease \\\n              :app:bundleRelease \\\n              :app:assembleNonMinifiedRelease \\\n              :baseline-profile:assembleNonMinifiedRelease \\\n              --build-cache \\\n              --parallel \\\n              --stacktrace\n"""
    return android.replace(needle, duplicate + needle, 1), coverage


def main() -> None:
    """Run CI-contract mutations and verify the optimized quality graph shape."""
    run_current_contract_must_pass()
    require_shadow_metrics_contract()
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
    run_mutation("Release Configuration Cache reuse assertion removed", remove_release_configuration_cache_reuse_assertion)
    run_mutation("Partial release/performance graph duplicated", duplicate_partial_release_graph)
    run_mutation("Full release graph duplicated without Configuration Cache flags", duplicate_full_release_graph_without_configuration_cache)

    # Phase 2 block 4 starts by proving the current workflow still executes the
    # full quality graph twice. This deliberately fails until the implementation
    # replaces the duplicate execution with a configuration-only reuse probe.
    android = ANDROID.read_text(encoding="utf-8")
    quality_start, quality_end = quality_step_bounds(android)
    quality_script = android[quality_start:quality_end]
    quality_graph_occurrences = quality_script.count(":app:assembleDebug")
    quality_probe_occurrences = quality_script.count("--dry-run")
    executable_quality_graphs = quality_graph_occurrences - quality_probe_occurrences
    if executable_quality_graphs != 1 or quality_probe_occurrences != 2:
        raise SystemExit(
            "Phase 2 block 7: expected one executable quality task graph and "
            "two identical --dry-run cache probes; "
            f"found {executable_quality_graphs} executable and "
            f"{quality_probe_occurrences} probes"
        )
    coverage = COVERAGE.read_text(encoding="utf-8")
    if ":app:testDebugUnitTest" in quality_script:
        raise SystemExit("Phase 2 block 5: quality must not duplicate coverage-owned unit tests")
    if ":app:createDebugUnitTestCoverageReport" not in coverage:
        raise SystemExit("Phase 2 block 5: coverage must remain the authoritative unit-test gate")

    # Phase 2 block 6 is enforced by the parsed CI safety contract above.
    # Mutations prove both the reuse assertion and duplicate partial graphs fail.

    print("CI safety contract regression tests passed.")


if __name__ == "__main__":
    main()
