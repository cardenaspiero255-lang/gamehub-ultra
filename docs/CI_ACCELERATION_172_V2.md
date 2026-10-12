# PR #172 — CI V2: independent benchmark critical path (2026-10-12)

This supersedes the old Android 5/Coverage 8 and preinstalled-SDK probe optimization. The branch merges the current main tree, then changes only the new benchmark topology, concurrency contracts and safe Android tools selection.

## Observed baseline

- Android main PR #187 (SHA 7cbdabf): 20 jobs PASS; longest performance-a was 562 s; performance-b 373 s; performance-a sequential baseline and startup 2m22s + 49s in the later main run.
- Android main merge #187 (SHA 7aa5df3): performance-a 476s; performance-b 467s; total Android ~8 minutes.
- Coverage old main (PR #171): 6m25s; original PR #172 recorded 3m40s with 8 parallel workers, but did **not** reduce the Android critical path.

## V2 strategy

- **Six** real API 35 device runners: release APK, UI, baseline profile, startup macro, library macro, settings macro. Each previously packed performance check is now independently executed; none is replaced by an inert command or skipped test.
- **Four** Android test workers and **eight** simultaneous Coverage workers (eight total Coverage shards, 64 logical). BundleRelease now depends on quality-lint, serializing two non-critical support jobs. Two early support workers (Lint and quality contracts) plus 4 Android unit + 6 device + 8 Coverage = at most **20 primary runners in the first wave** under the accounting contract. The bundle still runs fully and is required by the final fan-in. CI may still queue for unrelated workflows.
- Release signing, Sentry, 90% patch coverage, full JaCoCo aggregation, adversarial/SDK retry tests, result fan-ins, benchmark XML and report artifacts remain blocking.
- Uses the trusted main SDK action, **not** the old preinstalled-SDK probe. Retains the separate non-destructive cmdline-tools/latest correctness fix because the previous destructive replacement was a confirmed CI failure.
- Every workflow/contract mutation is checked by `.github/scripts/test-ci-safety-contract-regressions.py`; unknown evidence fails closed. No self-approval of protected workflows.

## Acceptance and measurement

Compare successful GitHub Actions runs of the previous main against this PR's HEAD (wall-clock, longest shard, queue, benchmark XML, Android 20+ jobs, Coverage 9/9). Do not state a guaranteed number of minutes until real CI validates it. Require trusted AST and independent approval on the exact SHA for modified workflows before merging.

## V3: critical-path scheduling verification

AAB `release-bundle` is not the Android critical path in measured V2 (roughly one minute versus 6m42s). Deferring it behind required Lint frees one early worker, enabling all eight Coverage physical shards concurrently without exceeding a 20-runner first-wave budget. The full AAB build, artifact upload and blocking final build fan-in remain unchanged. The new CI contract and mutation suite reject an eager bundle and any reduction/overcommit of Coverage. Compare the newly triggered Actions runs with V2 (Android 6m42s, Coverage 5m17s) and base main (Android 8m19s, Coverage 6m25s); figures are observations, not guarantees.
