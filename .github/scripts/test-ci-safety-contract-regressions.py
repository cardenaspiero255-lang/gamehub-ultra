#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile

import yaml
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"
COVERAGE = ROOT / ".github/workflows/coverage.yml"
COVERAGE_POST = ROOT / ".github/workflows/coverage-post-processing.yml"
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
        "--fail-on-unavailable",
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
        shutil.copy2(SHADOW_METRICS, temp / ".github/workflows/ci-metrics-shadow.yml")
        shutil.copy2(
            COVERAGE_POST,
            temp / ".github/workflows/coverage-post-processing.yml"
        )
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


def remove_build_always_condition(android: str, coverage: str):
    """Remove the fan-in job condition that makes dependency failures observable."""
    needle = """  build:
    name: build
    if: always()
"""
    if needle not in android:
        raise SystemExit("Fixture drift: aggregate build job condition not found")
    return android.replace(needle, """  build:
    name: build
""", 1), coverage


def remove_release_upload_research_key_guard(android: str, coverage: str):
    """Reject publishing installable release outputs without the research key guard."""
    needle = """      - name: Upload installable release outputs
        if: env.RESEARCH_RELEASE_READY == 'true'
"""
    if needle not in android:
        raise SystemExit("Fixture drift: release upload research-key guard not found")
    replacement = """      - name: Upload installable release outputs
"""
    return android.replace(needle, replacement, 1), coverage


def weaken_release_research_key_whitespace_guard(android: str, coverage: str):
    """Reject reverting the release gate to a raw non-empty secret check."""
    needle = """          research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"
"""
    if needle not in android:
        raise SystemExit("Fixture drift: whitespace-safe research-key guard not found")
    replacement = """          research_key_compact="${SUPABASE_PUBLISHABLE_KEY:-}"
"""
    return android.replace(needle, replacement, 1), coverage


def disable_trusted_release_empty_key_failure_branch(android: str, coverage: str):
    """Reject turning the trusted empty-key failure branch into dead code."""
    needle = """            if [ "${TRUSTED_RELEASE_CONTEXT}" = "true" ]; then
"""
    if needle not in android:
        raise SystemExit("Fixture drift: trusted release failure branch not found")
    replacement = """            if false; then
"""
    return android.replace(needle, replacement, 1), coverage


def require_dependabot_prs_are_untrusted(android: str, coverage: str):
    """Dependabot pull requests must not require Actions-only release secrets."""
    expected = """TRUSTED_RELEASE_CONTEXT: ${{ github.event_name != 'pull_request' || (github.actor != 'dependabot[bot]' && github.event.pull_request.head.repo.full_name == github.repository) }}"""
    if expected not in android:
        raise SystemExit(
            "Dependabot pull requests are not explicitly routed through untrusted release validation"
        )
    return android, coverage


def remove_dependabot_untrusted_guard(android: str, coverage: str):
    """Reject treating same-repository Dependabot pull requests as trusted."""
    needle = """          TRUSTED_RELEASE_CONTEXT: ${{ github.event_name != 'pull_request' || (github.actor != 'dependabot[bot]' && github.event.pull_request.head.repo.full_name == github.repository) }}
"""
    if needle not in android:
        raise SystemExit("Fixture drift: Dependabot release-context guard not found")
    replacement = """          TRUSTED_RELEASE_CONTEXT: ${{ github.event_name != 'pull_request' || github.event.pull_request.head.repo.full_name == github.repository }}
"""
    return android.replace(needle, replacement, 1), coverage


def remove_device_validation_result_assertion(android: str, coverage: str):
    """Remove one blocking dependency-result assertion from the aggregate gate."""
    needle = '          test "${{ needs.device-validation.result }}" = "success"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: device-validation aggregate assertion not found")
    return android.replace(needle, "", 1), coverage


def remove_unit_test_but_leave_comment(android: str, coverage: str):
    """Replace the active coverage test command with a non-executable comment."""
    needle = "          gradle :app:createDebugUnitTestCoverageReport \\\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage-owned unit-test command not found")
    replacement = "          # gradle :app:createDebugUnitTestCoverageReport \\\n"
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


