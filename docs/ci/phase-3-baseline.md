# Phase 3 CI baseline

Baseline commit: `8f0ce10ccdc343df631b398554654591a8ce46a5`

## Safety contract

Phase 3 optimizes CI latency and duplicated work without removing or weakening:

- unit-test coverage and Codecov publication;
- lint/build quality gates;
- API 35 install, update, and uninstall validation;
- MainActivity device wiring validation;
- Baseline Profile generation;
- Macrobenchmark execution;
- CAR-29 emulator-noise rejection;
- installable release outputs.

## Current execution boundaries

The current pipeline initializes independent Android/Gradle environments for:

1. `android.yml / quality`
2. `android.yml / device-validation`
3. `coverage.yml / coverage`
4. `sonar.yml / sonar` when Sonar runs

Within `android.yml`, release and performance variants are already built in one Gradle DAG and overlap with API 35 emulator startup. Configuration Cache reuse is explicitly verified. Device validation owns Gradle cache writes while quality is read-only.

## Phase 3 optimization target

Prefer removing duplicated setup/build work across workflow boundaries and reusing artifacts only when provenance is tied to the exact commit SHA. Do not trade validation depth for elapsed-time improvements.

Every optimization cut must compare against this baseline and retain the safety contract above.
