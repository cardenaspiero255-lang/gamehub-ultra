#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / ".github/workflows/android.yml"


def fail(message: str) -> None:
    raise SystemExit(message)


def named_step(job: dict, name: str) -> dict:
    matches = [
        step
        for step in job.get("steps", [])
        if isinstance(step, dict) and step.get("name") == name
    ]
    if len(matches) != 1:
        fail(
            f"Phase 3 block 3: expected exactly one {name!r} step; "
            f"found {len(matches)}"
        )
    return matches[0]


def validate(android_text: str) -> None:
    workflow = yaml.safe_load(android_text)
    jobs = workflow.get("jobs", {})
    quality = jobs.get("quality", {})
    build = jobs.get("build", {})
    if not isinstance(quality, dict) or not isinstance(build, dict):
        fail("Phase 3 block 3: quality/build jobs are missing")

    producer = named_step(quality, "Record Phase 3 artifact provenance")
    producer_env = producer.get("env", {})
    if producer_env.get("PHASE3_RUN_ID") != "${{ github.run_id }}":
        fail("Phase 3 block 3: producer is not bound to the current run")
    if producer_env.get("PHASE3_SHA") != "${{ github.sha }}":
        fail("Phase 3 block 3: producer is not bound to the current commit")

    producer_run = producer.get("run", "")
    producer_requirements = (
        'sha256sum "$APK"',
        "run_id=%s",
        '"$PHASE3_RUN_ID"',
        "sha=%s",
        '"$PHASE3_SHA"',
        "apk_sha256=%s",
        '"$APK_SHA256"',
    )
    missing = [item for item in producer_requirements if item not in producer_run]
    if missing:
        fail(
            "Phase 3 block 3: producer provenance manifest is incomplete: "
            f"{missing!r}"
        )

    upload = named_step(quality, "Upload debug APK")
    if not str(upload.get("uses", "")).startswith("actions/upload-artifact@"):
        fail("Phase 3 block 3: debug producer no longer uploads an artifact")
    upload_with = upload.get("with", {})
    if upload_with.get("name") != "gamehub-ultra-debug":
        fail("Phase 3 block 3: debug artifact name drifted")
    upload_paths = str(upload_with.get("path", ""))
    for required_path in (
        "app/build/outputs/apk/debug/app-debug.apk",
        "app/build/outputs/apk/debug/phase3-artifact-provenance.env",
    ):
        if required_path not in upload_paths:
            fail(f"Phase 3 block 3: uploaded artifact is missing {required_path}")

    download = named_step(build, "Download exact-run debug artifact")
    if not str(download.get("uses", "")).startswith("actions/download-artifact@"):
        fail("Phase 3 block 3: build no longer downloads the reusable artifact")
    download_with = download.get("with", {})
    if download_with.get("name") != "gamehub-ultra-debug":
        fail("Phase 3 block 3: consumer artifact name no longer matches producer")

    selected_run = download_with.get("run-id")
    selected_repository = download_with.get("repository")
    selected_token = download_with.get("github-token")
    if selected_run not in (None, "${{ github.run_id }}"):
        fail("Phase 3 block 3: consumer can select a foreign workflow run")
    if selected_repository not in (None, "${{ github.repository }}"):
        fail("Phase 3 block 3: consumer can select a foreign repository")
    if selected_run is None and (
        selected_repository is not None or selected_token is not None
    ):
        fail(
            "Phase 3 block 3: cross-run artifact credentials require the exact current run"
        )
    if selected_run is not None and selected_token not in (
        "${{ github.token }}",
        "${{ secrets.GITHUB_TOKEN }}",
    ):
        fail("Phase 3 block 3: explicit same-run download uses an untrusted token")

    verifier = named_step(build, "Verify Phase 3 artifact provenance")
    verifier_env = verifier.get("env", {})
    verifier_run = verifier.get("run", "")
    run_bound = (
        verifier_env.get("EXPECTED_RUN_ID") == "${{ github.run_id }}"
        or "GITHUB_RUN_ID" in verifier_run
    )
    sha_bound = (
        verifier_env.get("EXPECTED_SHA") == "${{ github.sha }}"
        or "GITHUB_SHA" in verifier_run
    )
    if not run_bound:
        fail("Phase 3 block 3: consumer does not verify the current run")
    if not sha_bound:
        fail("Phase 3 block 3: consumer does not verify the current commit")

    verifier_requirements = (
        'grep -Fxq "run_id=$EXPECTED_RUN_ID" "$PROVENANCE"',
        'grep -Fxq "sha=$EXPECTED_SHA" "$PROVENANCE"',
        'sha256sum "$APK"',
        'test "$ACTUAL_SHA256" = "$RECORDED_SHA256"',
    )
    missing = [item for item in verifier_requirements if item not in verifier_run]
    if missing:
        fail(
            "Phase 3 block 3: consumer provenance verification is incomplete: "
            f"{missing!r}"
        )


def replace_once(text: str, needle: str, replacement: str) -> str:
    if needle not in text:
        fail(f"Fixture drift: artifact reuse fixture not found: {needle!r}")
    return text.replace(needle, replacement, 1)


def foreign_run(android: str) -> str:
    needle = """          name: gamehub-ultra-debug
          path: ${{ runner.temp }}/phase3-debug-reuse
"""
    replacement = """          name: gamehub-ultra-debug
          path: ${{ runner.temp }}/phase3-debug-reuse
          github-token: ${{ github.token }}
          run-id: 1
"""
    return replace_once(android, needle, replacement)


def wrong_sha(android: str) -> str:
    return replace_once(
        android,
        "          EXPECTED_SHA: ${{ github.sha }}\n",
        "          EXPECTED_SHA: ${{ github.ref }}\n",
    )


def wrong_run_binding(android: str) -> str:
    return replace_once(
        android,
        "          PHASE3_RUN_ID: ${{ github.run_id }}\n",
        "          PHASE3_RUN_ID: 1\n",
    )


def skip_hash_check(android: str) -> str:
    return replace_once(
        android,
        '          test "$ACTUAL_SHA256" = "$RECORDED_SHA256"\n',
        '          echo "$ACTUAL_SHA256 $RECORDED_SHA256" >/dev/null\n',
    )


def expect_rejected(label: str, original: str, mutate) -> None:
    mutated = mutate(original)
    try:
        validate(mutated)
    except SystemExit:
        return
    fail(f"Phase 3 block 3 unsafe mutation was not rejected: {label}")


def main() -> None:
    android = ANDROID.read_text(encoding="utf-8")
    validate(android)
    expect_rejected("foreign workflow run", android, foreign_run)
    expect_rejected("mismatched commit binding", android, wrong_sha)
    expect_rejected("mismatched producer run", android, wrong_run_binding)
    expect_rejected("artifact hash verification removed", android, skip_hash_check)
    print("Phase 3 artifact reuse provenance contract passed.")


if __name__ == "__main__":
    main()