def make_local_patch_coverage_advisory(android: str, coverage: str):
    """Reject weakening the authoritative local patch coverage gate."""
    needle = """      - name: Enforce local patch coverage
        env:
"""
    if needle not in coverage:
        raise SystemExit("Fixture drift: local patch coverage gate not found")
    replacement = """      - name: Enforce local patch coverage
        continue-on-error: true
        env:
"""
    return android, coverage.replace(needle, replacement, 1)


def replace_local_patch_gate_with_echo(android: str, coverage: str):
    """Reject inert text that merely repeats the required coverage command."""
    needle = "          python3 .github/scripts/local_patch_coverage.py \\\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: local patch coverage command not found")
    replacement = "          echo 'python3 .github/scripts/local_patch_coverage.py --xml report.xml --base x --head y --min-patch-line 90'\n"
    return android, coverage.replace(needle, replacement, 1)


def mask_local_patch_coverage_with_or_true(android: str, coverage: str):
    """Reject masking the authoritative local patch coverage command."""
    needle = "            --min-patch-line 90\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: local patch coverage terminator not found")
    return android, coverage.replace(
        needle,
        "            --min-patch-line 90 || true\n",
        1,
    )


def comment_out_coverage_command(android: str, coverage: str):
    """Comment out the authoritative coverage command while preserving its text."""
    needle = "          gradle :app:createDebugUnitTestCoverageReport \\\n"
    if needle not in coverage:
        raise SystemExit("Fixture drift: coverage command not found")
    return android, coverage.replace(
        needle,
        "          # gradle :app:createDebugUnitTestCoverageReport \\\n",
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
    command = "          gradle :app:createDebugUnitTestCoverageReport \\\n"
    if command not in coverage:
        raise SystemExit("Fixture drift: coverage-owned unit-test command not found")
    replacement = "          echo ':app:createDebugUnitTestCoverageReport' \\\n"
    return android, coverage.replace(command, replacement, 1)



def mask_quality_gradle_with_or_true(android: str, coverage: str):
    """Mask a piped quality Gradle failure with a spaced logical OR true."""
    needle = '              --stacktrace 2>&1 | tee "$QUALITY_ATTEMPT_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '              --stacktrace 2>&1 | tee "$QUALITY_ATTEMPT_LOG" || true\n'
    return android.replace(needle, replacement, 1), coverage


def mask_quality_gradle_with_fused_or_true(android: str, coverage: str):
    """Mask a piped quality Gradle failure with a fused logical OR true."""
    needle = '              --stacktrace 2>&1 | tee "$QUALITY_ATTEMPT_LOG"\n'
    if needle not in android:
        raise SystemExit("Fixture drift: quality Gradle terminator not found")
    replacement = '              --stacktrace 2>&1 | tee "$QUALITY_ATTEMPT_LOG" ||true\n'
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


def remove_quality_transient_retry(android: str, coverage: str):
    """Reject losing bounded retry protection around the executable quality graph."""
    needle = 'QUALITY_MAX_ATTEMPTS=3'
    if needle not in android:
        # RED fixture: current workflow has no bounded retry yet.
        return android, coverage
    return android.replace(needle, 'QUALITY_MAX_ATTEMPTS=1', 1), coverage


def spoof_quality_retry_bound_with_comment(android: str, coverage: str):
    """Reject a stale comment that hides a weakened active retry assignment."""
    needle = "          QUALITY_MAX_ATTEMPTS=3\n"
    if needle not in android:
        raise SystemExit("Fixture drift: active quality retry bound not found")
    replacement = "          QUALITY_MAX_ATTEMPTS=1\n          # QUALITY_MAX_ATTEMPTS=3\n"
    return android.replace(needle, replacement, 1), coverage


def scan_full_quality_history_for_retry(android: str, coverage: str):
    """Reject retry decisions that scan the whole attempt instead of the terminal failure."""
    needle = '            if ! grep -Eq "$QUALITY_TRANSIENT_RE" "$QUALITY_TERMINAL_LOG"; then\n'
    if needle not in android:
        # RED fixture until terminal-failure scoping is implemented.
        return android, coverage
    replacement = '            if ! grep -Eq "$QUALITY_TRANSIENT_RE" "$QUALITY_ATTEMPT_LOG"; then\n'
    return android.replace(needle, replacement, 1), coverage


def hide_connected_validation_inside_echo(android: str, coverage: str):
    """Replace the real connected Gradle invocation with inert echoed text."""
    needle = """          gradle :app:connectedDebugAndroidTest \\\n            --build-cache \\\n            --configuration-cache \\\n            --configuration-cache-problems=fail \\\n            --stacktrace
"""
    replacement = """          echo 'gradle :app:connectedDebugAndroidTest --build-cache --configuration-cache --configuration-cache-problems=fail --stacktrace'
"""
    if needle not in android:
        raise SystemExit("Fixture drift: connected validation invocation not found")
    return android.replace(needle, replacement, 1), coverage


def move_connected_cache_flags_to_dry_run(android: str, coverage: str):
    """Keep cache flags only on a dry-run while weakening the real device test."""
    needle = """          gradle :app:connectedDebugAndroidTest \\\n            --build-cache \\\n            --configuration-cache \\\n            --configuration-cache-problems=fail \\\n            --stacktrace
"""
    replacement = """          gradle :app:connectedDebugAndroidTest \\\n            --build-cache \\\n            --configuration-cache \\\n            --configuration-cache-problems=fail \\\n            --dry-run \\\n            --stacktrace
          gradle :app:connectedDebugAndroidTest \\\n            --build-cache \\\n            --stacktrace
"""
    if needle not in android:
        raise SystemExit("Fixture drift: connected validation invocation not found")
    return android.replace(needle, replacement, 1), coverage


def remove_connected_configuration_cache_flag(android: str, coverage: str):
    """Remove the positive Configuration Cache enablement from connected validation."""
    needle = """            --configuration-cache \\\n"""
    step_name = "      - name: Verify MainActivity presentation wiring on API 35\n"
    start = android.find(step_name)
    if start < 0:
        raise SystemExit("Fixture drift: connected validation step not found")
    end = android.find("\n      - name:", start + len(step_name))
    if end < 0:
        end = len(android)
    step = android[start:end]
    if needle not in step:
        raise SystemExit("Fixture drift: connected Configuration Cache flag not found")
    return android[:start] + step.replace(needle, "", 1) + android[end:], coverage


def require_blocking_shell_line(script: str, expected: str, label: str) -> None:
    """Require an active, exact verifier command under fail-fast shell semantics."""
    active_lines = [
        line.strip()
        for line in script.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if "set -euo pipefail" not in active_lines:
        raise SystemExit(f"Phase 3 block 3: {label} verifier is not fail-fast")
    if expected not in active_lines:
        raise SystemExit(f"Phase 3 block 3: {label} assertion is not active and blocking")

def require_exact_artifact_reuse_contract(android_text: str) -> None:
    """Validate the actual producer, consumer and exact provenance relationship."""
    workflow = yaml.safe_load(android_text)
    jobs = workflow.get("jobs", {})
    quality_steps = jobs.get("quality", {}).get("steps", [])
    producer = next((step for step in quality_steps if step.get("name") == "Record Phase 3 artifact provenance"), None)
    if not producer:
        raise SystemExit("Phase 3 block 3: artifact provenance producer is missing")
    producer_env = producer.get("env", {})
    producer_run = producer.get("run", "")
    if producer_env.get("PHASE3_RUN_ID") != "${{ github.run_id }}":
        raise SystemExit("Phase 3 block 3: producer is not bound to the current run")
    if producer_env.get("PHASE3_SHA") != "${{ github.sha }}":
        raise SystemExit("Phase 3 block 3: producer is not bound to the current SHA")
    if "apk_sha256=" not in producer_run:
        raise SystemExit("Phase 3 block 3: producer does not record artifact integrity")
    build_steps = jobs.get("build", {}).get("steps", [])
    download = next((step for step in build_steps if step.get("name") == "Download exact-run quality artifact"), None)
    if not download or not str(download.get("uses", "")).startswith("actions/download-artifact@"):
        raise SystemExit("Phase 3 block 3: exact-run artifact consumer is missing")
    download_with = download.get("with", {})
    if download_with.get("name") != "gamehub-ultra-debug":
        raise SystemExit("Phase 3 block 3: consumer selected the wrong artifact")
    if download_with.get("run-id") != "${{ github.run_id }}":
        raise SystemExit("Phase 3 block 3: consumer is not restricted to the current run")
    upload = next((step for step in quality_steps if step.get("name") == "Upload debug APK"), None)
    if not upload:
        raise SystemExit("Phase 3 block 3: debug artifact upload is missing")
    upload_with = upload.get("with", {})
    if upload_with.get("compression-level") != 0:
        raise SystemExit("Phase 3 block 3 cut 2: reusable APK must skip redundant artifact compression")
    verifier = next((step for step in build_steps if step.get("name") == "Verify Phase 3 artifact provenance"), None)
    if not verifier:
        raise SystemExit("Phase 3 block 3: provenance verifier is missing")
    verifier_env = verifier.get("env", {})
    verifier_run = verifier.get("run", "")
    if verifier_env.get("EXPECTED_RUN_ID") != "${{ github.run_id }}":
        raise SystemExit("Phase 3 block 3: downloaded run provenance is not bound")
    if verifier_env.get("EXPECTED_SHA") != "${{ github.sha }}":
        raise SystemExit("Phase 3 block 3: downloaded SHA provenance is not bound")
    require_blocking_shell_line(
        verifier_run,
        'grep -Fxq "run_id=$EXPECTED_RUN_ID" "$PROVENANCE"',
        "downloaded run provenance",
    )
    require_blocking_shell_line(
        verifier_run,
        'grep -Fxq "sha=$EXPECTED_SHA" "$PROVENANCE"',
        "downloaded SHA provenance",
    )
    require_blocking_shell_line(
        verifier_run,
        'test "$ACTUAL_SHA256" = "$RECORDED_SHA256"',
        "downloaded artifact integrity",
    )


def require_artifact_reuse_mutations_rejected(android_text: str) -> None:
    """Prove unsafe artifact provenance changes cannot satisfy the contract."""
    assertions = (
        'grep -Fxq "run_id=$EXPECTED_RUN_ID" "$PROVENANCE"',
        'grep -Fxq "sha=$EXPECTED_SHA" "$PROVENANCE"',
        'test "$ACTUAL_SHA256" = "$RECORDED_SHA256"',
    )
    commented = android_text
    masked = android_text
    for assertion in assertions:
        commented = commented.replace(assertion, f"# {assertion}", 1)
        masked = masked.replace(assertion, f"{assertion} || true", 1)

    mutations = {
        "foreign run": android_text.replace("run-id: ${{ github.run_id }}", "run-id: ${{ github.event.workflow_run.id }}", 1),
        "mismatched SHA": android_text.replace('EXPECTED_SHA: ${{ github.sha }}', 'EXPECTED_SHA: foreign-sha', 1),
        "unverified run": android_text.replace('grep -Fxq "run_id=$EXPECTED_RUN_ID" "$PROVENANCE"', 'grep -Fq "run_id=" "$PROVENANCE"', 1),
        "commented verifier assertions": commented,
        "masked verifier assertions": masked,
    }
    for label, mutated in mutations.items():
        if mutated == android_text:
            raise SystemExit(f"Phase 3 block 3: mutation fixture drift for {label}")
        try:
            require_exact_artifact_reuse_contract(mutated)
        except SystemExit:
            continue
        raise SystemExit(f"Phase 3 block 3: unsafe {label} mutation was accepted")


def main() -> None:
    """Run CI-contract mutations and verify the optimized quality graph shape."""
    run_current_contract_must_pass()
    require_shadow_metrics_contract()
    # Phase 3 block 4 cut 2: keep the aggregate build gate as the only
    # in-workflow fan-in. Performance metrics are collected post-run by the
    # read-only shadow workflow, so they cannot delay the blocking CI path.
    android = yaml.safe_load(ANDROID.read_text(encoding="utf-8"))
    jobs = android.get("jobs", {})
    build_needs = jobs.get("build", {}).get("needs", [])
    if set(build_needs) != {"quality", "device-validation"}:
        raise SystemExit("Phase 3 block 4 cut 2: build fan-in gate changed")
    if "metrics" in jobs:
        raise SystemExit("Phase 3 block 4 cut 2: inline metrics still delays Android workflow completion")
    android = ANDROID.read_text(encoding="utf-8")
    coverage = COVERAGE.read_text(encoding="utf-8")
    commented_quality_marker_before_active_step(android, coverage)
    run_mutation("aggregate build loses always() fan-in condition", remove_build_always_condition)
    run_mutation("aggregate build loses device-validation result assertion", remove_device_validation_result_assertion)
    run_mutation("release artifact upload loses research-key guard", remove_release_upload_research_key_guard)
    run_mutation("release research key accepts whitespace-only secret", weaken_release_research_key_whitespace_guard)
    run_mutation("trusted release empty-key branch disabled", disable_trusted_release_empty_key_failure_branch)
    require_dependabot_prs_are_untrusted(android, coverage)
    run_mutation("Dependabot PR trusted guard removed", remove_dependabot_untrusted_guard)
    run_mutation("Android unit tests removed but text left in a comment", remove_unit_test_but_leave_comment)
    run_mutation("quality gate made advisory with continue-on-error", make_quality_advisory)
    run_mutation("coverage gate made advisory with continue-on-error", make_coverage_advisory)
    run_mutation("local patch coverage gate made advisory", make_local_patch_coverage_advisory)
    run_mutation("local patch coverage replaced by inert echo", replace_local_patch_gate_with_echo)
    run_mutation("local patch coverage failure masked with || true", mask_local_patch_coverage_with_or_true)
    run_mutation("coverage command commented out", comment_out_coverage_command)
    run_mutation("fake quality step hidden inside run heredoc", hide_quality_step_inside_run_heredoc)
    run_mutation("unit test removed from Gradle but echoed later", remove_unit_test_but_echo_name)
    run_mutation("quality gate made advisory with expression", make_quality_advisory_with_expression)
    run_mutation("quality Gradle pipeline masked with || true", mask_quality_gradle_with_or_true)
    run_mutation("quality Gradle pipeline masked with fused ||true", mask_quality_gradle_with_fused_or_true)
    run_mutation("quality Gradle command masked with plain || true", mask_quality_gradle_plain_or_true)
    run_mutation("quality transient retry removed", remove_quality_transient_retry)
    run_mutation("quality retry bound spoofed by stale comment", spoof_quality_retry_bound_with_comment)
    run_mutation("quality retry scans full attempt history", scan_full_quality_history_for_retry)
    run_mutation("connected validation hidden inside echo", hide_connected_validation_inside_echo)
    run_mutation("connected validation loses Configuration Cache enablement", remove_connected_configuration_cache_flag)
    run_mutation("connected cache flags moved to dry-run only", move_connected_cache_flags_to_dry_run)
    run_mutation("Configuration Cache reuse assertion removed", remove_configuration_cache_reuse_assertion)
    run_mutation("Partial release/performance graph duplicated", duplicate_partial_release_graph)
    run_mutation("Full release graph duplicated without Configuration Cache flags", duplicate_full_release_graph_without_configuration_cache)

    # Phase 2 block 4 starts by proving the current workflow still executes the
    # Quality executes assemble+lint exactly once. Configuration Cache remains
    # enabled on that real graph; duplicate dry-run probes are forbidden.
    quality_start, quality_end = quality_step_bounds(android)
    quality_script = android[quality_start:quality_end]
    if quality_script.count(":app:assembleDebug") != 1:
        raise SystemExit("Quality must execute exactly one assemble+lint graph")
    if "--dry-run" in quality_script:
        raise SystemExit("Quality must not reintroduce duplicate dry-run Gradle probes")
    if "--max-workers=8" not in quality_script:
        raise SystemExit("Quality must preserve the tuned worker bound")
    if ":app:testDebugUnitTest" in quality_script:
        raise SystemExit("Quality must not duplicate coverage-owned unit tests")


    # Phase 3 block 5 cut 1: connected debug validation must reuse the
    # already-running API 35 emulator without rebuilding the debug APK that
    # quality already produced. The device gate remains authoritative; only
    # duplicate host-side assembly is forbidden.
    device_steps = jobs.get("device-validation", {}).get("steps", [])
    wiring = next(
        (step for step in device_steps if step.get("name") == "Verify MainActivity presentation wiring on API 35"),
        None,
    )
    if not wiring:
        raise SystemExit("Phase 3 block 5 cut 1: MainActivity device validation is missing")
    wiring_run = str(wiring.get("run", ""))
    if ":app:connectedDebugAndroidTest" not in wiring_run:
        raise SystemExit("Phase 3 block 5 cut 1: connected MainActivity validation changed")
    if "--no-build-cache" in wiring_run:
        raise SystemExit("Phase 3 block 5 cut 1: connected validation disabled build-cache reuse")
    if "--build-cache" not in wiring_run:
        raise SystemExit("Phase 3 block 5 cut 1: connected validation must preserve build-cache reuse")
    if "--no-configuration-cache" in wiring_run:
        raise SystemExit(
            "Phase 3 block 5 cut 1: connected validation still disables Configuration Cache"
        )

    # Phase 3 block 5 cut 2 RED: the real Baseline Profile/Macrobenchmark
    # invocation must preserve every performance case while enabling strict
    # Configuration Cache reuse. This intentionally fails until GREEN updates
    # the performance command.
    performance = next(
        (step for step in device_steps if step.get("name") == "Run baseline profile and macrobenchmarks on API 35"),
        None,
    )
    if not performance:
        raise SystemExit("Phase 3 block 5 cut 2: performance validation is missing")
    performance_run = str(performance.get("run", ""))
    required_performance_cache_flags = (
        "--build-cache",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
    )
    missing_performance_cache_flags = [
        flag for flag in required_performance_cache_flags if flag not in performance_run
    ]
    if missing_performance_cache_flags or "--no-configuration-cache" in performance_run:
        raise SystemExit(
            "Phase 3 block 5 cut 2 RED: performance validation must use strict "
            f"Configuration Cache; missing={missing_performance_cache_flags!r}"
        )

    # Phase 3 block 3 cut 1: validate the real producer/consumer relationship.
    android_workflow = ANDROID.read_text(encoding="utf-8")
    require_exact_artifact_reuse_contract(android_workflow)
    require_artifact_reuse_mutations_rejected(android_workflow)

    # Phase 3 block 3 cut 3: keep the aggregate gate blocking while ensuring
    # it never re-enters Gradle or rebuilds an artifact already produced by quality.
    workflow = yaml.safe_load(android_workflow)
    build_job = workflow.get("jobs", {}).get("build", {})
    build_steps = build_job.get("steps", [])
    if set(build_job.get("needs", [])) != {"quality", "device-validation"}:
        raise SystemExit("Phase 3 block 3 cut 3: aggregate build gate dependencies changed")
    build_scripts = "\n".join(str(step.get("run", "")) for step in build_steps)
    if "gradle " in build_scripts or "./gradlew" in build_scripts:
        raise SystemExit("Phase 3 block 3 cut 3: aggregate gate must not rebuild Gradle outputs")
    if not any(step.get("name") == "Verify Phase 3 artifact provenance" for step in build_steps):
        raise SystemExit("Phase 3 block 3 cut 3: aggregate gate lost artifact verification")

    # Phase 3 block 4 cut 2: post-run metrics must retain exact parent
    # provenance and must continue to require the blocking Android build job.
    shadow_text = SHADOW_METRICS.read_text(encoding="utf-8")
    if 'REQUIRED_JOB="build"' not in shadow_text:
        raise SystemExit("Phase 3 block 4 cut 2: shadow Android metrics lost required build provenance")

    # Phase 2 block 6 is enforced by the parsed CI safety contract above.
    # Mutations prove both the reuse assertion and duplicate partial graphs fail.

    print("CI safety contract regression tests passed.")


if __name__ == "__main__":
    main()
