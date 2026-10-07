#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/android.yml"


def load_yaml(path: Path) -> dict[str, Any]:
    ruby = r'''
require "yaml"
require "json"
parsed = YAML.safe_load(File.read(ARGV.fetch(0)), permitted_classes: [], permitted_symbols: [], aliases: true)
abort("workflow root is not a mapping") unless parsed.is_a?(Hash)
STDOUT.write(JSON.generate(parsed))
'''
    result = subprocess.run(
        ["ruby", "-e", ruby, str(path)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(f"Unable to parse workflow: {result.stderr}")
    return json.loads(result.stdout)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def named_step(job: dict[str, Any], name: str) -> dict[str, Any]:
    matches = [
        step for step in job.get("steps", [])
        if isinstance(step, dict) and step.get("name") == name
    ]
    require(len(matches) == 1, f"Expected exactly one step {name!r}; found {len(matches)}")
    return matches[0]


def main() -> None:
    workflow = load_yaml(WORKFLOW)
    jobs = workflow.get("jobs", {})
    require(isinstance(jobs, dict), "jobs mapping is missing")

    for job_name in (
        "quality",
        "configuration-cache-canary",
        "artifact-build",
        "device-validation",
        "package-release",
        "build",
    ):
        require(job_name in jobs, f"Phase 2 job is missing: {job_name}")

    canary = jobs["configuration-cache-canary"]
    canary_step = named_step(canary, "Verify configuration cache canary")
    canary_run = str(canary_step.get("run", ""))
    require("--configuration-cache" in canary_run, "Configuration cache canary is not enabled")
    require(
        "--configuration-cache-problems=fail" in canary_run,
        "Configuration cache canary must fail closed on cache incompatibilities",
    )
    require(
        "Reusing configuration cache" in canary_run
        or "Configuration cache entry reused" in canary_run,
        "Configuration cache canary does not verify reuse",
    )

    artifact = jobs["artifact-build"]
    reusable = named_step(artifact, "Build reusable device artifacts")
    reusable_run = str(reusable.get("run", ""))
    for task in (
        ":app:assembleRelease",
        ":app:assembleNonMinifiedRelease",
        ":baseline-profile:assembleNonMinifiedRelease",
    ):
        require(task in reusable_run, f"Reusable artifact build lost {task}")
    require(
        ":app:bundleRelease" not in reusable_run,
        "AAB must stay out of the reusable device-artifact critical path",
    )
    upload = named_step(artifact, "Upload reusable device artifacts")
    require(
        upload.get("with", {}).get("name") == "gamehub-ultra-device-inputs",
        "Reusable device artifacts must use the stable artifact name",
    )

    device = jobs["device-validation"]
    strategy = device.get("strategy", {})
    require(strategy.get("fail-fast") is False, "Device pool must not cancel the sibling shard")
    include = strategy.get("matrix", {}).get("include", [])
    require(isinstance(include, list) and len(include) == 2, "API 35 pool must contain exactly two shards")
    shards = {entry.get("shard"): entry for entry in include if isinstance(entry, dict)}
    require(set(shards) == {"startup", "navigation"}, f"Unexpected performance shards: {sorted(shards)}")
    require(shards["startup"].get("execution") == "connected", "Startup shard must keep Gradle connected A/B path")
    require(shards["navigation"].get("execution") == "adb", "Navigation shard must exercise direct ADB path")
    require(all(entry.get("api") == 35 for entry in include), "Every device-pool shard must run API 35")

    named_step(device, "Download reusable device artifacts")
    connected = named_step(device, "Run startup shard through Gradle connected tests")
    direct = named_step(device, "Run navigation shard through direct ADB instrumentation")
    require(
        ":baseline-profile:connectedNonMinifiedReleaseAndroidTest" in str(connected.get("run", "")),
        "Connected A/B path no longer executes Android instrumentation",
    )
    direct_run = str(direct.get("run", ""))
    require("adb shell am instrument" in direct_run, "Direct ADB A/B path is missing")
    require("navigationToLibrary" in direct_run and "navigationToSettings" in direct_run,
            "Direct ADB shard lost required navigation benchmarks")

    lifecycle = named_step(device, "Validate release APK install-update-uninstall on API 35")
    require(
        "matrix.shard == 'startup'" in str(lifecycle.get("if", "")),
        "Install/update/uninstall must execute exactly once in the two-device pool",
    )

    package = jobs["package-release"]
    package_step = named_step(package, "Build distributable release APK and AAB")
    package_run = str(package_step.get("run", ""))
    require(":app:bundleRelease" in package_run, "Packaging job must still create the release AAB")
    require(":app:assembleRelease" in package_run, "Packaging job must still create the release APK")

    final_needs = jobs["build"].get("needs", [])
    if isinstance(final_needs, str):
        final_needs = [final_needs]
    require(
        set(final_needs) >= {
            "quality",
            "configuration-cache-canary",
            "artifact-build",
            "device-validation",
            "package-release",
        },
        f"Final gate is not waiting for every Phase 2 blocking job: {final_needs}",
    )

    print(
        "Phase 2 workflow contract verified: configuration-cache canary, reusable "
        "device artifacts, parallel packaging, two API 35 shards, and connected/ADB A/B paths."
    )


if __name__ == "__main__":
    main()
