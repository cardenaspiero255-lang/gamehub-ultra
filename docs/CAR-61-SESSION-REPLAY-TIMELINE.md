# CAR-61 — Session Replay Timeline (read-only first phase)

## Implemented
- A completed session's replay is derived exclusively from its persisted `GameSessionRecord` and `PerformanceEvent` records.
- Session-ID equality and inclusive `startedAtMillis..endedAtMillis` bounds prevent cross-session data leakage. Malformed, incomplete and contradictory sessions cannot be replayed.
- Exactly one beginning and one ending marker use observed session fields. Intermediate event markers show manual profile changes (`manual_profile_selection`), automatic profile changes with a recorded reason, unclassified changes, and numeric Android thermal status changes (0..6).
- Markers are shown chronologically, with relative elapsed time. A compact expandable replay is available on the Home session center for completed sessions only.
- **Privacy:** raw event detail strings are never displayed, exported or echoed in the replay. Dynamic profile fields come from known application enums; session identifiers are not shown.
- **Retention:** the session store currently retains at most 30 sessions by default; performance-event history retains at most 50 events globally. The replay renders at most 20 matching event markers plus start/end and honestly labels truncated records. The existing Session Center clear action removes session records and their replay controls.
- **Unknown data:** the current durable session record does not contain a historical stream of refresh rate, latency or full battery/thermal samples. The UI explicitly displays that refresh and latency history is unavailable instead of inventing samples, FPS, or measurements. Start/end battery values and a final thermal status are real only when stored.

## Tests
- JVM domain tests: strict session ownership, time boundary checks, deterministic ordering, manual/automatic/unknown profile attribution, safe handling of malformed thermal event payloads, missing telemetry, input with 40 markers and 20-event retention.
- Robolectric Compose: completed-session replay expansion/collapse, active sessions hidden, privacy of unrelated events and retention clearing.
- Required CAR-29: full Android build, Android test shards, emulator smoke tests, quality contracts, lint, JaCoCo patch line coverage >=90%, authenticated reviews and checks on the exact SHA.

## Outstanding work / deliberate scope
- Historical refresh rate and latency changes require first creating an opt-in session-scoped sampling persistence model and retention controls; these values cannot be reconstructed from the existing data. It is wrong to invent them or replay current in-memory telemetry as if it belonged to a historical session.
- CAR-58 #259, CAR-59 #260, CAR-60 #261 remain unmerged. This branch is stacked on the CAR-60 SHA `19221068dd3e1964a9d5fd5fc0f8355f9d2af583`. The PR temporarily targets `main` to trigger existing workflows; its diff includes pending predecessor changes and cannot be merged as one batch.
- Correct release order remains CAR-58 → CAR-59 → CAR-60 → CAR-61. Each must be isolated/rebased and fully re-validated on the eventual main SHA. Owner-controlled Epic OAuth and entitlements are not bypassed or claimed complete. Physical checks remain deferred through CAR-69.
