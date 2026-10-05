#!/usr/bin/env python3
from __future__ import annotations

import json
import shlex
import subprocess
from pathlib import Path
from typing import Any

ANDROID = Path(".github/workflows/android.yml")
COVERAGE = Path(".github/workflows/coverage.yml")
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
    """Validate that CI optimization preserves every required blocking gate."""
    android = load_workflow(ANDROID)
    coverage = load_workflow(COVERAGE)
    shadow_metrics = load_workflow(SHADOW_METRICS)

    quality = require_step(
        android,
        "quality",
        "Run fast quality gates",
        shell="bash",
    )
    require_gradle_invocation(
        quality,
        "quality/Run fast quality gates",
        tasks=(":app:assembleDebug", ":app:lintDebug"),
        args=("--build-cache", "--parallel", "--configuration-cache", "--configuration-cache-problems=fail", "--console=plain"),
    )
    quality_run = str(quality.get("run", ""))
    quality_retry_fragments = (
        "QUALITY_RETRY_BASE_SECONDS=5",
        'QUALITY_TERMINAL_LOG="$RUNNER_TEMP/quality-gates-terminal.log"',
        "QUALITY_TRANSIENT_RE='Received status code (408|425|429|500|502|503|504)|Read timed out|Connect timed out|Connection reset|Temporary failure in name resolution|Remote host terminated the handshake'",
        "/^\\* What went wrong:$/",
        "/^\\* Try:$/",
        'if ! grep -Eq "$QUALITY_TRANSIENT_RE" "$QUALITY_TERMINAL_LOG"; then',
        'if (( quality_attempt >= QUALITY_MAX_ATTEMPTS )); then',
        'exit "$quality_status"',
    )
    for fragment in quality_retry_fragments:
        require_run_fragment(quality, "quality/bounded transient dependency retry", fragment)

    active_retry_bounds: list[str] = []
    for line in quality_run.splitlines():
        stripped = line.strip()
        if stripped.startswith("QUALITY_MAX_ATTEMPTS="):
            value = stripped.split("=", 1)[1].split("#", 1)[0].strip().strip("'\"")
            active_retry_bounds.append(value)
    if active_retry_bounds != ["3"]:
        fail(
            "Quality retry bound must have exactly one active shell assignment "
            f"QUALITY_MAX_ATTEMPTS=3; found {active_retry_bounds!r}"
        )
    quality_matches = [
        tokens
        for tokens in gradle_commands(str(quality.get("run", "")))
        if all(task in set(tokens) for task in (":app:assembleDebug", ":app:lintDebug"))
        and all(arg in set(tokens) for arg in ("--build-cache", "--parallel", "--configuration-cache", "--configuration-cache-problems=fail", "--console=plain"))
    ]
    executable_quality_matches = [tokens for tokens in quality_matches if "--dry-run" not in set(tokens)]
    probe_quality_matches = [tokens for tokens in quality_matches if "--dry-run" in set(tokens)]
    if len(executable_quality_matches) != 1 or len(probe_quality_matches) != 2:
        fail(
            "Configuration Cache proof requires one executable quality graph and "
            "two non-executing --dry-run reuse probes; found "
            f"{len(executable_quality_matches)} executable and "
            f"{len(probe_quality_matches)} probes"
        )
    if normalized_gradle_invocation(probe_quality_matches[0]) != normalized_gradle_invocation(probe_quality_matches[1]):
        fail("Quality --dry-run Configuration Cache probes differ")
    require_shell_command(
        quality,
        "quality/Configuration Cache reuse",
        ("grep", "-Fq", "Reusing configuration cache.", "$CONFIG_CACHE_LOG"),
    )

    release = require_step(
        android,
        "device-validation",
        "Build telemetry-disabled release APK and AAB for validation",
        shell="bash",
    )
    require_gradle_invocation(
        release,
        "device-validation/Build telemetry-disabled release APK and AAB for validation",
        tasks=(
            ":app:assembleRelease",
            ":app:bundleRelease",
            ":app:assembleNonMinifiedRelease",
            ":baseline-profile:assembleNonMinifiedRelease",
        ),
        args=("--build-cache", "--parallel"),
    )

    release_tasks = (
        ":app:assembleRelease",
        ":app:bundleRelease",
        ":app:assembleNonMinifiedRelease",
        ":baseline-profile:assembleNonMinifiedRelease",
    )
    release_args = (
        "--build-cache",
        "--parallel",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
    )
    # Count complete release graphs independently of their flags so an extra
    # expensive invocation cannot hide by dropping Configuration Cache options.
    release_graphs = [
        tokens
        for tokens in gradle_commands(str(release.get("run", "")))
        if all(task in set(tokens) for task in release_tasks)
    ]
    executable_release_graphs = [
        tokens for tokens in release_graphs if "--dry-run" not in set(tokens)
    ]
    probe_release_graphs = [
        tokens for tokens in release_graphs if "--dry-run" in set(tokens)
    ]
    if len(executable_release_graphs) != 1 or len(probe_release_graphs) != 2:
        fail(
            "Configuration Cache proof requires exactly one executable "
            "release/performance graph and two identical non-executing --dry-run "
            "probes; found "
            f"{len(executable_release_graphs)} executable and "
            f"{len(probe_release_graphs)} probes"
        )
    if normalized_gradle_invocation(probe_release_graphs[0]) != normalized_gradle_invocation(probe_release_graphs[1]):
        fail("Release/performance --dry-run Configuration Cache probes differ")
    for label, tokens in (
        ("executable release/performance graph", executable_release_graphs[0]),
        ("first release/performance --dry-run probe", probe_release_graphs[0]),
        ("second release/performance --dry-run probe", probe_release_graphs[1]),
    ):
        token_set = set(tokens)
        missing_args = [arg for arg in release_args if arg not in token_set]
        if missing_args:
            fail(f"{label} is missing required arguments: {missing_args!r}")
    release_task_set = set(release_tasks)
    for tokens in gradle_commands(str(release.get("run", ""))):
        token_set = set(tokens)
        present_release_tasks = release_task_set.intersection(token_set)
        if present_release_tasks and present_release_tasks != release_task_set:
            fail(
                "Release/performance Gradle invocations must not execute a partial "
                "required task graph; found "
                f"{sorted(present_release_tasks)!r}"
            )
    require_shell_command(
        release,
        "device-validation/Configuration Cache reuse",
        ("grep", "-Fq", "Reusing configuration cache.", "$RELEASE_CONFIG_CACHE_LOG"),
    )

    connected = require_step(
        android,
        "device-validation",
        "Verify MainActivity presentation wiring on API 35",
        shell="bash",
    )
    require_gradle_invocation(
        connected,
        "device-validation/Verify MainActivity presentation wiring on API 35",
        tasks=(":app:connectedDebugAndroidTest",),
        args=(
            "--build-cache",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
        ),
    )
    connected_commands = [
        tokens
        for tokens in gradle_commands(str(connected.get("run", "")))
        if ":app:connectedDebugAndroidTest" in set(tokens)
    ]
    executable_connected = [
        tokens for tokens in connected_commands if "--dry-run" not in set(tokens)
    ]
    if len(executable_connected) != 1:
        fail(
            "Connected API 35 validation requires exactly one executable Gradle "
            f"invocation; found {len(executable_connected)}"
        )

    connected_required_args = {
        "--build-cache",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
    }
    executable_connected_args = set(executable_connected[0])
    missing_connected_args = sorted(
        connected_required_args.difference(executable_connected_args)
    )
    if missing_connected_args:
        fail(
            "Executable connected API 35 validation is missing required arguments: "
            f"{missing_connected_args!r}"
        )

    # Phase 2 block 2 must remain a measurable A/B experiment: the candidate
    # starts the emulator immediately and emits timestamps used to compare
    # end-to-end device-validation latency against main.
    for fragment in (
        "EXCLUSIVE_BUILD_SECONDS=0",
        "EMULATOR_LAUNCH_EPOCH=",
        "BUILD_COMPLETE_EPOCH=",
        "emulator-startup-ab.md",
    ):
        require_run_fragment(release, "Phase 2 emulator startup A/B telemetry", fragment)

    ab_upload = require_step(
        android,
        "device-validation",
        "Upload emulator startup A/B metrics",
        uses_prefix="actions/upload-artifact@",
        allowed_if="always()",
    )
    if "${{ runner.temp }}/emulator-startup-ab.md" not in str(ab_upload.get("with", {}).get("path", "")):
        fail("Phase 2 emulator startup A/B metrics artifact lost its metrics file")

    readiness = require_step(
        android,
        "device-validation",
        "Wait for API 35 emulator readiness",
        shell="bash",
    )
    for fragment in ("sys.boot_completed", "ro.build.version.sdk", 'grep -Fxq "35"'):
        require_run_fragment(readiness, "API 35 readiness", fragment)

    install = require_step(
        android,
        "device-validation",
        "Validate release APK install-update-uninstall on API 35",
        shell="bash",
    )
    for fragment in ("adb install", "adb install -r", "adb uninstall"):
        require_run_fragment(install, "API 35 install/update/uninstall", fragment)

    performance = require_step(
        android,
        "device-validation",
        "Run baseline profile and macrobenchmarks on API 35",
        shell="bash",
    )
    require_gradle_invocation(
        performance,
        "device-validation/Run baseline profile and macrobenchmarks on API 35",
        tasks=(":baseline-profile:connectedNonMinifiedReleaseAndroidTest",),
    )
    for fragment in (
        "enabledRules=BaselineProfile,Macrobenchmark",
        "BaselineProfileGenerator",
        "GameHubMacrobenchmark",
        "Expected at least 3 GameHubMacrobenchmark cases",
        "Required performance tests were skipped",
    ):
        require_run_fragment(performance, "performance report verification", fragment)

    noise = require_step(
        android,
        "device-validation",
        "Reject known CAR-29 emulator noise",
        shell="bash",
        allowed_if="always()",
    )
    for fragment in ("Failed to start Emulator console", "stop: Not implemented"):
        require_run_fragment(noise, "CAR-29 emulator-noise gate", fragment)

    sentry_build = require_step(
        android,
        "device-validation",
        "Build distributable release APK and AAB with Sentry",
        shell="bash",
        allowed_if="success() && github.event_name != 'pull_request'",
    )
    require_gradle_invocation(
        sentry_build,
        "trusted Sentry release build",
        tasks=(":app:assembleRelease", ":app:bundleRelease"),
        args=("--rerun-tasks",),
    )

    sentry_register = require_step(
        android,
        "device-validation",
        "Register Sentry release and GitHub commit",
        shell="bash",
        allowed_if="success() && github.event_name != 'pull_request'",
        best_effort=True,
    )
    require_run_fragment(
        sentry_register,
        "trusted Sentry release registration",
        "register_sentry_release.py",
    )

    research_config = require_step(
        android,
        "device-validation",
        "Verify release research configuration",
        shell="bash",
    )
    research_env = research_config.get("env")
    if not isinstance(research_env, dict):
        fail("Release research configuration env is missing")
    trusted_release_context = str(
        research_env.get("TRUSTED_RELEASE_CONTEXT", "")
    )
    expected_trusted_release_context = (
        "${{ github.event_name != 'pull_request' || "
        "(github.actor != 'dependabot[bot]' && "
        "github.event.pull_request.head.repo.full_name == github.repository) }}"
    )
    if trusted_release_context != expected_trusted_release_context:
        fail(
            "TRUSTED_RELEASE_CONTEXT must keep Dependabot pull requests "
            "on the untrusted validation path"
        )

    for fragment in (
        'RESEARCH_RELEASE_READY=false',
        'research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"',
        'if [ "${TRUSTED_RELEASE_CONTEXT}" = "true" ]; then',
        'exit 1',
        'RESEARCH_RELEASE_READY=true',
    ):
        require_run_fragment(
            research_config,
            "whitespace-safe release research configuration",
            fragment,
        )

    release_upload = require_step(
        android,
        "device-validation",
        "Upload installable release outputs",
        uses_prefix="actions/upload-artifact@",
        allowed_if="env.RESEARCH_RELEASE_READY == 'true'",
    )
    release_upload_with = release_upload.get("with")
    if not isinstance(release_upload_with, dict):
        fail("Release artifact upload configuration is missing")
    release_upload_paths = str(release_upload_with.get("path", ""))
    for required_path in (
        "app/build/outputs/apk/release/**/*.apk",
        "app/build/outputs/bundle/release/**/*.aab",
    ):
        if required_path not in release_upload_paths:
            fail(f"Release artifact upload lost required output: {required_path}")

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
        require_run_fragment(coverage_verify, "coverage XML verification", fragment)

    local_patch_coverage = require_step(
        coverage,
        "coverage",
        "Enforce local patch coverage",
        shell="bash",
    )
    for fragment in (
        ".github/scripts/local_patch_coverage.py",
        "report.xml",
        "--base",
        "--head",
        "--min-patch-line 90",
    ):
        require_run_fragment(
            local_patch_coverage,
            "blocking local patch coverage gate",
            fragment,
        )

    codecov_probe = require_step(
        coverage,
        "coverage",
        "Probe Codecov ingest availability",
        shell="bash",
        allowed_if="steps.codecov_token.outputs.available == 'true'",
    )
    for fragment in (
        "https://ingest.codecov.io/",
        "reachable=false",
        "reachable=true",
    ):
        require_run_fragment(codecov_probe, "Codecov availability probe", fragment)

    codecov = require_step(
        coverage,
        "coverage",
        "Upload coverage to Codecov",
        uses_prefix="codecov/codecov-action@",
        allowed_if=(
            "steps.codecov_token.outputs.available == 'true' && "
            "steps.codecov_probe.outputs.reachable == 'true'"
        ),
        best_effort=True,
    )
    with_values = codecov.get("with")
    if not isinstance(with_values, dict) or with_values.get("fail_ci_if_error") is not True:
        fail("Codecov upload no longer reports upload failures")

    codecov_retry = require_step(
        coverage,
        "coverage",
        "Retry Codecov through legacy endpoint",
        uses_prefix="codecov/codecov-action@",
        allowed_if=(
            "steps.codecov_token.outputs.available == 'true' && "
            "steps.codecov_probe.outputs.reachable == 'true' && "
            "steps.codecov_upload.outcome != 'success'"
        ),
        best_effort=True,
    )
    retry_with = codecov_retry.get("with")
    if not isinstance(retry_with, dict) or retry_with.get("fail_ci_if_error") is not True:
        fail("Codecov retry no longer reports upload failures")
    if retry_with.get("use_legacy_upload_endpoint") is not True:
        fail("Codecov retry must preserve the independent legacy endpoint")

    codecov_status = require_step(
        coverage,
        "coverage",
        "Report Codecov upload status",
        shell="bash",
        allowed_if="always() && steps.codecov_token.outputs.available == 'true'",
    )
    for fragment in (
        "$PROBE_REACHABLE",
        "$UPLOAD_OUTCOME",
        "$RETRY_OUTCOME",
        "local patch coverage",
    ):
        require_run_fragment(codecov_status, "Codecov status reporting", fragment)

    android_jobs = android.get("jobs")
    if not isinstance(android_jobs, dict):
        fail("Android workflow jobs mapping is missing")
    if "metrics" in android_jobs:
        fail("Phase 3 block 4 requires Android metrics to run only post-run in shadow mode")

    build_job = job(android, "build")
    build_needs = build_job.get("needs")
    if not isinstance(build_needs, list) or set(build_needs) != {"quality", "device-validation"}:
        fail("Aggregate build gate must depend directly on quality and device-validation")
    if normalized_if(build_job.get("if")) != "always()":
        fail("Aggregate build gate must use if: always() so failed dependencies remain observable")
    if build_job.get("continue-on-error") not in (None, False):
        fail("Aggregate build gate must remain blocking")

    aggregate_gate = require_step(
        android,
        "build",
        "Verify all Android CI gates",
        shell="bash",
    )
    require_shell_command(
        aggregate_gate,
        "build/Verify all Android CI gates quality result",
        ("test", "${{ needs.quality.result }}", "=", "success"),
    )
    require_shell_command(
        aggregate_gate,
        "build/Verify all Android CI gates device-validation result",
        ("test", "${{ needs.device-validation.result }}", "=", "success"),
    )
    require_step(
        android,
        "build",
        "Verify Phase 3 artifact provenance",
        shell="bash",
    )

    shadow_collect = job(shadow_metrics, "collect")
    if shadow_collect.get("continue-on-error") not in (None, False):
        fail("Shadow metrics collector unexpectedly became advisory")
    shadow_step = require_step(
        shadow_metrics,
        "collect",
        "Collect completed parent metrics in shadow mode",
        shell="bash",
    )
    shadow_run = str(shadow_step.get("run", ""))
    for fragment in (
        'REQUIRED_JOB="build"',
        '--require-completed',
        '--require-job "$REQUIRED_JOB"',
        '--expected-run-id "$PARENT_RUN_ID"',
        '--expected-head-sha "$PARENT_HEAD_SHA"',
        '--expected-workflow "$PARENT_WORKFLOW"',
    ):
        if fragment not in shadow_run:
            fail(f"Shadow metrics lost required provenance fragment: {fragment}")

    require_concurrency(android, "Android workflow")
    require_concurrency(coverage, "coverage workflow")

    # Exactly one real performance Gradle invocation must remain.
    perf_invocations = 0
    for job_name, job_value in android.get("jobs", {}).items():
        if not isinstance(job_value, dict):
            continue
        for candidate in job_value.get("steps", []):
            if not isinstance(candidate, dict):
                continue
            run = candidate.get("run")
            if not isinstance(run, str):
                continue
            for tokens in gradle_commands(run):
                if ":baseline-profile:connectedNonMinifiedReleaseAndroidTest" in tokens:
                    perf_invocations += 1

    if perf_invocations != 1:
        fail(
            "Android performance validation must use exactly one executable "
            f"instrumentation invocation; found {perf_invocations}"
        )

    print(
        "CI safety contract verified with real YAML parsing: "
        "required gates execute and preserve failure semantics."
    )


if __name__ == "__main__":
    main()
