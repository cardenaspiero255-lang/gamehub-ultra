# CAR-26 — Release hardening validation

Validated against commit 95d02dfe301271492a0848c90f14f78a421876c1 (2026-09-24).

## Acceptance gate

- JVM/unit tests: passed in the latest Android build workflow.
- Android debug APK: built successfully.
- Android lint: included in the workflow and completed successfully with the latest build.
- Android release APK/AAB: built successfully in the latest build workflow.
- Baseline profile and macrobenchmark jobs are part of the same validation pipeline.
- Account, library, voice, AI, performance-engine and Quick Settings code paths remain covered by the existing regression suite.
- No new privileged API claims are introduced by CAR-26.
- Session history remains bounded and stores no credentials or provider tokens.

## Result

CAR-26 is complete only on the main branch after the post-validation commit also reaches a successful Android build.