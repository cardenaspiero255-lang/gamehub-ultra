#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ANDROID = Path(".github/workflows/android.yml")
COVERAGE = Path(".github/workflows/coverage.yml")


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def leading_spaces(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def active_lines(path: Path) -> list[str]:
    if not path.is_file():
        fail(f"Missing workflow: {path}")
    return path.read_text(encoding="utf-8").splitlines()


def parse_steps(path: Path) -> dict[str, dict[str, object]]:
    lines = active_lines(path)
    steps: dict[str, dict[str, object]] = {}
    i = 0
    while i < len(lines):
        raw = lines[i]
        stripped = raw.lstrip()
        if stripped.startswith("#"):
            i += 1
            continue
        match = re.match(r"^(\s*)- name:\s*(.+?)\s*$", raw)
        if not match:
            i += 1
            continue
        indent = len(match.group(1))
        name = match.group(2).strip().strip('"').strip("'")
        block: list[str] = [raw]
        i += 1
        while i < len(lines):
            nxt = lines[i]
            nxt_stripped = nxt.lstrip()
            if nxt_stripped and not nxt_stripped.startswith("#"):
                nxt_indent = leading_spaces(nxt)
                if nxt_indent <= indent and re.match(r"^\s*-\s+name:", nxt):
                    break
                if nxt_indent < indent:
                    break
            block.append(nxt)
            i += 1

        fields: dict[str, str] = {}
        run_lines: list[str] = []
        run_indent: int | None = None
        for idx, line in enumerate(block[1:], start=1):
            stripped_line = line.lstrip()
            if not stripped_line or stripped_line.startswith("#"):
                continue
            line_indent = leading_spaces(line)
            if run_indent is not None:
                if line_indent > run_indent:
                    run_lines.append(stripped_line)
                    continue
                run_indent = None
            field_match = re.match(r"^\s{2,}([A-Za-z0-9_-]+):\s*(.*?)\s*$", line)
            if not field_match:
                continue
            key, value = field_match.group(1), field_match.group(2)
            fields[key] = value.strip().strip('"').strip("'")
            if key == "run":
                run_indent = line_indent
                if value not in ("|", ">", ""):
                    run_lines.append(value.strip())

        steps[name] = {"fields": fields, "run": "\n".join(run_lines), "block": "\n".join(block)}
    return steps


def require_step(
    steps: dict[str, dict[str, object]],
    name: str,
    *,
    commands: tuple[str, ...] = (),
    uses_prefix: str | None = None,
    allowed_if: str | None = None,
    shell: str | None = None,
) -> dict[str, object]:
    step = steps.get(name)
    if step is None:
        fail(f"Missing required active step: {name}")
    fields = step["fields"]
    assert isinstance(fields, dict)
    run = step["run"]
    assert isinstance(run, str)

    if fields.get("continue-on-error", "").lower() == "true":
        fail(f"Required gate is advisory via continue-on-error: {name}")

    actual_if = fields.get("if")
    if allowed_if is None:
        if actual_if not in (None, ""):
            fail(f"Required gate has unexpected if condition: {name}: {actual_if}")
    elif actual_if != allowed_if:
        fail(f"Required gate has unexpected if condition: {name}: {actual_if!r}; expected {allowed_if!r}")

    if shell is not None and fields.get("shell") != shell:
        fail(f"Required gate changed shell: {name}: {fields.get('shell')!r}; expected {shell!r}")

    for command in commands:
        uncommented = "\n".join(
            line for line in run.splitlines() if not line.lstrip().startswith("#")
        )
        if command not in uncommented:
            fail(f"Required gate lost executable command {command!r}: {name}")

    if uses_prefix is not None:
        uses = str(fields.get("uses", ""))
        if not uses.startswith(uses_prefix):
            fail(f"Required action changed for {name}: {uses!r}")

    return step


def require_uncommented_line(path: Path, exact_fragment: str, label: str) -> None:
    for line in active_lines(path):
        stripped = line.lstrip()
        if stripped.startswith("#"):
            continue
        if exact_fragment in stripped:
            return
    fail(f"Workflow contract lost: {label}")


def main() -> None:
    android_steps = parse_steps(ANDROID)
    coverage_steps = parse_steps(COVERAGE)

    require_step(
        android_steps,
        "Run fast quality gates",
        commands=(":app:testDebugUnitTest", ":app:assembleDebug", ":app:lintDebug", "--build-cache", "--parallel"),
        shell="bash",
    )
    require_step(
        android_steps,
        "Build telemetry-disabled release APK and AAB for validation",
        commands=(":app:assembleRelease", ":app:bundleRelease", "--build-cache"),
        shell="bash",
    )
    require_step(
        android_steps,
        "Validate release APK install-update-uninstall on API 35",
        commands=("adb install", "adb install -r", "adb uninstall", "ro.build.version.sdk"),
        shell="bash",
    )
    require_step(
        android_steps,
        "Run baseline profile and macrobenchmarks on API 35",
        commands=(
            "connectedNonMinifiedReleaseAndroidTest",
            "enabledRules=BaselineProfile,Macrobenchmark",
            "BaselineProfileGenerator",
            "GameHubMacrobenchmark",
            "Expected at least 3 GameHubMacrobenchmark cases",
        ),
        shell="bash",
    )
    require_step(
        android_steps,
        "Reject known CAR-29 emulator noise",
        commands=("Failed to start Emulator console", "stop: Not implemented"),
        allowed_if="always()",
        shell="bash",
    )
    require_step(
        android_steps,
        "Build distributable release APK and AAB with Sentry",
        commands=(":app:assembleRelease", ":app:bundleRelease", "--rerun-tasks"),
        allowed_if="success() && github.event_name != 'pull_request'",
        shell="bash",
    )
    require_step(
        android_steps,
        "Register Sentry release and GitHub commit",
        commands=("register_sentry_release.py",),
        allowed_if="success() && github.event_name != 'pull_request'",
        shell="bash",
    )
    require_step(
        android_steps,
        "Upload installable release outputs",
        uses_prefix="actions/upload-artifact@",
    )

    require_step(
        coverage_steps,
        "Generate debug unit-test coverage",
        commands=(":app:createDebugUnitTestCoverageReport", "--build-cache", "--parallel"),
        shell="bash",
    )
    require_step(
        coverage_steps,
        "Verify coverage XML report",
        commands=("report.xml", "test -s"),
        shell="bash",
    )
    codecov = require_step(
        coverage_steps,
        "Upload coverage to Codecov",
        uses_prefix="codecov/codecov-action@",
        allowed_if="steps.codecov_token.outputs.available == 'true'",
    )
    codecov_block = str(codecov["block"])
    if "fail_ci_if_error: true" not in codecov_block:
        fail("Codecov upload no longer fails CI on upload errors")

    require_uncommented_line(ANDROID, "cancel-in-progress: true", "Android stale-run cancellation")
    require_uncommented_line(COVERAGE, "cancel-in-progress: true", "coverage stale-run cancellation")
    require_uncommented_line(ANDROID, "android-35", "API 35 emulator target")

    instrumentation_occurrences = 0
    for step in android_steps.values():
        run = str(step["run"])
        instrumentation_occurrences += sum(
            1
            for line in run.splitlines()
            if "connectedNonMinifiedReleaseAndroidTest" in line
            and not line.lstrip().startswith("#")
        )
    if instrumentation_occurrences != 1:
        fail(
            "Android performance validation must use exactly one executable "
            f"instrumentation invocation; found {instrumentation_occurrences}"
        )

    print("CI safety contract verified structurally: required gates execute and remain blocking.")


if __name__ == "__main__":
    main()
