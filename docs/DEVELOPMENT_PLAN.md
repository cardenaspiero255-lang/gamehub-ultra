# Development plan

## Phase 1 — Foundation
- [ ] Android project structure
- [ ] Independent application ID
- [ ] Stable Gradle and SDK configuration
- [ ] Basic launcher activity

## Phase 2 — GameHub Ultra UI
- [ ] Dashboard
- [ ] Performance profile selector
- [ ] Hardware information
- [ ] Settings and limitations screen

## Phase 3 — Safe integrations
- [ ] Battery and thermal information where Android exposes it
- [ ] Device capability detection
- [ ] Per-game profile model
- [ ] Validation and tests

## Performance profile definitions

### FPS balanceado
A balanced configuration intent that prioritizes a reasonable tradeoff between responsiveness, battery, and temperature.

### Priorizar interpolación de frames
A configuration intent that prioritizes frame-interpolation-related options when the target device/API supports them. The app must not claim to force interpolation if Android or the game does not expose a supported mechanism.

### X4
A high-performance profile label. It must be implemented as a real, documented configuration only where supported; otherwise it remains an explicit UI preset without pretending to modify unsupported system behavior.
