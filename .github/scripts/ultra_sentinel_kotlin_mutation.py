#!/usr/bin/env python3
"""Ultra Sentinel Kotlin Mutation Lab — deterministic opt-in TDD on an ephemeral CI checkout.

Only a small, explicitly allowlisted CAR-48 thermal policy. This is *not* an
AST-powered generic mutator and never changes user worktrees/main. No secrets.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass

TARGET = "app/src/main/java/com/cardenaspiero255/gamehubultra/domain/ThermalPredictionEngine.kt"
TEST_CLASS = "com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngineTest"
TASK = ":app:testDebugUnitTest"
MAX_MUTANTS = 5
TEST_TIMEOUT = 540
ROOT_SHA = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True)
class Mutation:
    identifier: str
    original: str
    replacement: str
    behavior: str


MUTATIONS = (
    Mutation("risk-boundary", "risk >= preventiveRiskThreshold &&",
             "risk > preventiveRiskThreshold &&", "Equal risk threshold must remain actionable"),
    Mutation("confidence-boundary", "confidence >= minimumPreventiveConfidence",
             "confidence > minimumPreventiveConfidence", "Exact confidence threshold is inclusive"),
    Mutation("trend-membership", "trend in preventiveTrends &&",
             "trend !in preventiveTrends &&", "Only configured upward trends may trigger prevention"),
    Mutation("sample-floor", "require(minimumSamples >= 3)",
             "require(minimumSamples >= 2)", "Reject policies with only two minimum samples"),
    Mutation("critical-above-high", "require(criticalRiskHeadroom > highRiskHeadroom)",
             "require(criticalRiskHeadroom >= highRiskHeadroom)", "Critical must exceed high-risk limit"),
)


def mutated_text(source: str, mutation: Mutation) -> str:
    """Mutate exactly one token span; refuse unexpected or duplicated source."""
    if source.count(mutation.original) != 1:
        raise ValueError(f"Ambiguous/missing Kotlin mutation anchor: {mutation.identifier}")
    if mutation.original == mutation.replacement:
        raise ValueError("No-op mutation")
    return source.replace(mutation.original, mutation.replacement, 1)


def plan(root: Path) -> dict:
    path = root / TARGET
    if not path.is_file() or path.is_symlink():
        raise ValueError("Allowlisted Kotlin source missing or symlinked")
    source = path.read_text(encoding="utf-8")
    rows = []
    for case in MUTATIONS:
        mutated_text(source, case)
        line = source[:source.index(case.original)].count("\n") + 1
        rows.append({"id": case.identifier, "line": line, "behavior": case.behavior})
    return {
        "schema": "ultra-sentinel-kotlin-mutation/v1",
        "target": TARGET,
        "testClass": TEST_CLASS,
        "mutationCount": len(rows),
        "mutations": rows,
        "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "safety": "Opt-in ephemeral runner; no commits, pushes, secrets, or mutation of main",
    }


def git(root: Path, *args: str) -> str:
    p = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, timeout=8, check=True)
    return p.stdout.strip()


def mutation_command(root: Path) -> list[str]:
    # This repository intentionally uses the pinned Gradle 8.13 distribution installed
    # by gradle/actions/setup-gradle, not ./gradlew (which is absent).
    gradle = shutil.which("gradle")
    if not gradle:
        raise ValueError("Gradle 8.13 missing: install using gradle/actions/setup-gradle")
    return [gradle, "--no-daemon", "--console=plain", "--max-workers=4",
            TASK, "--tests", TEST_CLASS]


def run_gradle(root: Path, timeout: int) -> dict:
    t0 = time.monotonic()
    try:
        completed = subprocess.run(mutation_command(root), cwd=root, capture_output=True,
                                   text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        return {"status": "timeout", "seconds": round(time.monotonic() - t0, 1)}
    combined = (completed.stdout or "")[-10000:] + "\n" + (completed.stderr or "")[-6000:]
    # A Kotlin compile failure must NOT count as tests detecting a behavioral mutation.
    failed_test_task = bool(re.search(r":app:testDebugUnitTest\s+FAILED|There were failing tests|[1-9]\d* tests? failed",
                                      combined, re.IGNORECASE))
    return {"status": "passed" if completed.returncode == 0 else
            "tests_failed" if failed_test_task else "inconclusive",
            "seconds": round(time.monotonic() - t0, 1)}


def execute(root: Path, max_mutants: int, timeout: int) -> dict:
    if (os.getenv("CI") != "true" or os.getenv("GITHUB_ACTIONS") != "true" or
            os.getenv("SENTINEL_KOTLIN_MUTATION_APPROVED") != "1"):
        raise PermissionError("Kotlin mutations require an explicitly approved isolated GitHub Actions runner")
    if max_mutants < 1 or max_mutants > MAX_MUTANTS or timeout < 60 or timeout > TEST_TIMEOUT:
        raise ValueError("Mutation budget outside configured bounds")
    if git(root, "rev-parse", "--show-toplevel") != str(root.resolve()):
        raise ValueError("Repository root mismatch")
    branch = os.getenv("GITHUB_SHA", "").lower()
    if not ROOT_SHA.fullmatch(branch):
        raise ValueError("Expected full immutable GitHub SHA")
    if git(root, "rev-parse", "HEAD").lower() != branch:
        raise ValueError("Checkout does not match requested head SHA")
    if git(root, "status", "--porcelain", "--", TARGET):
        raise ValueError("Kotlin source already modified; refusing to overwrite")
    filepath = root / TARGET
    original = filepath.read_bytes()
    if len(original) > 256_000:
        raise ValueError("Kotlin source too large")
    baseline = run_gradle(root, timeout)
    if baseline["status"] != "passed":
        raise RuntimeError("Baseline unit tests not green; no mutation may be counted")
    text = original.decode("utf-8")
    cases = []
    try:
        for item in MUTATIONS[:max_mutants]:
            # Reset before each mutant, and guarantee restoration after any exception.
            mutated = mutated_text(text, item).encode("utf-8")
            filepath.write_bytes(mutated)
            try:
                observed = run_gradle(root, timeout)
            finally:
                filepath.write_bytes(original)
            cases.append({
                "id": item.identifier, "behavior": item.behavior,
                "outcome": "killed" if observed["status"] == "tests_failed" else
                "survived" if observed["status"] == "passed" else "inconclusive",
                "seconds": observed["seconds"],
            })
    finally:
        filepath.write_bytes(original)
    if filepath.read_bytes() != original:
        raise RuntimeError("Mutation source restoration failed")
    killed = sum(x["outcome"] == "killed" for x in cases)
    survived = sum(x["outcome"] == "survived" for x in cases)
    inconclusive = sum(x["outcome"] == "inconclusive" for x in cases)
    return {
        **plan(root),
        "sha": branch,
        "baseline": baseline,
        "results": cases,
        "summary": {"killed": killed, "survived": survived, "inconclusive": inconclusive,
                    "score": killed / (killed + survived) if killed + survived else None},
        "limits": "Only one targeted Kotlin test class. Results do not prove no Android regressions.",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("plan", "run"), default="plan")
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--max-mutants", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=TEST_TIMEOUT)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        root = args.root.resolve()
        report = plan(root) if args.mode == "plan" else execute(root, args.max_mutants, args.timeout)
        payload = json.dumps(report, ensure_ascii=False, indent=2)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(payload + "\n", encoding="utf-8")
        print(payload)
        if args.mode == "run" and (report["summary"]["inconclusive"] or report["summary"]["survived"]):
            return 1
        return 0
    except (ValueError, PermissionError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        print("Ultra Sentinel Kotlin mutation: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
