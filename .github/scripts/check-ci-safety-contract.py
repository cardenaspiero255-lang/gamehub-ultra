#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import shlex
import subprocess
from pathlib import Path
from typing import Any

ANDROID = Path(".github/workflows/android.yml")
SMOKE = Path(".github/workflows/supabase-research-smoke.yml")
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
    """Fail closed if packed CI drops any blocking validation."""
    android = load_workflow(ANDROID)
    smoke = load_workflow(SMOKE)
    coverage = load_workflow(COVERAGE)
    shadow_metrics = load_workflow(SHADOW_METRICS)

    require_concurrency(android, "Android workflow")
    require_concurrency(smoke, "research smoke workflow")
    require_concurrency(coverage, "coverage workflow")

    android_jobs = android.get("jobs")
    if not isinstance(android_jobs, dict):
        fail("Android workflow jobs mapping is missing")
    if "metrics" in android_jobs:
        fail("Android metrics must stay post-run and off the blocking path")

    packed_contracts = require_step(
        android,
        "quality-contract-shard",
        "Run packed CI contract shards",
        shell="bash",
    )
    for fragment in (
        "logical_shards=(0 1 2 3 4 5)",
        "test-benchmark-fast-path.sh",
        "test-ci-safety-contract-regressions.py",
        "test-local-patch-coverage.py",
        "test-ci-safety-contract.sh",
        "test-sentry-release-auth-fallback.py",
        "test-ci-performance-metrics.py",
        'pids+=("$!")',
        'wait "${pids[$index]}"',
        'exit "$status"',
    ):
        require_run_fragment(
            packed_contracts,
            "packed quality contract workers",
            fragment,
        )

    contracts_fan_in = job(android, "quality-contracts")
    if normalized_if(contracts_fan_in.get("if")) != "always()":
        fail("quality-contracts fan-in must use if: always()")
    if contracts_fan_in.get("needs") != "quality-contract-shard":
        fail("quality-contracts fan-in lost packed contract dependency")
    contracts_gate = require_step(
        android,
        "quality-contracts",
        "Verify packed quality contract shards",
        shell="bash",
    )
    require_shell_command(
        contracts_gate,
        "quality-contracts fan-in result",
        (
            "test",
            "${{ needs.quality-contract-shard.result }}",
            "=",
            "success",
        ),
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
            "--max-workers=12",
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
        "android-test-shards",
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
    for dependency in ("quality-contracts", "quality-lint", "android-test-shards"):
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

    bundle_job = job(android, "release-bundle")
    bundle_research = require_step(
        android,
        "release-bundle",
        "Verify release research configuration",
        shell="bash",
    )
    bundle_research_env = bundle_research.get("env")
    if not isinstance(bundle_research_env, dict):
        fail("release bundle research configuration env is missing")
    expected_bundle_trusted_context = (
        "${{ github.event_name != 'pull_request' || "
        "(github.actor != 'dependabot[bot]' && "
        "github.event.pull_request.head.repo.full_name == github.repository) }}"
    )
    if bundle_research_env.get("TRUSTED_RELEASE_CONTEXT") != expected_bundle_trusted_context:
        fail("release bundle TRUSTED_RELEASE_CONTEXT guard changed")
    for fragment in (
        'research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"',
        'exit 1',
    ):
        require_run_fragment(
            bundle_research,
            "release bundle research-key guard",
            fragment,
        )

    bundle_build = require_step(
        android,
        "release-bundle",
        "Build release bundle in parallel shard",
        shell="bash",
    )
    require_gradle_invocation(
        bundle_build,
        "release-bundle build",
        tasks=(":app:bundleRelease",),
        args=(
            "--build-cache",
            "--parallel",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    bundle_upload = require_step(
        android,
        "release-bundle",
        "Upload release bundle",
        uses_prefix="actions/upload-artifact@",
    )
    bundle_with = bundle_upload.get("with")
    if not isinstance(bundle_with, dict):
        fail("release bundle upload inputs are missing")
    if bundle_with.get("name") != "gamehub-ultra-release-bundle":
        fail("release bundle artifact name changed")
    if "app/build/outputs/bundle/release/**/*.aab" not in str(bundle_with.get("path", "")):
        fail("release bundle artifact lost AAB output")

    shard_job = job(android, "device-validation-shard")
    strategy = shard_job.get("strategy")
    if not isinstance(strategy, dict):
        fail("device validation strategy is missing")
    if strategy.get("fail-fast") is not False:
        fail("device validation matrix must keep fail-fast disabled")
    if strategy.get("max-parallel") != 4:
        fail("device validation matrix must keep four physical runners")
    matrix = strategy.get("matrix")
    if not isinstance(matrix, dict):
        fail("device validation matrix definition is missing")
    if set(matrix.get("shard", [])) != {
        "release-apk",
        "ui",
        "performance-a",
        "performance-b",
    }:
        fail("device validation lost release/ui/packed performance coverage")

    retry_test = require_step(
        android,
        "device-validation-shard",
        "Test Android SDK retry safety",
        shell="bash",
        allowed_if="matrix.shard == 'release-apk'",
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
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest",
        ":app:assembleNonMinifiedRelease",
        ":baseline-profile:assembleNonMinifiedRelease",
        "performance-*",
        "--max-workers=12",
        "EMULATOR_LAUNCH_EPOCH=",
        "-no-window",
        "-no-snapshot-load",
        "-no-snapshot-save",
        "-cores 4",
        "-memory 4096",
    ):
        require_run_fragment(prebuild, "parallel device prebuild", fragment)
    if ":app:bundleRelease" in str(prebuild.get("run", "")):
        fail("AAB build must stay isolated in release-bundle shard")

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
        'LOGICAL_SHARDS=(baseline macro-startup)',
        'LOGICAL_SHARDS=(macro-library macro-settings)',
        'RULE="BaselineProfile"',
        'RULE="Macrobenchmark"',
        "coldStartup",
        "navigationToLibrary",
        "navigationToSettings",
        "android.testInstrumentationRunnerArguments.class",
        "Expected at least one {label} case",
        "Required performance tests were skipped",
        'for LOGICAL_SHARD in "${LOGICAL_SHARDS[@]}"',
    ):
        require_run_fragment(validate, "device validation coverage", fragment)
    require_gradle_invocation(
        validate,
        "device validation/UI instrumentation",
        tasks=(":app:connectedDebugAndroidTest",),
        args=(
            "--build-cache",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    require_gradle_invocation(
        validate,
        "device validation/performance instrumentation",
        tasks=(":baseline-profile:connectedNonMinifiedReleaseAndroidTest",),
        args=(
            "--build-cache",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    validate_run = str(validate.get("run", ""))
    if validate_run.count(":baseline-profile:connectedNonMinifiedReleaseAndroidTest") != 1:
        fail("packed performance script must contain exactly one looped instrumentation command")
    for expected_command in (
        ("adb", "install", "$APK"),
        ("adb", "install", "-r", "$APK"),
        ("adb", "uninstall", "com.cardenaspiero255.gamehubultra"),
    ):
        require_shell_command(
            validate,
            "device validation/release install lifecycle",
            expected_command,
        )

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
        "performance-validation.log",
    ):
        require_run_fragment(noise, "CAR-29 emulator-noise gate", fragment)

    research = require_step(
        android,
        "device-validation-shard",
        "Verify release research configuration",
        shell="bash",
        allowed_if="matrix.shard == 'release-apk'",
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
        "Build distributable release APK with Sentry",
        shell="bash",
        allowed_if="success() && matrix.shard == 'release-apk' && github.event_name != 'pull_request'",
    )
    require_gradle_invocation(
        sentry_build,
        "trusted Sentry release APK build",
        tasks=(":app:assembleRelease",),
        args=("--rerun-tasks",),
    )
    if ":app:bundleRelease" in str(sentry_build.get("run", "")):
        fail("Sentry APK shard must not duplicate the parallel AAB build")
    require_step(
        android,
        "device-validation-shard",
        "Register Sentry release and GitHub commit",
        shell="bash",
        allowed_if="success() && matrix.shard == 'release-apk' && github.event_name != 'pull_request'",
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
        allowed_if="matrix.shard == 'release-apk' && env.RESEARCH_RELEASE_READY == 'true'",
    )
    release_with = release_upload.get("with")
    if not isinstance(release_with, dict):
        fail("release artifact upload inputs are missing")
    release_paths = str(release_with.get("path", ""))
    if "app/build/outputs/apk/release/**/*.apk" not in release_paths:
        fail("release APK artifact output lost")
    if ".aab" in release_paths:
        fail("release APK shard must not duplicate AAB artifact")

    benchmark_upload = require_step(
        android,
        "device-validation-shard",
        "Upload benchmark reports",
        uses_prefix="actions/upload-artifact@",
        allowed_if="always() && startsWith(matrix.shard, 'performance-')",
    )
    benchmark_with = benchmark_upload.get("with")
    if not isinstance(benchmark_with, dict):
        fail("packed benchmark artifact inputs are missing")
    if "${{ runner.temp }}/performance-reports/**" not in str(benchmark_with.get("path", "")):
        fail("packed performance reports are not preserved")

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

    smoke_job = job(smoke, "smoke")
    smoke_strategy = smoke_job.get("strategy")
    if not isinstance(smoke_strategy, dict):
        fail("smoke shard strategy is missing")
    if smoke_strategy.get("fail-fast") is not False:
        fail("smoke shards must keep fail-fast disabled")
    if smoke_strategy.get("max-parallel") != 4:
        fail("smoke physical concurrency changed")
    smoke_matrix = smoke_strategy.get("matrix")
    expected_smoke_runners = set(range(8))
    if (
        not isinstance(smoke_matrix, dict)
        or set(smoke_matrix.get("shard", [])) != expected_smoke_runners
    ):
        fail("smoke must keep 8 physical runners")
    smoke_env = smoke_job.get("env")
    if not isinstance(smoke_env, dict):
        fail("smoke shard env is missing")
    expected_smoke_env = {
        "SMOKE_STABLE_WORKERS": "32",
        "SMOKE_SHARD_COUNT": "64",
    }
    for key, expected in expected_smoke_env.items():
        if smoke_env.get(key) != expected:
            fail(f"smoke packed parallelism changed: {key}")
    smoke_run = require_step(
        smoke,
        "smoke",
        "Exercise 50000 stratified and runtime-generated questions",
        shell="bash",
    )
    for fragment in (
        "physical_runner_count = 8",
        "logical_shards_per_runner = 8",
        "shard_index + physical_runner_count * lane",
        "max_workers=stable_workers",
    ):
        require_run_fragment(smoke_run, "smoke 64-logical-shard packing", fragment)

    android_test_shard = job(android, "android-test-shard")
    android_test_strategy = android_test_shard.get("strategy")
    if not isinstance(android_test_strategy, dict):
        fail("Android test shard strategy is missing")
    if android_test_strategy.get("fail-fast") is not False:
        fail("Android test shards must keep fail-fast disabled")
    if android_test_strategy.get("max-parallel") != 4:
        fail("Android test physical concurrency changed")
    android_test_matrix = android_test_strategy.get("matrix")
    if (
        not isinstance(android_test_matrix, dict)
        or set(android_test_matrix.get("runner", [])) != set(range(8))
    ):
        fail("Android physical test runner matrix changed")
    android_test_env = android_test_shard.get("env")
    if not isinstance(android_test_env, dict):
        fail("Android test shard env is missing")
    expected_android_test_env = {
        "GAMEHUB_UNIT_TEST_FORKS": "8",
        "GAMEHUB_ENABLE_UNIT_TEST_COVERAGE": "false",
        "ANDROID_LOGICAL_SHARD_COUNT": "64",
        "ANDROID_PHYSICAL_SHARD_COUNT": "8",
    }
    for key, expected in expected_android_test_env.items():
        if android_test_env.get(key) != expected:
            fail(f"Android packed test parallelism changed: {key}")
    android_test_select = require_step(
        android,
        "android-test-shard",
        "Select deterministic Android logical test shards",
        shell="bash",
    )
    for fragment in (
        "hashlib.sha256",
        "logical_count // physical_count",
        "runner + wave * physical_count",
        "No Android unit tests selected",
    ):
        require_run_fragment(android_test_select, "Android logical shard selection", fragment)
    android_test_run = require_step(
        android,
        "android-test-shard",
        "Run packed Android unit shards",
        shell="bash",
    )
    require_gradle_invocation(
        android_test_run,
        "Android packed unit tests",
        tasks=(":app:testDebugUnitTest",),
        args=(
            "--build-cache",
            "--parallel",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    android_test_fan_in = job(android, "android-test-shards")
    if normalized_if(android_test_fan_in.get("if")) != "always()":
        fail("Android test fan-in must use if: always()")
    if android_test_fan_in.get("needs") != "android-test-shard":
        fail("Android test fan-in lost packed shard dependency")

    coverage_shard_job = job(coverage, "coverage-shard")
    coverage_strategy = coverage_shard_job.get("strategy")
    if not isinstance(coverage_strategy, dict):
        fail("coverage shard strategy is missing")
    if coverage_strategy.get("fail-fast") is not False:
        fail("coverage shards must keep fail-fast disabled")
    if coverage_strategy.get("max-parallel") != 4:
        fail("coverage physical concurrency changed")
    coverage_matrix = coverage_strategy.get("matrix")
    if (
        not isinstance(coverage_matrix, dict)
        or set(coverage_matrix.get("runner", [])) != set(range(8))
    ):
        fail("coverage physical runner matrix changed")
    coverage_env = coverage_shard_job.get("env")
    if not isinstance(coverage_env, dict):
        fail("coverage shard env is missing")
    expected_coverage_env = {
        "GAMEHUB_UNIT_TEST_FORKS": "8",
        "GAMEHUB_ENABLE_UNIT_TEST_COVERAGE": "true",
        "COVERAGE_LOGICAL_SHARD_COUNT": "64",
        "COVERAGE_PHYSICAL_SHARD_COUNT": "8",
    }
    for key, expected in expected_coverage_env.items():
        if coverage_env.get(key) != expected:
            fail(f"coverage packed parallelism changed: {key}")

    coverage_select = require_step(
        coverage,
        "coverage-shard",
        "Select deterministic logical test shards",
        shell="bash",
    )
    for fragment in (
        "hashlib.sha256",
        "logical_count // physical_count",
        "runner + wave * physical_count",
        "No unit tests selected",
    ):
        require_run_fragment(coverage_select, "coverage shard selection", fragment)

    coverage_run = require_step(
        coverage,
        "coverage-shard",
        "Run packed coverage shards",
        shell="bash",
    )
    require_gradle_invocation(
        coverage_run,
        "coverage packed unit tests",
        tasks=(":app:testDebugUnitTest",),
        args=(
            "--build-cache",
            "--parallel",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    require_run_fragment(
        coverage_run,
        "coverage packed unit tests",
        'TEST_ARGS+=(--tests "$test_class")',
    )

    coverage_collect = require_step(
        coverage,
        "coverage-shard",
        "Collect JaCoCo execution data",
        shell="bash",
    )
    for fragment in ("*.exec", "*.ec", "No JaCoCo execution data produced"):
        require_run_fragment(coverage_collect, "coverage execution-data collection", fragment)

    coverage_classes_upload = require_step(
        coverage,
        "coverage-shard",
        "Upload compiled coverage classes",
        uses_prefix="actions/upload-artifact@",
        allowed_if="matrix.runner == 0",
    )
    classes_upload_with = coverage_classes_upload.get("with")
    if not isinstance(classes_upload_with, dict):
        fail("coverage class artifact inputs are missing")
    if classes_upload_with.get("name") != "gamehub-ultra-coverage-classes":
        fail("coverage class artifact name changed")
    coverage_classes_download = require_step(
        coverage,
        "coverage",
        "Download compiled coverage classes",
        uses_prefix="actions/download-artifact@",
    )
    classes_download_with = coverage_classes_download.get("with")
    if not isinstance(classes_download_with, dict):
        fail("coverage compiled-class download inputs are missing")
    if classes_download_with.get("name") != "gamehub-ultra-coverage-classes":
        fail("coverage compiled-class download selected the wrong artifact")
    coverage_classes_verify = require_step(
        coverage,
        "coverage",
        "Verify compiled coverage classes",
        shell="bash",
    )
    for fragment in ("tmp/kotlin-classes/debug", "No compiled coverage classes"):
        require_run_fragment(
            coverage_classes_verify,
            "coverage compiled-class reuse",
            fragment,
        )

    coverage_exec_upload = require_step(
        coverage,
        "coverage-shard",
        "Upload coverage execution shard",
        uses_prefix="actions/upload-artifact@",
    )
    exec_with = coverage_exec_upload.get("with")
    if not isinstance(exec_with, dict):
        fail("coverage execution shard upload inputs are missing")
    if exec_with.get("name") != "gamehub-ultra-coverage-exec-${{ matrix.runner }}":
        fail("coverage execution shard artifact name changed")

    coverage_download = require_step(
        coverage,
        "coverage",
        "Download packed JaCoCo execution shards",
        uses_prefix="actions/download-artifact@",
    )
    download_with = coverage_download.get("with")
    if not isinstance(download_with, dict):
        fail("coverage execution download inputs are missing")
    if download_with.get("pattern") != "gamehub-ultra-coverage-exec-*":
        fail("coverage aggregation lost shard artifact pattern")
    if download_with.get("merge-multiple") is not True:
        fail("coverage aggregation must merge execution artifacts")

    coverage_generate = require_step(
        coverage,
        "coverage",
        "Aggregate sharded debug unit-test coverage",
        shell="bash",
    )
    require_gradle_invocation(
        coverage_generate,
        "coverage/Aggregate sharded debug unit-test coverage",
        tasks=(":app:createShardedDebugUnitTestCoverageReport",),
        args=(
            "--build-cache",
            "--parallel",
            "--max-workers=12",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    aggregate_env = coverage_generate.get("env")
    if not isinstance(aggregate_env, dict):
        fail("coverage aggregation env is missing")
    if aggregate_env.get("GAMEHUB_COVERAGE_EXECUTION_DATA_DIR") != "${{ runner.temp }}/coverage-shards":
        fail("coverage aggregation execution-data directory changed")
    if aggregate_env.get("GAMEHUB_COVERAGE_CLASS_ROOT") != "${{ runner.temp }}/coverage-classes":
        fail("coverage aggregation compiled-class root changed")

    coverage_verify = require_step(
        coverage,
        "coverage",
        "Verify coverage XML report",
        shell="bash",
    )
    for fragment in ("report.xml", "test -s"):
        require_run_fragment(coverage_verify, "coverage XML verification", fragment)

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

    coverage_report_upload = require_step(
        coverage,
        "coverage",
        "Preserve coverage report",
        uses_prefix="actions/upload-artifact@",
        allowed_if="always()",
    )
    report_with = coverage_report_upload.get("with")
    if not isinstance(report_with, dict) or report_with.get("name") != "gamehub-ultra-coverage-report":
        fail("coverage report artifact name changed")

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
    download = step(publish_job, "publish", "Download exact coverage artifact")
    if not str(download.get("uses", "")).startswith("actions/download-artifact@"):
        fail("coverage report download action is missing")
    download_with = download.get("with")
    if not isinstance(download_with, dict):
        fail("coverage report download inputs are missing")
    if download_with.get("run-id") != "${{ github.event.workflow_run.id }}":
        fail("coverage report download lost triggering-run binding")

    codecov = step(publish_job, "publish", "Upload coverage to Codecov")
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
        "release-bundle",
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
    for dependency in ("quality", "device-validation", "release-bundle"):
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
        "CI safety contract verified: packed logical shards preserve all blocking "
        "lint, device, release, benchmark, coverage and provenance gates."
    )


if __name__ == "__main__":
    main()
