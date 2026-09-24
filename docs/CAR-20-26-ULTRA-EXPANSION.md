# GameHub Ultra — CAR-20..30 expansion

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

## CAR-27 — Smart Game Assistant
- Contextual per-game recommendations based only on available device/game data.
- Suggest performance, thermal, refresh-rate and resolution settings without claiming unsupported capabilities.
- Quick “recommended / balanced / battery” profile presets.
- Explain why a recommendation was made.
- Keep recommendations local and privacy-conscious.

## CAR-28 — Performance Timeline
- Live session timeline for FPS/refresh rate, temperature, battery and performance profile changes when those metrics are actually available.
- Highlight thermal throttling or sudden metric changes.
- Session summary after exiting a game.
- Export/share a lightweight diagnostic report without credentials or tokens.

## CAR-29 — Ultra Game Center
- Rich game detail screen with icon, package, install state, favorite/recent state and profile.
- Per-game quick actions for launch, profile, diagnostics and session history.
- Optional categories/tags and local filtering.
- Keep package launching safe and avoid privileged operations that Android does not expose.

## CAR-30 — Ultra Final Experience
- Final unified GameHub Ultra experience combining dashboard, profiles, diagnostics, library and session history.
- Consistent red/black visual language across portrait and landscape.
- Accessibility pass: touch targets, contrast, readable text and content descriptions.
- Performance pass: avoid unnecessary recompositions/re-queries and keep startup responsive.
- Full regression and release validation before declaring Ultra complete.

## Acceptance gate for CAR-20..30
1. JVM/unit tests pass.
2. Android debug APK compiles.
3. Lint passes.
4. Relevant UI/instrumentation tests pass when applicable.
5. No regression in account, library, voice, AI, performance-engine or Quick Settings behavior.
6. No fabricated hardware/vendor capabilities or privileged API claims.
7. Only then mark the CAR complete.

## Current implementation note
CAR-20 and CAR-21 are implemented in the main branch. CAR-22..30 are expansion scope and must not be marked complete until their individual acceptance gates pass.
