# GameHub Ultra — CAR-31..70 Next-Gen Roadmap

This roadmap begins only after CAR-29 and CAR-30 satisfy their full acceptance gates. It expands GameHub Ultra without fabricating hardware capabilities, relying on privileged APIs that Android does not expose, or hiding CI failures.

## Global acceptance gate for CAR-31..70
Every CAR must satisfy all applicable checks before it is marked complete:
1. JVM/unit tests pass.
2. Android debug APK compiles.
3. Lint passes with no new actionable errors.
4. Release APK and AAB compile where the CAR affects release code.
5. Relevant UI/instrumentation tests pass.
6. Package launch, account, library, voice, AI, performance engine and Quick Settings regressions are checked.
7. Accessibility requirements remain valid.
8. No fabricated FPS, thermal, driver, vendor, frame-generation or privileged capability claims.
9. Release/install/update/uninstall smoke checks stay green when applicable.
10. CI logs are reviewed for actionable errors and warnings, not just job status.
11. Only then mark the CAR complete.

# Phase A — Visual identity, responsive shell and UX

## CAR-31 — Canva Red Neon Identity
- Replace the current launcher artwork with the approved red/black GameHub Ultra controller + lightning identity.
- Adaptive launcher icon, round icon and legacy launcher assets.
- Remove white/black placeholder launcher treatment.
- Align splash, tile and shortcut iconography with the Ultra identity.
- Preserve clarity at small launcher sizes.

## CAR-32 — Landscape-first Responsive Dashboard
- Dedicated wide-screen composition instead of stretching portrait UI.
- Left navigation rail, central game/telemetry workspace and right Ultra assistant panel where width permits.
- Responsive breakpoints for phone landscape, foldables, tablets and handhelds.
- Portrait layout remains fully supported.
- Avoid duplicated expensive telemetry subscriptions across layouts.

## CAR-33 — Unified Ultra Navigation Shell
- Consistent Home, Library, Booster, Assistant and Settings destinations.
- Adaptive bottom bar/navigation rail.
- Preserve state while switching form factors and orientation.
- Deep-link routing for library, selected game and performance pages.
- Accessibility semantics for every navigation destination.

## CAR-34 — Ultra Game Center 2.0
- Rich selected-game hero panel.
- Compact quick actions: launch, profile, diagnostics, history and assistant.
- Favorites, recents, categories and local filters.
- Better empty/uninstalled/error states.
- Safe package-launch verification.

# Phase B — Ultra Voice: major evolution

## CAR-35 — Ultra Voice Core 2.0
- Split wake/listen/parse/execute/speak into testable layers.
- Explicit state machine: idle, wake detected, listening, understanding, acting, speaking, error.
- Cancellation, timeout and retry behavior.
- Keep microphone lifecycle tied to permission and app/service state.

## CAR-36 — Wake Word “Ultra” Reliability
- Improve wake-word debouncing and false-positive handling.
- Configurable wake sensitivity where supported.
- Wake cooldown and repeated-command suppression.
- Offline-first wake path where platform/model support exists.
- Clear fallback when continuous listening is unavailable.

## CAR-37 — Contextual Voice Commands
- Voice context includes selected game, active profile and current diagnostics.
- Commands like “Ultra, abre este juego”, “Ultra, modo equilibrado”, “Ultra, estado térmico”.
- Follow-up context for short commands without repeating the game name.
- Confirmation for destructive or high-impact actions.

## CAR-38 — In-session Ultra Voice
- Game-session-aware voice responses.
- Answer available FPS/refresh, battery, thermal, RAM and latency metrics.
- Never invent unavailable metrics.
- Short, low-distraction responses suitable while gaming.
- Session commands for profile changes only when safely supported.

## CAR-39 — Voice Command Personalization
- Local aliases for games and profiles.
- User-defined phrases mapped to safe allow-listed actions.
- Per-game preferred commands.
- Conflict detection when multiple aliases match.
- Export/import non-sensitive voice preferences.

