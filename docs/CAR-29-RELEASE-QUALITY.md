# CAR-29 — Release quality and recovery

## Acceptance checklist

- Runtime diagnostics degrade to safe defaults when Android system services fail.
- Connectivity latency measurement is bounded, network-pinned, and returns null on failure or network changes.
- Game launch rejects blank package names and converts resolver/start failures into a clean failure result.
- DataStore corruption handlers and preference migrations remain enabled for persistent settings and session history.
- JVM unit tests run before release packaging.
- Debug lint, release APK, and release AAB build in CI.
- CI installs the release APK on API 35, launches the package, installs it again with replacement, and verifies uninstall removes the package.
- Baseline profile and macrobenchmark validation remain part of the same Android workflow.
- Release artifacts are uploaded only when present; missing artifacts fail the workflow.

## Completion gate

CAR-29 is considered complete only after the final commit on `main` has all required GitHub Actions jobs successful and no known failure remains.