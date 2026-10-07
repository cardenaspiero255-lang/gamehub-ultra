#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import shlex
import subprocess
from pathlib import Path
from typing import Any

ANDROID = Path(".github/workflows/android.yml")
COVERAGE = Path(".github/workflows/coverage.yml")
COVERAGE_POST = Path(".github/workflows/coverage-post-processing.yml")
SHADOW_METRICS = Path(".github/workflows/ci-metrics-shadow.yml")


def fail(message: str) -> None:
    """Fail the contract with a GitHub Actions annotation."""
    print(f"::error::{message}")
    raise SystemExit(1)


def load_workflow(path: Path) -> dict[str, Any]:
    """Parse a workflow with Ruby's real YAML parser (Psych), or fail closed."""
    if not path.is_file():
        fail(f"Missing workflow: {path}")

    ruby = r'''
require "yaml"
require "json"

begin
  parsed = YAML.safe_load(
    File.read(ARGV.fetch(0)),
    permitted_classes: [],
    permitted_symbols: [],
    aliases: true
  )
  abort("workflow root is not a mapping") unless parsed.is_a?(Hash)
  STDOUT.write(JSON.generate(parsed))
rescue LoadError => e
  warn("YAML parser unavailable: #{e.message}")
  exit(70)
rescue Psych::Exception => e
  warn("YAML parse failed: #{e.message}")
  exit(71)
end
'''
    try:
        result = subprocess.run(
            ["ruby", "-e", ruby, str(path)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except FileNotFoundError:
        fail("Real YAML parser unavailable: Ruby/Psych is required")

    if result.returncode != 0:
        fail(
            f"Unable to parse {path} with Ruby/Psych "
            f"(exit {result.returncode}): {result.stderr.strip()}"
        )

    try:
        parsed = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        fail(f"YAML parser returned invalid JSON for {path}: {exc}")

    if not isinstance(parsed, dict):
        fail(f"Workflow root is not a mapping: {path}")
    return parsed


def job(workflow: dict[str, Any], job_name: str) -> dict[str, Any]:
    """Return one required workflow job and reject malformed structures."""
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict):
        fail("Workflow jobs mapping is missing")
    value = jobs.get(job_name)
    if not isinstance(value, dict):
        fail(f"Missing required job: {job_name}")

    defaults = value.get("defaults")
    if isinstance(defaults, dict):
        run_defaults = defaults.get("run")
        if isinstance(run_defaults, dict) and run_defaults.get("working-directory") not in (None, ""):
            fail(f"Required job changes working-directory: {job_name}")
    return value


def step(job_value: dict[str, Any], job_name: str, step_name: str) -> dict[str, Any]:
    """Locate exactly one named step inside the expected job's real steps list."""
    steps = job_value.get("steps")
    if not isinstance(steps, list):
        fail(f"Job has no steps list: {job_name}")

    matches = [
        candidate
        for candidate in steps
        if isinstance(candidate, dict) and candidate.get("name") == step_name
    ]
    if len(matches) != 1:
        fail(
            f"Expected exactly one active step {step_name!r} in job {job_name!r}; "
            f"found {len(matches)}"
        )
    return matches[0]


def normalized_if(value: Any) -> str | None:
    """Normalize a workflow if-expression for exact contract comparisons."""
    if value is None:
        return None
    if not isinstance(value, str):
        fail(f"Workflow if condition must be a string, found {type(value).__name__}")
    return value.strip()


def require_blocking_semantics(step_value: dict[str, Any], label: str) -> None:
    """Require failures to propagate; expressions are intentionally rejected."""
    value = step_value.get("continue-on-error")
    if value not in (None, False):
        fail(
            f"Required gate must be blocking with continue-on-error absent/false: "
            f"{label}: {value!r}"
        )


def require_continue_on_error_true(step_value: dict[str, Any], label: str) -> None:
    """Require one intentionally best-effort step to remain explicitly true."""
    value = step_value.get("continue-on-error")
    if value is not True:
        fail(f"Expected continue-on-error: true for {label}, found {value!r}")


def require_no_working_directory(step_value: dict[str, Any], label: str) -> None:
    """Prevent required commands from being redirected into another directory."""
    if step_value.get("working-directory") not in (None, ""):
        fail(f"Required gate changes working-directory: {label}")


def logical_shell_commands(script: str) -> list[str]:
    """Join backslash continuations into executable shell command lines."""
    commands: list[str] = []
    current = ""
    for raw in script.splitlines():
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue

        if current:
            current += " " + stripped
        else:
            current = stripped

        if current.endswith("\\"):
            current = current[:-1].rstrip()
            continue

        commands.append(current)
        current = ""

    if current:
        commands.append(current)
    return commands


def gradle_commands(script: str) -> list[list[str]]:
    """Return tokenized real Gradle invocations, excluding unrelated shell code."""
    result: list[list[str]] = []
    for command in logical_shell_commands(script):
        stripped = command.lstrip()
        if not (
            stripped == "gradle"
            or stripped.startswith("gradle ")
            or stripped == "./gradlew"
            or stripped.startswith("./gradlew ")
        ):
            continue

        try:
            tokens = shlex.split(command, comments=True, posix=True)
        except ValueError as exc:
            fail(f"Unable to parse Gradle command in required gate: {exc}: {command!r}")
        if not tokens:
            continue

        if any(token == "||" or token.startswith("||") for token in tokens):
            fail(f"Required Gradle command masks failure with ||: {command!r}")

        if "|" in tokens:
            pipe = tokens.index("|")
            tail = tokens[pipe + 1:]
            if not tail or tail[0] != "tee":
                fail(f"Required Gradle command has unsupported pipeline: {command!r}")
            if any(
                token in {"|", "&&", "&"}
                or token == "||"
                or token.startswith("||")
                for token in tail[1:]
            ):
                fail(f"Required Gradle pipeline changes failure semantics: {command!r}")
            tokens = tokens[:pipe]

        result.append(tokens)
    return result


def normalized_gradle_invocation(tokens: list[str]) -> list[str]:
    """Keep every Gradle argument while dropping only shell redirection plumbing."""
    normalized: list[str] = []
    for token in tokens:
        if token in {"|", ">", ">>", "1>", "1>>", "2>", "2>>", "2>&1"}:
            break
        if token.startswith((">", "1>", "2>")):
            break
        normalized.append(token)
    return normalized


def require_gradle_invocation(
    step_value: dict[str, Any],
    label: str,
    *,
    tasks: tuple[str, ...],
    args: tuple[str, ...] = (),
) -> None:
    """Require all named tasks/arguments on the same executable Gradle command."""
    script = step_value.get("run")
    if not isinstance(script, str):
        fail(f"Required Gradle gate has no run script: {label}")

    for tokens in gradle_commands(script):
        token_set = set(tokens)
        if all(task in token_set for task in tasks) and all(arg in token_set for arg in args):
            return

    fail(
        f"Required Gradle invocation missing in {label}; "
        f"tasks={tasks!r}, args={args!r}"
    )


def require_run_fragment(step_value: dict[str, Any], label: str, fragment: str) -> None:
    """Require an executed run script to contain a critical verification fragment."""
    script = step_value.get("run")
    if not isinstance(script, str):
        fail(f"Required run gate has no script: {label}")
    if fragment not in script:
        fail(f"Required gate lost verification fragment {fragment!r}: {label}")


def require_shell_command(step_value: dict[str, Any], label: str, expected: tuple[str, ...]) -> None:
    """Require one exact executable shell command, not merely matching text."""
    script = step_value.get("run")
    if not isinstance(script, str):
        fail(f"Required run gate has no script: {label}")

    for command in logical_shell_commands(script):
        try:
            tokens = shlex.split(command, comments=True, posix=True)
        except ValueError as exc:
            fail(f"Unable to parse shell command in required gate: {exc}: {command!r}")
        if tuple(tokens) == expected:
            return

    fail(f"Required executable shell command missing in {label}: {expected!r}")


def require_step(
    workflow: dict[str, Any],
    job_name: str,
    step_name: str,
    *,
    shell: str | None = None,
    allowed_if: str | None = None,
    best_effort: bool = False,
    uses_prefix: str | None = None,
) -> dict[str, Any]:
    """Validate one real step's placement and failure semantics."""
    job_value = job(workflow, job_name)
    value = step(job_value, job_name, step_name)

    require_no_working_directory(value, f"{job_name}/{step_name}")
    if best_effort:
        require_continue_on_error_true(value, f"{job_name}/{step_name}")
    else:
        require_blocking_semantics(value, f"{job_name}/{step_name}")

    actual_if = normalized_if(value.get("if"))
    if allowed_if is None:
        if actual_if not in (None, ""):
            fail(
                f"Required gate has unexpected if condition: "
                f"{job_name}/{step_name}: {actual_if!r}"
            )
    elif actual_if != allowed_if:
        fail(
            f"Required gate has unexpected if condition: "
            f"{job_name}/{step_name}: {actual_if!r}; expected {allowed_if!r}"
        )

    if shell is not None and value.get("shell") != shell:
        fail(
            f"Required gate changed shell: {job_name}/{step_name}: "
            f"{value.get('shell')!r}; expected {shell!r}"
        )

    if uses_prefix is not None:
        uses = value.get("uses")
        if not isinstance(uses, str) or not uses.startswith(uses_prefix):
            fail(
                f"Required action changed for {job_name}/{step_name}: {uses!r}"
            )

    return value


def require_concurrency(workflow: dict[str, Any], label: str) -> None:
    """Require stale-run cancellation to remain explicitly enabled."""
    concurrency = workflow.get("concurrency")
    if not isinstance(concurrency, dict):
        fail(f"Missing concurrency mapping: {label}")
    if concurrency.get("cancel-in-progress") is not True:
        fail(f"Stale-run cancellation disabled: {label}")


def main() -> None:
    """Fail closed if the accelerated CI drops any blocking validation."""
    android = load_workflow(ANDROID)
    coverage = load_workflow(COVERAGE)
    shadow_metrics = load_workflow(SHADOW_METRICS)

    require_concurrency(android, "Android workflow")
    require_concurrency(coverage, "coverage workflow")

    android_jobs = android.get("jobs")
    if not isinstance(android_jobs, dict):
        fail("Android workflow jobs mapping is missing")
    if "metrics" in android_jobs:
        fail("Android metrics must stay post-run and off the blocking path")

    for step_name in (
        "Verify benchmark fast-path contract",
        "Verify CI safety contract regression coverage",
        "Test local patch coverage gate",
        "Verify CI safety contract",
        "Test Sentry release auth fallback",
        "Test CI performance metrics",
    ):
        require_step(
            android,
            "quality-contracts",
            step_name,
            shell="bash",
        )

    lint = require_step(
        android,
        "quality-lint",
        "Run fast quality gates",
        shell="bash",
    )
    require_gradle_invocation(
        lint,
        "quality-lint/Run fast quality gates",
        tasks=(":app:lintDebug",),
        args=(
            "--build-cache",
            "--parallel",
            "--max-workers=8",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "--console=plain",
        ),
    )
    lint_run = str(lint.get("run", ""))
    for fragment in (
        "QUALITY_MAX_ATTEMPTS=3",
        "QUALITY_RETRY_BASE_SECONDS=5",
        'QUALITY_TERMINAL_LOG="$RUNNER_TEMP/quality-gates-terminal.log"',
        'QUALITY_TRANSIENT_RE=',
        'if ! grep -Eq "$QUALITY_TRANSIENT_RE" "$QUALITY_TERMINAL_LOG"; then',
        'exit "$quality_status"',
    ):
        require_run_fragment(lint, "quality bounded transient retry", fragment)
    if "--dry-run" in lint_run:
        fail("Quality lint must stay single-pass without duplicate dry-run Gradle probes")

    quality_job = job(android, "quality")
    quality_needs = quality_job.get("needs")
    if not isinstance(quality_needs, list) or set(quality_needs) != {
        "quality-contracts",
        "quality-lint",
    }:
        fail("quality fan-in lost a required shard")
    if normalized_if(quality_job.get("if")) != "always()":
        fail("quality fan-in must use if: always()")
    quality_gate = require_step(
        android,
        "quality",
        "Verify quality shards",
        shell="bash",
    )
    for dependency in ("quality-contracts", "quality-lint"):
        require_shell_command(
            quality_gate,
            "quality fan-in result",
            (
                "test",
                f"${{{{ needs.{dependency}.result }}}}",
                "=",
                "success",
            ),
        )

    shard_job = job(android, "device-validation-shard")
    strategy = shard_job.get("strategy")
    if not isinstance(strategy, dict):
        fail("device validation strategy is missing")
    if strategy.get("fail-fast") is not False:
        fail("device validation matrix must keep fail-fast disabled")
    if strategy.get("max-parallel") != 4:
        fail("device validation matrix must keep four-way parallel execution")
    matrix = strategy.get("matrix")
    if not isinstance(matrix, dict):
        fail("device validation matrix definition is missing")
    if set(matrix.get("shard", [])) != {"release", "ui", "baseline", "macro"}:
        fail("device validation lost release/ui/baseline/macro coverage")

    retry_test = require_step(
        android,
        "device-validation-shard",
        "Test Android SDK retry safety",
        shell="bash",
        allowed_if="matrix.shard == 'release'",
    )
    require_run_fragment(
        retry_test,
        "runtime SDK retry safety",
        "test-android-sdk-retry-safety.sh",
    )

    prebuild = require_step(
        android,
        "device-validation-shard",
        "Build shard while installing emulator runtime",
        shell="bash",
    )
    for fragment in (
        "install-android-runtime-sdk.sh",
        "SDK_PID=$!",
        "BUILD_PID=$!",
        ":app:assembleRelease",
        ":app:bundleRelease",
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest",
        ":app:assembleNonMinifiedRelease",
        ":baseline-profile:assembleNonMinifiedRelease",
        "--max-workers=8",
        "EMULATOR_LAUNCH_EPOCH=",
        "-no-window",
        "-no-snapshot-load",
        "-no-snapshot-save",
    ):
        require_run_fragment(prebuild, "parallel device prebuild", fragment)

    readiness = require_step(
        android,
        "device-validation-shard",
        "Wait for API 35 emulator readiness",
        shell="bash",
    )
    for fragment in (
        "sys.boot_completed",
        "ro.build.version.sdk",
        'grep -Fxq "35"',
    ):
        require_run_fragment(readiness, "API 35 readiness", fragment)

    validate = require_step(
        android,
        "device-validation-shard",
        "Validate shard on API 35",
        shell="bash",
    )
    for fragment in (
        "adb install",
        "adb install -r",
        "adb uninstall",
        ":app:connectedDebugAndroidTest",
        ":baseline-profile:connectedNonMinifiedReleaseAndroidTest",
        'RULE="BaselineProfile"',
        'RULE="Macrobenchmark"',
        "Expected at least {minimum} {label} cases",
        "Required performance tests were skipped",
    ):
        require_run_fragment(validate, "device validation coverage", fragment)
    validate_run = str(validate.get("run", ""))
    if validate_run.count(":baseline-profile:connectedNonMinifiedReleaseAndroidTest") != 1:
        fail("performance shard script must contain exactly one instrumentation command")

    noise = require_step(
        android,
        "device-validation-shard",
        "Reject known CAR-29 emulator noise",
        shell="bash",
        allowed_if="always()",
    )
    for fragment in (
        "Failed to start Emulator console",
        "stop: Not implemented",
    ):
        require_run_fragment(noise, "CAR-29 emulator-noise gate", fragment)

    research = require_step(
        android,
        "device-validation-shard",
        "Verify release research configuration",
        shell="bash",
        allowed_if="matrix.shard == 'release'",
    )
    research_env = research.get("env")
    expected_trusted_release_context = (
        "${{ github.event_name != 'pull_request' || "
        "(github.actor != 'dependabot[bot]' && "
        "github.event.pull_request.head.repo.full_name == github.repository) }}"
    )
    if not isinstance(research_env, dict):
        fail("release research configuration env is missing")
    if research_env.get("TRUSTED_RELEASE_CONTEXT") != expected_trusted_release_context:
        fail("TRUSTED_RELEASE_CONTEXT guard changed")
    for fragment in (
        'RESEARCH_RELEASE_READY=false',
        'research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"',
        'exit 1',
        'RESEARCH_RELEASE_READY=true',
    ):
        require_run_fragment(research, "release research-key guard", fragment)

    sentry_build = require_step(
        android,
        "device-validation-shard",
        "Build distributable release APK and AAB with Sentry",
        shell="bash",
        allowed_if="success() && matrix.shard == 'release' && github.event_name != 'pull_request'",
    )
    require_gradle_invocation(
        sentry_build,
        "trusted Sentry release build",
        tasks=(":app:assembleRelease", ":app:bundleRelease"),
        args=("--rerun-tasks",),
    )
    require_step(
        android,
        "device-validation-shard",
        "Register Sentry release and GitHub commit",
        shell="bash",
        allowed_if="success() && matrix.shard == 'release' && github.event_name != 'pull_request'",
        best_effort=True,
    )

    provenance = require_step(
        android,
        "device-validation-shard",
        "Record Phase 3 artifact provenance",
        shell="bash",
        allowed_if="matrix.shard == 'ui'",
    )
    provenance_env = provenance.get("env")
    if not isinstance(provenance_env, dict):
        fail("debug artifact provenance env is missing")
    if provenance_env.get("PHASE3_RUN_ID") != "${{ github.run_id }}":
        fail("debug artifact provenance lost current run binding")
    if provenance_env.get("PHASE3_SHA") != "${{ github.sha }}":
        fail("debug artifact provenance lost current SHA binding")

    debug_upload = require_step(
        android,
        "device-validation-shard",
        "Upload debug APK",
        uses_prefix="actions/upload-artifact@",
        allowed_if="matrix.shard == 'ui'",
    )
    debug_with = debug_upload.get("with")
    if not isinstance(debug_with, dict):
        fail("debug artifact upload inputs are missing")
    if debug_with.get("name") != "gamehub-ultra-debug":
        fail("debug artifact name changed")
    if debug_with.get("compression-level") != 0:
        fail("debug APK must keep redundant compression disabled")

    release_upload = require_step(
        android,
        "device-validation-shard",
        "Upload installable release outputs",
        uses_prefix="actions/upload-artifact@",
        allowed_if="matrix.shard == 'release' && env.RESEARCH_RELEASE_READY == 'true'",
    )
    release_with = release_upload.get("with")
    if not isinstance(release_with, dict):
        fail("release artifact upload inputs are missing")
    release_paths = str(release_with.get("path", ""))
    for required_path in (
        "app/build/outputs/apk/release/**/*.apk",
        "app/build/outputs/bundle/release/**/*.aab",
    ):
        if required_path not in release_paths:
            fail(f"release artifact output lost: {required_path}")

    device_job = job(android, "device-validation")
    device_needs = device_job.get("needs")
    if not isinstance(device_needs, list) or set(device_needs) != {
        "device-validation-shard"
    }:
        fail("device-validation fan-in lost matrix dependency")
    if normalized_if(device_job.get("if")) != "always()":
        fail("device-validation fan-in must use if: always()")
    device_gate = require_step(
        android,
        "device-validation",
        "Verify device validation shards",
        shell="bash",
    )
    require_shell_command(
        device_gate,
        "device fan-in result",
        (
            "test",
            "${{ needs.device-validation-shard.result }}",
            "=",
            "success",
        ),
    )

    coverage_generate = require_step(
        coverage,
        "coverage",
        "Generate debug unit-test coverage",
        shell="bash",
    )
    require_gradle_invocation(
        coverage_generate,
        "coverage/Generate debug unit-test coverage",
        tasks=(":app:createDebugUnitTestCoverageReport",),
        args=("--build-cache", "--parallel"),
    )
    coverage_verify = require_step(
        coverage,
        "coverage",
        "Verify coverage XML report",
        shell="bash",
    )
    for fragment in ("report.xml", "test -s"):
        require_run_fragment(
            coverage_verify,
            "coverage XML verification",
            fragment,
        )
    local_patch = require_step(
        coverage,
        "coverage",
        "Enforce local patch coverage",
        shell="bash",
    )
    for fragment in (
        ".github/scripts/local_patch_coverage.py",
        "report.xml",
        "--min-patch-line 90",
    ):
        require_run_fragment(local_patch, "local patch coverage", fragment)

    coverage_text = COVERAGE.read_text(encoding="utf-8")
    if "codecov/codecov-action@" in coverage_text:
        fail("Codecov publishing must stay off the blocking coverage path")

    coverage_post = load_workflow(COVERAGE_POST)
    post_on = coverage_post.get("on")
    if not isinstance(post_on, dict):
        post_on = coverage_post.get("true")
    if not isinstance(post_on, dict):
        post_on = coverage_post.get(True)
    if not isinstance(post_on, dict):
        fail("coverage post-processing trigger mapping is missing")
    workflow_run = post_on.get("workflow_run")
    if not isinstance(workflow_run, dict):
        fail("coverage post-processing workflow_run trigger is missing")
    if workflow_run.get("workflows") != ["Unit Test Coverage"]:
        fail("coverage post-processing target workflow changed")
    if workflow_run.get("types") != ["completed"]:
        fail("coverage post-processing must trigger only on completion")
    publish_job = job(coverage_post, "publish")
    download = step(
        publish_job,
        "publish",
        "Download exact coverage artifact",
    )
    if not str(download.get("uses", "")).startswith("actions/download-artifact@"):
        fail("coverage report download action is missing")
    download_with = download.get("with")
    if not isinstance(download_with, dict):
        fail("coverage report download inputs are missing")
    if download_with.get("run-id") != "${{ github.event.workflow_run.id }}":
        fail("coverage report download lost triggering-run binding")

    codecov = step(
        publish_job,
        "publish",
        "Upload coverage to Codecov",
    )
    if not str(codecov.get("uses", "")).startswith("codecov/codecov-action@"):
        fail("Codecov post-processing action is missing")
    require_continue_on_error_true(codecov, "Codecov post-processing upload")
    codecov_with = codecov.get("with")
    if not isinstance(codecov_with, dict):
        fail("Codecov inputs are missing")
    expected_codecov = {
        "override_commit": "${{ github.event.workflow_run.head_sha }}",
        "override_branch": "${{ github.event.workflow_run.head_branch }}",
        "override_pr": "${{ github.event.workflow_run.pull_requests[0].number || '' }}",
        "override_build": "${{ github.event.workflow_run.id }}",
    }
    for key, expected in expected_codecov.items():
        if codecov_with.get(key) != expected:
            fail(f"Codecov {key} lost triggering-run provenance")

    build_job = job(android, "build")
    build_needs = build_job.get("needs")
    if not isinstance(build_needs, list) or set(build_needs) != {
        "quality",
        "device-validation",
    }:
        fail("aggregate build fan-in dependencies changed")
    if normalized_if(build_job.get("if")) != "always()":
        fail("aggregate build fan-in must use if: always()")
    aggregate_gate = require_step(
        android,
        "build",
        "Verify all Android CI gates",
        shell="bash",
    )
    for dependency in ("quality", "device-validation"):
        require_shell_command(
            aggregate_gate,
            "aggregate build result",
            (
                "test",
                f"${{{{ needs.{dependency}.result }}}}",
                "=",
                "success",
            ),
        )

    debug_download = require_step(
        android,
        "build",
        "Download exact-run debug artifact",
        uses_prefix="actions/download-artifact@",
    )
    debug_download_with = debug_download.get("with")
    if not isinstance(debug_download_with, dict):
        fail("debug artifact consumer inputs are missing")
    if debug_download_with.get("name") != "gamehub-ultra-debug":
        fail("debug artifact consumer selected the wrong artifact")
    if debug_download_with.get("run-id") != "${{ github.run_id }}":
        fail("debug artifact consumer lost current-run binding")

    verifier = require_step(
        android,
        "build",
        "Verify Phase 3 artifact provenance",
        shell="bash",
    )
    for fragment in (
        'grep -Fxq "run_id=$EXPECTED_RUN_ID" "$PROVENANCE"',
        'grep -Fxq "sha=$EXPECTED_SHA" "$PROVENANCE"',
        'test "$ACTUAL_SHA256" = "$RECORDED_SHA256"',
    ):
        require_run_fragment(verifier, "debug artifact provenance", fragment)

    shadow = require_step(
        shadow_metrics,
        "collect",
        "Collect completed parent metrics in shadow mode",
        shell="bash",
    )
    for fragment in (
        'REQUIRED_JOB="build"',
        "--require-completed",
        '--require-job "$REQUIRED_JOB"',
        "--expected-run-id",
        "--expected-head-sha",
        "--expected-workflow",
    ):
        require_run_fragment(shadow, "shadow metrics provenance", fragment)

    print(
        "CI safety contract verified: parallel quality/device validation preserves "
        "all blocking lint, device, release, benchmark, coverage and provenance gates."
    )


if __name__ == "__main__":
    main()
