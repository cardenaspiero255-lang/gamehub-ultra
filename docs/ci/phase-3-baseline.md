# Phase 3 CI baseline

Baseline commit: `ed9ad65e83f96a58202f818a5edf34d936a99662`
Baseline run: Android build #1720 (push to `main`)
Result: success

## Safety contract

Phase 3 optimizes CI latency and duplicated work without removing or weakening:

- unit-test coverage and Codecov publication;
- lint/build quality gates;
- API 35 install, update, and uninstall validation;
- MainActivity device wiring validation;
- Baseline Profile generation;
- Macrobenchmark execution;
- CAR-29 emulator-noise rejection;
- installable release APK/AAB outputs.

## Baseline execution map

Android build #1720 completed all four jobs successfully:

1. `quality`
2. `device-validation`
3. `build` (aggregate gate)
4. `metrics`

The critical expensive boundary remains `device-validation`. It owns the API 35 emulator, release/performance build graph, device tests, Baseline Profile and Macrobenchmark validation.

## Confirmed duplicated or repeated work

- `quality` builds `:app:assembleDebug` while `device-validation` later builds release/performance variants. These variants serve different gates and must not be collapsed without provenance-safe reuse.
- `device-validation` first builds telemetry-disabled Release + AAB + non-minified performance variants for validation.
- On trusted non-PR runs it later executes `:app:assembleRelease :app:bundleRelease --rerun-tasks` to produce distributable Sentry-aware outputs. This is a real repeated Release graph, but it intentionally changes telemetry/release inputs and therefore is **not safe to delete blindly**.
- Android/JDK/Gradle setup is repeated across independent jobs/workflows. Cross-job reuse is only acceptable when tied to the exact commit SHA and does not weaken isolation or validation.
- Configuration Cache reuse is already explicitly proven inside the quality and release/performance graphs; Phase 3 must not regress that contract.

## Optimization decision rules

1. Measure before changing.
2. Preserve every safety gate above.
3. Reuse artifacts only with exact-SHA provenance.
4. Do not reuse telemetry-disabled validation binaries as distributable Sentry-enabled outputs.
5. Do not trade device-validation depth for wall-clock improvements.
6. A cut is accepted only after its workflows are green and CAR-29 review finds no confirmed errors.

## Phase 3 target

Prefer removing setup/build duplication across workflow boundaries, improving the job DAG, and safely reusing immutable outputs. The final phase report must compare the optimized pipeline against Android build #1720 rather than against the pre-Maximum-Structure baseline.


## Cut 2 measurement findings

Android build #1721 is a pull-request validation run for this Phase 3 branch. It also completed `quality`, `device-validation`, `build`, and `metrics` successfully, but it is **not** an elapsed-time replacement for #1720:

- #1720 is a trusted `push` to `main`; Sentry discovery, distributable Release rebuild, and release registration execute there.
- #1721 is a `pull_request` run; those trusted-release steps are intentionally skipped because PR workflows do not receive that release path.
- Therefore Phase 3 performance claims must compare equivalent event classes. PR cuts are used for safety validation; final wall-clock improvement is measured against a post-merge trusted `main` run.

### Safe candidates for Block 2

The next block may change the workflow DAG only where validation semantics remain identical. Initial candidates are:

1. remove orchestration latency that exists only because independent successful gates wait on an otherwise trivial aggregate job;
2. move metrics collection off unnecessary serial dependencies when it can still observe the complete run safely;
3. preserve `quality` and `device-validation` as independent required gates unless exact-SHA artifact provenance proves a replacement equivalent;
4. leave the trusted Sentry distributable rebuild untouched until a dedicated cut proves an equivalent telemetry-enabled output without `--rerun-tasks`.

These are candidates, not assumed wins. Each must be measured independently and reverted if it adds fragility or does not improve equivalent wall-clock latency.
