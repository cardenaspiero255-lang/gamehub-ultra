#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"
COVERAGE = ROOT / ".github/workflows/coverage.yml"
COVERAGE_POST = ROOT / ".github/workflows/coverage-post-processing.yml"
SHADOW_METRICS = ROOT / ".github/workflows/ci-metrics-shadow.yml"
CHECKER = ROOT / ".github/scripts/check-ci-safety-contract.py"
CONTRACT = ROOT / ".github/scripts/test-ci-safety-contract.sh"


def run_contract(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["bash", str(root / ".github/scripts/test-ci-safety-contract.sh")],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )


def current_contract_must_pass() -> None:
    result = run_contract(ROOT)
    if result.returncode != 0:
        raise SystemExit(
            "Current optimized workflows must satisfy the CI contract.\n"
            f"--- contract output ---\n{result.stdout}"
        )


def reject_mutation(
    label: str,
    *,
    android_replace: tuple[str, str] | None = None,
    coverage_replace: tuple[str, str] | None = None,
    post_replace: tuple[str, str] | None = None,
) -> None:
    with tempfile.TemporaryDirectory(prefix="gamehub-ci-contract-") as raw:
        temp = Path(raw)
        (temp / ".github/workflows").mkdir(parents=True)
        (temp / ".github/scripts").mkdir(parents=True)

        android = ANDROID.read_text(encoding="utf-8")
        coverage = COVERAGE.read_text(encoding="utf-8")
        post = COVERAGE_POST.read_text(encoding="utf-8")

        for replacement, target_name in (
            (android_replace, "android"),
            (coverage_replace, "coverage"),
            (post_replace, "post"),
        ):
            if replacement is None:
                continue
            old, new = replacement
            source = {
                "android": android,
                "coverage": coverage,
                "post": post,
            }[target_name]
            if old not in source:
                raise SystemExit(
                    f"Fixture drift for {label}: {target_name} needle missing"
                )
            source = source.replace(old, new, 1)
            if target_name == "android":
                android = source
            elif target_name == "coverage":
                coverage = source
            else:
                post = source

        (temp / ".github/workflows/android.yml").write_text(
            android,
            encoding="utf-8",
        )
        (temp / ".github/workflows/coverage.yml").write_text(
            coverage,
            encoding="utf-8",
        )
        (temp / ".github/workflows/coverage-post-processing.yml").write_text(
            post,
            encoding="utf-8",
        )
        shutil.copy2(
            SHADOW_METRICS,
            temp / ".github/workflows/ci-metrics-shadow.yml",
        )
        shutil.copy2(
            CHECKER,
            temp / ".github/scripts/check-ci-safety-contract.py",
        )
        shutil.copy2(
            CONTRACT,
            temp / ".github/scripts/test-ci-safety-contract.sh",
        )

        result = run_contract(temp)
        if result.returncode == 0:
            raise SystemExit(
                f"Unsafe CI mutation was accepted: {label}"
            )


def main() -> None:
    current_contract_must_pass()

    reject_mutation(
        "quality lint made advisory",
        android_replace=(
            "      - name: Run fast quality gates\n        shell: bash\n",
            "      - name: Run fast quality gates\n"
            "        continue-on-error: true\n"
            "        shell: bash\n",
        ),
    )
    reject_mutation(
        "quality retry bound weakened",
        android_replace=(
            "          QUALITY_MAX_ATTEMPTS=3\n",
            "          QUALITY_MAX_ATTEMPTS=1\n",
        ),
    )
    reject_mutation(
        "quality Configuration Cache removed",
        android_replace=(
            "              --configuration-cache \\\n"
            "              --configuration-cache-problems=fail \\\n"
            "              --console=plain \\\n",
            "              --configuration-cache-problems=fail \\\n"
            "              --console=plain \\\n",
        ),
    )
    reject_mutation(
        "macro settings shard removed",
        android_replace=(
            "        shard: [release, ui, baseline, macro-startup, macro-library, macro-settings]\n",
            "        shard: [release, ui, baseline, macro-startup, macro-library]\n",
        ),
    )
    reject_mutation(
        "device parallelism reduced",
        android_replace=(
            "      max-parallel: 6\n",
            "      max-parallel: 3\n",
        ),
    )
    reject_mutation(
        "runtime install overlap removed",
        android_replace=(
            "          SDK_PID=$!\n",
            "          SDK_PID=0\n",
        ),
    )
    reject_mutation(
        "release update validation removed",
        android_replace=(
            '              adb install -r "$APK"\n',
            "",
        ),
    )
    reject_mutation(
        "UI instrumentation removed",
        android_replace=(
            "              gradle :app:connectedDebugAndroidTest \\\n",
            "              echo connectedDebugAndroidTest \\\n",
        ),
    )
    reject_mutation(
        "baseline rule disabled",
        android_replace=(
            '                RULE="BaselineProfile"\n',
            '                RULE="DisabledBaseline"\n',
        ),
    )
    reject_mutation(
        "macrobenchmark rule disabled",
        android_replace=(
            '                RULE="Macrobenchmark"\n',
            '                RULE="DisabledMacro"\n',
        ),
    )
    reject_mutation(
        "benchmark skipped-test failure removed",
        android_replace=(
            '              raise SystemExit(f"Required performance tests were skipped: {skipped}")\n',
            "              print(skipped)\n",
        ),
    )
    reject_mutation(
        "release research-key whitespace guard weakened",
        android_replace=(
            '          research_key_compact="${SUPABASE_PUBLISHABLE_KEY//[[:space:]]/}"\n',
            '          research_key_compact="${SUPABASE_PUBLISHABLE_KEY:-}"\n',
        ),
    )
    reject_mutation(
        "release artifact research guard removed",
        android_replace=(
            "        if: matrix.shard == 'release' && env.RESEARCH_RELEASE_READY == 'true'\n",
            "        if: matrix.shard == 'release'\n",
        ),
    )
    reject_mutation(
        "device fan-in no longer blocks on all shards",
        android_replace=(
            '          test "${{ needs.device-validation-shard.result }}" = "success"\n',
            "",
        ),
    )
    reject_mutation(
        "build fan-in loses device-validation result",
        android_replace=(
            '          test "${{ needs.device-validation.result }}" = "success"\n',
            "",
        ),
    )
    reject_mutation(
        "debug artifact current-run binding removed",
        android_replace=(
            "          run-id: ${{ github.run_id }}\n",
            "          run-id: 1\n",
        ),
    )
    reject_mutation(
        "coverage made advisory",
        coverage_replace=(
            "      - name: Generate debug unit-test coverage\n"
            "        shell: bash\n",
            "      - name: Generate debug unit-test coverage\n"
            "        continue-on-error: true\n"
            "        shell: bash\n",
        ),
    )
    reject_mutation(
        "coverage command replaced by inert text",
        coverage_replace=(
            "          gradle :app:createDebugUnitTestCoverageReport \\\n",
            "          echo :app:createDebugUnitTestCoverageReport \\\n",
        ),
    )
    reject_mutation(
        "Codecov pull-request provenance removed",
        post_replace=(
            "          override_pr: ${{ github.event.workflow_run.pull_requests[0].number || '' }}\n",
            "",
        ),
    )

    print(
        "CI safety regression suite passed: accelerated parallel gates fail closed."
    )


if __name__ == "__main__":
    main()