## CAR-40 — Voice Resilience and Accessibility
- Graceful recovery after service death, permission revocation or audio focus loss.
- Text fallback for every voice action.
- Captions/transcript for responses.
- Large touch targets and screen-reader labels.
- Avoid always-on behavior when system restrictions prohibit it.

# Phase C — Local AI: major evolution

## CAR-41 — Ultra AI Core 2.0
- Separate observation, recommendation, explanation, feedback and memory layers.
- Structured inputs from real diagnostics and game profile state.
- Deterministic fallback when an on-device model is unavailable.
- No cloud dependency required for core recommendations.

## CAR-42 — AI Feedback Learning Loop
- Store local outcomes of recommendations.
- Track whether a change improved stability, thermal behavior, battery or user preference.
- Learn per-device/per-game preferences without storing credentials.
- Allow clearing learned optimization history.
- Prevent one bad session from dominating future recommendations.

## CAR-43 — AI Mistake Recovery
- Record rejected or reverted recommendations.
- Reduce confidence for repeatedly poor suggestions.
- Detect contradictory recommendations.
- Explain what changed after a previous suggestion performed poorly.
- Regression tests for feedback-loop behavior.

## CAR-44 — Explainable Game Recommendations
- Every suggestion includes reason, evidence and confidence.
- Distinguish measured data from inferred advice.
- Show unavailable data explicitly.
- Compare recommended, balanced and battery-oriented outcomes without fabricated guarantees.
- Keep advice concise in gaming mode.

## CAR-45 — AI Session Coach
- Pre-session readiness summary.
- Mid-session observations when meaningful changes occur.
- Post-session summary with actionable next steps.
- Identify recurring thermal, battery, refresh or latency patterns.
- Avoid noisy recommendations for insignificant changes.

## CAR-46 — AI Profile Builder
- Generate a proposed per-game profile from actual device/game observations.
- Require explicit application of significant changes.
- Version profile recommendations.
- Roll back to previous known-good profile.
- Keep unsupported settings disabled and explained.

# Phase D — Performance intelligence

## CAR-47 — Per-game Adaptive Optimizer 2.0
- Separate optimization state per package and version.
- Combine thermal, battery, refresh, memory and latency trends.
- Prevent rapid profile oscillation.
- Cooldown and hysteresis for adaptive decisions.
- Record reasons for every automatic decision.

## CAR-48 — Thermal Prediction
- Estimate thermal trend from recent real samples.
- Detect rising thermal pressure before obvious throttling where evidence supports it.
- Suggest lower-impact profiles before critical conditions.
- Do not report a prediction as a measured sensor value.
- Device-specific fallback when thermal APIs are unavailable.

## CAR-49 — Battery-aware Gaming Engine
- Session battery drain estimation from measured history.
- Battery-oriented mode recommendations.
- Charger/charging state awareness.
- Prevent aggressive recommendations when battery state is constrained.
- Post-session drain summary.

## CAR-50 — Frame Pacing & Refresh Intelligence
- Distinguish refresh rate from measured frame data.
- Detect unstable refresh/available frame pacing signals where APIs expose them.
- Explain when actual game FPS is not observable.
- Recommend compatible refresh/profile combinations.
- Never label interpolation as active unless verifiable.

## CAR-51 — Network Gaming Diagnostics 2.0
- Latency history and jitter estimation from legitimate network probes.
- Connectivity type changes during a session.
- Packet-loss style diagnostics only when actually measurable.
- Detect large latency spikes.
- Game-safe recommendations without pretending to control the network stack.

## CAR-52 — Memory & Storage Pressure Intelligence
- RAM pressure trend.
- Storage headroom warnings.
- Background pressure hints using public Android signals.
- Avoid “RAM cleaner” claims that require unsupported privileged behavior.
- Explain when Android itself controls memory reclamation.

# Phase E — GPU, drivers and hardware compatibility

