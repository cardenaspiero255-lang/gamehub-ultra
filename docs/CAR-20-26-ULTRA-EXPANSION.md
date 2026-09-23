# GameHub Ultra — CAR-20..26 expansion

## CAR-20 — Ultra Dashboard
- Compact hardware and runtime metric chips.
- Active performance profile visibility.
- Thermal headroom indicator.
- Adaptive engine explanation.
- No privileged claims beyond exposed Android/vendor APIs.

## CAR-21 — Compact visual system
- Smaller typography already introduced by CAR-18.
- Consistent 8/10/12/16/20dp corner scale.
- Tighter dashboard spacing for landscape screens.
- Preserve red/black identity and readability.

## CAR-22 — Game profile cockpit
- Per-game profile summary.
- One-tap profile switching.
- Persist selected profile per package.
- Never promise frame generation when the platform/game does not expose it.

## CAR-23 — Gaming diagnostics
- Refresh-rate, latency, bandwidth, storage, battery, thermal and input status.
- Explain unavailable metrics instead of displaying fabricated values.
- Add a concise pre-session readiness summary.

## CAR-24 — Library experience
- Favorites and recents remain first-class.
- Search stays local and lightweight.
- Add compact game rows and clearer selected-game state.
- Keep safe package launching.

## CAR-25 — Session history
- Session start/end records.
- Profile changes and thermal transitions.
- Compact recent-session view.
- Avoid retaining credentials or provider tokens.

## CAR-26 — Release hardening
Acceptance gate for each CAR:
1. JVM/unit tests pass.
2. Android debug APK compiles.
3. Lint passes.
4. No regression in account, library, voice, AI, performance-engine or Quick Settings behavior.
5. Only then mark the CAR complete.

## Current implementation note
CAR-20 and CAR-21 are implemented in the main branch. CAR-22..26 are the next expansion scope and should not be marked complete until their acceptance gates pass.
