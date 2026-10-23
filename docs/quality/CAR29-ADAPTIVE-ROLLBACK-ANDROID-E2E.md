# CAR-29 · Android persistence E2E for adaptive replay × proposal-version rollback

## Production behavior being verified

Two previously distinct original GameHub fault mechanisms are exercised along one Android application data path:

| Source mechanism | Original GameHub source | Observed risk | Expected safeguard |
| --- | --- | --- | --- |
| Ultra Sentinel #153:71 | `PerGameAdaptiveRuntime.kt` | Pending session replay could lose/contradict the persisted target profile after an interrupted write | Failed event storage restores the previous profile; the pending decision is retried once after the optimizer is recreated and produces exactly one event |
| Ultra Sentinel #152:41 | `AiProfileProposalStore.kt` | A delayed lower-version applied proposal can replace the rollback destination of a newer proposal | A proposal with version below the persisted version does not change rollback version or known-good config |

## Test contract

- `AiProfileProposalMonotonicVersionRegressionTest` (Robolectric): RED/GREEN regression for the monotonic proposal checkpoint, with per-game isolation and forward progress.
- `GameHubRollbackPersistenceE2ETest` (Android instrumented on isolated API-35 emulator): real Android SessionCoach persistent sample store, production GameHub selection repository with explicit/adaptive selection policy, DataStore profile persistence, SharedPreferences adaptive optimizer and proposal rollback, a deliberately failing performance-event persistence attempt, a new optimizer/store instance, and MainActivity startup. Assert one replay event, no stranded pending decision, correct rollback target, and no stale-version overwrite.

The E2E deliberately injects a failure at the production event-persistence boundary to make replay deterministic. This is **a real persistence integration E2E**, not a physical-device trial or a test of tapping the exact UI buttons. It must not be described as either.

Run with existing `.github/workflows/android.yml`: JVM regression is selected by deterministic unit-test sharding, while the `device-validation-ui` shard runs `gradle :app:connectedDebugAndroidTest` on API-35 emulator. The only acceptable success proof is a JUnit XML containing the E2E test class and the complete test result, plus green required branch checks.

## Prior Ultra Sentinel experimental evidence (no GOLD promotion)

- [Ultra Sentinel PR #209](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/pull/209): original source-Kotlin rollback #153:71 × #152:41; six JVM variants with mutant-sensitive benign #153:69.
- [Original Kotlin run 39952600537](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/actions/runs/39952600537): owner artifact `12501326370`, ZIP SHA-256 `0cc4fdcb3c672ddfe6f6aeba35fd63cff5a909f79b1105aa9d7c34cb1532c910`.
- The original experiment paired **separate** JVM processes and did not demonstrate the production data path; that is why the Android E2E is necessary.

Both sources are historical *development* defects; the result does **not** establish two independent causal families, a new external GOLD truth label, detector precision, recall, F1 or SSS+ certification.

## Safety and reporting

Keep CAR-29 build/test/coverage gates enabled. CI emulator proof does not substitute for a physical Vivo V25 Pro and does not certify the UI-click flow; do not report final end-to-end certification until these additional validations are performed.