## CAR-53 — GPU/Driver Capability Matrix
- Record GPU vendor/renderer/API capability fingerprint.
- Per-device compatibility rules from actual exposed capabilities.
- Distinguish Vulkan availability, renderer identity and driver claims.
- No silent driver switching when the OS/vendor does not expose it.
- Diagnostic explanation for unsupported driver controls.

## CAR-54 — Turnip/Snapdragon Compatibility Layer
- Detect supported environments where custom/alternate driver selection is legitimately available.
- Per-game driver preference metadata.
- Safe fallback to system driver.
- Validate selected driver availability before launch.
- Never assume all Snapdragon devices support Turnip selection.

## CAR-55 — Vendor Adapter Framework
- Isolate Qualcomm/MediaTek/Exynos/vendor-specific optional integrations.
- Capability-gated adapters.
- No crashes when vendor APIs are absent.
- Keep generic Android path fully functional.
- Tests for unsupported vendor paths.

## CAR-56 — Device Compatibility Database
- Local compatibility records keyed by non-sensitive device fingerprint.
- Known-good profile defaults based on validated observations.
- Versioned rules with safe fallback.
- Exportable diagnostic compatibility report.
- No device-wide unique identifier storage beyond what is necessary for local configuration.

# Phase F — Accounts and game libraries

## CAR-57 — Steam Multi-account 2.0
- Multiple Steam account profiles.
- Explicit active-account switching.
- Keep credentials/tokens out of plain preferences.
- Separate imported libraries per account when data source permits.
- Clear disconnected/expired state handling.

## CAR-58 — Epic Integration 2.0
- Harden Epic account/link flow.
- Clear unsupported-platform boundaries.
- Local library metadata cache where permitted.
- Connection health and refresh state.
- No credential scraping or unsupported login automation.

## CAR-59 — Unified Cross-store Library
- Merge local/Steam/Epic entries without losing source identity.
- Deduplicate titles safely.
- Filter by source, installed, favorite and recent.
- Per-source connection status.
- Offline cached browsing for already known metadata.

## CAR-60 — Account Privacy & Recovery
- Credential-store migration hardening.
- Re-authentication prompts when required.
- Redact secrets from logs and diagnostic exports.
- Account disconnect cleanup.
- Recovery from corrupted/expired connection state.

# Phase G — Sessions, benchmarks and diagnostics

## CAR-61 — Session Replay Timeline
- Reconstruct profile/thermal/battery/refresh/latency changes over a completed session.
- Event markers for manual and automatic profile changes.
- Compact timeline UI.
- No raw credential or private account data in sessions.
- Retention controls.

## CAR-62 — Ultra Performance Lab
- Repeatable local benchmark scenarios.
- Before/after comparisons for profile changes.
- Store benchmark context so comparisons are meaningful.
- Detect statistically weak samples.
- Never equate synthetic benchmark results with guaranteed game FPS.

## CAR-63 — Regression Detector
- Compare recent sessions against a known-good baseline.
- Flag meaningful startup, thermal, battery or latency regressions.
- Separate app regression from uncertain external conditions.
- Link findings to diagnostics and profile history.
- Clear false-positive dismissal flow.

## CAR-64 — Diagnostic Support Bundle
- Export sanitized app/version/device/diagnostic data.
- Include relevant non-secret logs and configuration.
- Redact tokens, credentials and personal account details.
- User-controlled export.
- Useful for GitHub bug reports without exposing secrets.

# Phase H — Controls, peripherals and gaming interaction

## CAR-65 — Controller Intelligence
- Detect gamepads and controller capabilities exposed by Android.
- Show connected controller state.
- Per-game controller notes/profile metadata.
- Input diagnostic screen.
- Avoid claiming remapping outside allowed APIs.

## CAR-66 — Gaming Peripheral Hub
- Headset/audio route, keyboard, mouse and controller status.
- Hot-plug updates.
- Compact pre-session peripheral readiness.
- Accessible device labels.
- Safe handling of unsupported peripherals.

