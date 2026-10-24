# CAR-29 · Android persistence E2E for adaptive replay × proposal-version rollback

## Production behavior being verified

Two previously distinct original GameHub fault mechanisms are exercised along one Android application data path:

| Source mechanism | Original GameHub source | Observed risk | Expected safeguard |
| --- | --- | --- | --- |
| Ultra Sentinel #153:71 | `PerGameAdaptiveRuntime.kt` | Pending session replay could lose/contradict the persisted target profile after an interrupted write | Failed event storage restores the previous profile; the pending decision is retried once after the optimizer is recreated and produces exactly one event |
| Ultra Sentinel #152:41 | `AiProfileProposalStore.kt` | A delayed lower-version applied proposal can replace the rollback destination of a newer proposal | A proposal with version below the persisted version does not change rollback version or known-good config |

## Test contract

- `AiProfileProposalMonotonicVersionRegressionTest` (Robolectric): RED/GREEN regression for the monotonic proposal checkpoint, with per-game isolation and forward progress.
- `GameHubRollbackPersistenceE2ETest` (Android instrumented on isolated API-35 emulator): real Android SessionCoach persistent sample store, production GameHub selection repository with explicit/adaptive selection policy, DataStore profile persistence, SharedPreferences adaptive optimizer and proposal rollback, a deliberately failing performance-event persistence attempt, a new optimizer/store instance, and MainActivity startup (no process kill in the default CI run). Assert one replay event, no stranded pending decision, correct rollback target, and no stale-version overwrite.

The E2E deliberately injects a failure at the production event-persistence boundary to make replay deterministic. This is **a real persistence integration E2E**, not a physical-device trial or a test of tapping the exact UI buttons. It must not be described as either.

Run with existing `.github/workflows/android.yml`: JVM regression is selected by deterministic unit-test sharding, while the `device-validation-ui` shard runs `gradle :app:connectedDebugAndroidTest` on API-35 emulator. The protected workflow still **does not upload per-test JUnit XML**; changing it on this PR failed existing CAR-29 security review and was reverted.

For an *authorized, isolated emulator* with one connected ADB device, run from the repository root:

```bash
bash scripts/car29-verify-android-e2e.sh
```

The script enforces exactly one passing, non-skipped JUnit testcase; computes a SHA-256 for that JUnit XML and the instrumentation logs; builds a durable fixture with real Android repositories; `am force-stop`s the app; checks that the old app process is gone; cold-launches MainActivity; and invokes the exact same instrumentation test in a **separate verify phase** to re-read the original persisted session/profile/rollback without rewriting the fixture. The JSON receipt contains the exact Git commit SHA. The driver refuses physical phones and multiple devices.

**Evidence status:** This reproducible two-phase driver has been added but has **not** been executed on an emulator by the authoring environment. Until its receipt is genuinely produced, verified, and independently reconciled with CI, the true cold-start E2E is **not certified**. This script does not change CAR-29 workflows, and does not imply physical-device certification.

## Prior Ultra Sentinel experimental evidence (no GOLD promotion)

- [Ultra Sentinel PR #209](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/pull/209): original source-Kotlin rollback #153:71 × #152:41; six JVM variants with mutant-sensitive benign #153:69.
- [Original Kotlin run 39952600537](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/actions/runs/39952600537): owner artifact `12501326370`, ZIP SHA-256 `0cc4fdcb3c672ddfe6f6aeba35fd63cff5a909f79b1105aa9d7c34cb1532c910`.
- [Ultra Sentinel PR #215](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/pull/215): subsequent **shared-JVM** 2×2 experiment with original Kotlin modules, test-only ABI bridge, and a modeled shared rollback state; [run 40025122655](https://github.com/cardenaspiero255-lang/Ultra-Sentinel/actions/runs/40025122655). It is stronger than #209, but still NOT an Android production call graph.
- The Android E2E deliberately delivers a stale version *while adaptive replay remains pending*, then reopens the app stores and resumes. It tests production persistence interoperability and its invariant, not all four original/repaired variants in a production Android 2×2 factorial design.

Both sources are historical *development* defects; the result does **not** establish two independent causal families, a new external GOLD truth label, detector precision, recall, F1 or SSS+ certification.

## Production approval and rollback transaction

- `AiProfileProposalStore.applyProposal` rejects stale or repeated versions **before** calling the suspend DataStore writer, and commits the version/rollback checkpoint only after the write returns successfully.
- `AiProfileProposalStore.rollbackIfCurrent` re-checks the expected durable version under the same process-wide coroutine mutex, awaits the profile restore, then clears only a matching version.
- Both mutation entry points are now used by the actual `GameHubUltraApp` Compose callbacks through `GameHubViewModel.persistGameProfileConfig`; the earlier callback-based helper is retained for legacy unit tests but is not used by production UI.
- `AiProfileTransactionalMutationTest` covers rejected stale active writes, failed saves, outdated rollback clear, and a suspended write concurrent with an old rollback from another instance.
- Limit: DataStore and SharedPreferences are separate persistence engines; the mutation mutex guarantees **in-process ordering**, not a crash-atomic multi-store transaction.

## Additional version-fence regressions

- Lower-version callbacks cannot rewind the applied version or rollback target.
- An equal-version callback cannot overwrite the previously approved target.
- An equal-version delayed callback cannot **resurrect** the rollback after explicit user removal. The persistent version remains monotonic.
- `AiProfileProposalStore.recordApplied` uses one process-wide checkpoint lock so different store objects cannot interleave the version read and write.
- The default Android E2E interleaves the stale proposal with an outstanding adaptive replay and checks one persisted event after dependency recreation. The optional two-phase ADB driver adds the actual OS process force-stop/start boundary and reads the same original session from a new process.

## Safety and reporting

Keep CAR-29 build/test/coverage gates enabled. CI emulator proof does not substitute for a physical Vivo V25 Pro and does not certify the UI-click flow; do not report final end-to-end certification until these additional validations are performed.
