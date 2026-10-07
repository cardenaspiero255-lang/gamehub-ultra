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
        "packed quality worker removed",
        android_replace=(
            "          logical_shards=(0 1 2 3 4 5)\n",
            "          logical_shards=(0 1 2 3 4)\n",
        ),
    )
    reject_mutation(
        "packed quality failure propagation removed",
        android_replace=(
            '          exit "$status"\n',
            '          echo "$status"\n',
        ),
    )
    reject_mutation(
        "performance physical shard removed",
        android_replace=(
            "        shard: [release-apk, ui, performance-a, performance-b]\n",
            "        shard: [release-apk, ui, performance-a]\n",
        ),
    )
    reject_mutation(
        "device physical parallelism reduced",
        android_replace=(
            "      max-parallel: 4\n",
            "      max-parallel: 2\n",
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
            '                  RULE="BaselineProfile"\n',
            '                  RULE="DisabledBaseline"\n',
        ),
    )
    reject_mutation(
        "macrobenchmark rule disabled",
        android_replace=(
            '                  RULE="Macrobenchmark"\n',
            '                  RULE="DisabledMacro"\n',
        ),
    )
    reject_mutation(
        "packed startup macro removed",
        android_replace=(
            "                  LOGICAL_SHARDS=(baseline macro-startup)\n",
            "                  LOGICAL_SHARDS=(baseline)\n",
        ),
    )
    reject_mutation(
        "packed settings macro removed",
        android_replace=(
            "                  LOGICAL_SHARDS=(macro-library macro-settings)\n",
            "                  LOGICAL_SHARDS=(macro-library)\n",
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
        "release APK artifact research guard removed",
        android_replace=(
            "        if: matrix.shard == 'release-apk' && env.RESEARCH_RELEASE_READY == 'true'\n",
            "        if: matrix.shard == 'release-apk'\n",
        ),
    )
    reject_mutation(
        "release bundle Gradle task removed",
        android_replace=(
            "          gradle :app:bundleRelease \\\n",
            "          echo :app:bundleRelease \\\n",
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
        "build fan-in loses release bundle result",
        android_replace=(
            '          test "${{ needs.release-bundle.result }}" = "success"\n',
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
        "coverage physical runner removed",
        coverage_replace=(
            "        runner: [0, 1]\n",
            "        runner: [0]\n",
        ),
    )
    reject_mutation(
        "coverage logical shard count reduced",
        coverage_replace=(
            '      COVERAGE_LOGICAL_SHARD_COUNT: "8"\n',
            '      COVERAGE_LOGICAL_SHARD_COUNT: "4"\n',
        ),
    )
    reject_mutation(
        "coverage packed tests made advisory",
        coverage_replace=(
            "      - name: Run packed coverage shards\n        shell: bash\n",
            "      - name: Run packed coverage shards\n"
            "        continue-on-error: true\n"
            "        shell: bash\n",
        ),
    )
    reject_mutation(
        "coverage test command replaced by inert text",
        coverage_replace=(
            "          gradle :app:testDebugUnitTest \\\n",
            "          echo :app:testDebugUnitTest \\\n",
        ),
    )
    reject_mutation(
        "coverage aggregation command replaced by inert text",
        coverage_replace=(
            "          gradle :app:createShardedDebugUnitTestCoverageReport \\\n",
            "          echo :app:createShardedDebugUnitTestCoverageReport \\\n",
        ),
    )
    reject_mutation(
        "coverage patch threshold weakened",
        coverage_replace=(
            "            --min-patch-line 90\n",
            "            --min-patch-line 50\n",
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
        "CI safety regression suite passed: packed parallel gates fail closed."
    )


if __name__ == "__main__":
    main()