## CAR-67 — Quick Actions 2.0
- Harden Quick Settings tile behavior.
- App shortcuts for library, assistant and selected game where appropriate.
- Intent/deep-link validation.
- No deprecated launch APIs.
- Tests for API-level behavior.

# Phase I — Security, privacy, recovery and maintainability

## CAR-68 — Security Hardening 2.0
- Audit exported activities/services/receivers.
- PendingIntent immutability/mutability review.
- Deep-link validation.
- Secret/log redaction.
- Dependency vulnerability review where tooling permits.

## CAR-69 — Recovery, Migration & Data Integrity 2.0
- Versioned preference/data migrations.
- Corruption recovery.
- Safe defaults after interrupted updates.
- Preserve user profiles/history when schema changes.
- Rollback-aware migration tests.

## CAR-70 — GameHub Ultra 2.0 Final Experience
- Integrate the completed CAR-31..69 features into one coherent product.
- Final red/black neon visual consistency across portrait and landscape.
- Full accessibility and localization pass.
- Startup/recomposition/query performance pass.
- Full unit, lint, UI/instrumentation, release APK/AAB, install/update/uninstall, baseline-profile and macrobenchmark validation.
- Review logs for actionable warnings/errors.
- Verify release artifacts.
- Only mark Ultra 2.0 complete when all applicable gates are green and there are no known unresolved release-blocking errors.

## CAR-71 — Ultra Conversational AI & Long-Term Memory 3.0
- Natural multi-turn conversation with follow-up questions, corrections and context carry-over.
- Unified text + voice conversation surface so Ultra can be spoken to or typed to like a full conversational assistant.
- Persistent local conversation history with timestamps, titles, search, filters and per-conversation threads.
- Scrollable history and fast jump to older conversations.
- Long-term memory layer that can retrieve relevant facts from prior conversations when useful instead of loading every old message into the prompt.
- Semantic retrieval over past conversations using local embeddings/indexes where device capability permits.
- Explicit memory controls: remember, forget, pin, archive, delete, export and import.
- Memory provenance in responses: Ultra should be able to distinguish current-session context from recalled prior-history context.
- Per-user and per-game memory scopes so game-specific preferences do not pollute unrelated conversations.
- Conversation summarization/compaction so long histories remain usable without unbounded prompt growth.
- Offline-first architecture: local model, local conversation database, local retrieval, local speech-to-text and local text-to-speech when supported by the device.
- Graceful capability tiers for devices that cannot run the full local model; never pretend an unavailable offline capability exists.
- Optional hybrid online enhancement may be added later, but core history, memory and supported assistant actions must keep working offline.
- Encrypted-at-rest storage for conversation history and memories containing potentially sensitive user information.
- Never store credentials, authentication tokens or secrets as conversational memory.
- User-visible privacy controls for clearing all AI history/memory and for disabling long-term memory entirely.
- Ground assistant answers in GameHub telemetry, selected game, profiles, diagnostics, session history and the user-approved memory store.
- Tool/action safety layer: conversational requests that change profiles, launch games or alter settings must validate supported actions before execution.
- Offline failure handling: if speech/model/retrieval components are unavailable, explain the limitation and fall back to the best supported local interaction instead of fabricating a result.
- Benchmark the assistant against a fixed task suite covering conversation quality, context retention, memory retrieval, offline task completion, latency, action success and hallucination rate.
- Treat “better than another assistant” only as a measurable benchmark goal; do not claim a 200–250% improvement without reproducible evidence from the benchmark suite.
- Add unit/integration/instrumentation coverage for conversation persistence, memory retrieval, deletion, migration, offline mode and action safety.
- CAR-71 is not complete until all applicable CI gates are green, review threads are resolved, full logs have been checked for hidden actionable errors and release artifacts remain valid.

## Beyond CAR-70
CAR numbering is not a technical limit. Future CARs can be added for new Android APIs, new hardware/vendor integrations, new stores, new on-device AI capabilities, new voice models, new accessibility requirements and new device classes. New CARs should only be added when they provide a concrete, testable improvement.
