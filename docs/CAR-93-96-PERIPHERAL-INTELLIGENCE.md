# GameHub Ultra — CAR-93..96 Peripheral Intelligence & Wireless Experience

Status: proposed requirements; not implemented or certified.

## Global rules
- Reuse, do not duplicate: CAR-65 Controller Intelligence; CAR-66 Gaming Peripheral Hub; CAR-51/CAR-72 network diagnostics; CAR-77 Adaptive Orchestrator; CAR-82 multi-device qualification; CAR-91 Landscape Command Center.
- Every field must identify provenance/availability: measured, platform-reported, device-reported, estimated or unavailable.
- Never invent codec, Bluetooth version, polling rate, input/audio latency, interference, device capability or applied optimization.
- Do not assume hidden/system APIs, root, Shizuku, VPN or privileged access. Optional privileged integrations require capability detection, explicit consent and safe fallback.
- Use TDD for policy, reducers, capability gates and failure paths. Require unit/integration/UI tests, debug/release builds, lint, CI/log review and no unresolved actionable findings.
- Hardware-dependent claims remain pending physical validation until reproducible evidence exists.

## CAR-93 — Ultra Peripheral Intelligence Orchestrator

### Objective
Add a capability-aware orchestration layer over existing peripheral discovery so Ultra can understand the active gaming setup and provide a trustworthy readiness view.

### Reuses
CAR-65, CAR-66, CAR-77 and CAR-91.

### Scope
- Unified state for supported gamepads, headsets/audio routes, keyboards, mice and other Android input/audio peripherals.
- Hot-plug/disconnect updates without duplicate subscriptions or wasteful polling.
- Capability discovery per device: transport, exposed input capabilities, buttons/axes, audio route and only metadata available through public APIs.
- Per-game peripheral association and preferred-device metadata.
- Pre-session readiness panel: connected/disconnected, selected route, permission/capability gaps, battery only when exposed, and state age.
- Explain conflicts and unsupported functions; never claim remapping/device control unless implemented and permitted.
- Stable, non-sensitive device identity with minimal retention and user-controlled reset.

### Acceptance
- No duplicate discovery implementation from CAR-65/66.
- Correct add/remove/reconnect and stale-state recovery.
- Unsupported/partially exposed devices show explicit unknown/unavailable.
- Per-game association survives process restart and migration.
- Tests for hot-plug, reconnect, permission denial, service restart and missing metadata.
- CI/build/lint/release gates green; no actionable errors.

## CAR-94 — Peripheral Latency & Audio Codec Diagnostics Lab

### Objective
Provide evidence-based diagnostics for controller input and audio output, distinguishing what Android exposes from true end-to-end physical measurements.

### Reuses
CAR-62 Ultra Performance Lab; CAR-64 Diagnostic Support Bundle; CAR-65/66; CAR-87 Ultra HUD.

### Scope
- Show active audio output route and transport from public Android audio-device information.
- Display active codec, Bluetooth version, sample rate, bit depth and profile only when reliable runtime values are exposed; otherwise show "not exposed", never infer from product marketing specifications.
- Record controller input event timing where Android exposes timestamps; distinguish event delivery timing from true physical input latency.
- Repeatable tests and percentile summaries (p50/p95) only for valid samples.
- Separate software-observed latency, vendor-reported values and externally measured end-to-end latency.
- Optional physical protocol using high-speed camera, electrical/acoustic loopback or dedicated fixture; record device, firmware, route and method.
- Report audio route changes and disconnect indicators only where measurable.
- Sanitized export; no audio recordings, raw button/key content or personal identifiers by default.

### Acceptance
- No fabricated codec, polling rate or latency.
- Unknown fields explicit and tested.
- Method documents clock source, sample size, outlier handling and uncertainty.
- Tests for route changes, missing metadata, reconnects and invalid samples.
- Results reproducible and labelled by measurement class.
- CI green; physical certification separate from emulator tests.

## CAR-95 — Wireless Coexistence & Connection Health Intelligence

### Objective
Diagnose wireless instability across Wi-Fi, Bluetooth and optional 2.4 GHz dongles without claiming control over radio scheduling or unobservable interference.

### Reuses
CAR-51, CAR-66, CAR-72 and CAR-77.

### Scope
- Correlate timestamps of observed Wi-Fi changes, signal/transport changes, controller disconnects, audio route changes and app-visible stalls.
- Show Wi-Fi band/frequency only when Android exposes it; recommend 5/6 GHz only when phone, access point and network support it.
- Treat temporal correlation as a possible coexistence issue, not proof of RF interference.
- Compare Bluetooth with supported USB-C/2.4 GHz dongle modes when the peripheral provides them.
- Evidence-based suggestions: move closer to AP, try a supported less-congested band, test wired/dongle mode.
- Never change radio channels, transmit power, routing, QoS or coexistence settings without documented API and explicit authorization.
- Bound scan frequency, battery/data cost and permissions.
- Integrate with CAR-72 without duplicating ping/jitter/loss probes.

### Acceptance
- Correlation never labelled causation.
- No measured RF interference claim without a supported measurement source.
- Tests for Wi-Fi/mobile transitions, frequency metadata availability, BT disconnects, dongle attach/detach, VPN/captive portal and permission denial.
- Recommendations show evidence and confidence.
- CI green; physical RF validation separate.

## CAR-96 — Adaptive Gaming Peripheral Profiles & Session Readiness

### Objective
Coordinate existing game, adaptive, network and peripheral systems into user-controlled profiles for competitive, balanced, immersive/audio and portable use.

### Reuses
CAR-21, CAR-47, CAR-65/66, CAR-72, CAR-77 and CAR-93/94/95.

### Scope
- Profiles: Competitive, Balanced, Immersive/Audio, Portable/Battery Saver and user-defined variants.
- Include only settings GameHub Ultra can actually observe or apply; disable unsupported actions with explanation.
- Competitive readiness: controller/audio/network state, low-distraction UI and optional wired/dongle suggestions when supported.
- Immersive/Audio: preferred output and app-level settings only where APIs permit; never promise lossless codec if runtime negotiation is unavailable.
- Portable: battery-conscious suggestions based on measured battery/thermal state.
- Explainable readiness score with evidence, confidence and missing signals visible.
- Require user approval for meaningful changes unless reversible automation was explicitly enabled.
- Delegate confidence gates, hysteresis, cooldown, known-good rollback and conflict resolution to CAR-77; do not reimplement.
- Post-session report links observed peripheral/network events to timeline only when evidence supports it.

### Acceptance
- Reuse existing policy engines; no duplication of CAR-47/72/77.
- No unsupported codec, polling, latency, QoS or privileged-control claims.
- Deterministic tests for profile selection, conflicts, missing devices, stale metadata, overrides, rollback and battery/thermal constraints.
- UI tests for landscape/portrait, accessibility and empty/error states.
- CI/build/lint/release gates green; hardware-specific behavior pending physical validation.

## Suggested execution order
1. CAR-93 — inventory/orchestration foundation.
2. CAR-94 — measurement and diagnostics contracts.
3. CAR-95 — wireless correlation and connection health.
4. CAR-96 — profile orchestration after those contracts stabilize.

Do not mark any CAR complete solely because code compiles. Require tests, reviewed logs, green CI and separate physical evidence for hardware claims.
