# GameHub Ultra

Independent Android gaming companion project designed to coexist with the original GameHub app.

## Development coordination

- **Linear:** planning and task tracking — [GameHub Ultra project](https://linear.app/gamehubautooptimizer/project/gamehub-ultra-ddec8420cfb0b)
- **GitHub:** source control and review.
- **GitHub Copilot:** implementation assistance using `.github/copilot-instructions.md` and the linked Linear tasks.

## Current capabilities

- Gaming-oriented Compose interface.
- Performance profiles: FPS balanceado, priorizar interpolación de frames, and X4.
- Safe Android hardware information.
- Independent application ID so it can be installed beside the original GameHub.
- Smart game library with persistent selection and safe launching.
- Battery and thermal telemetry.
- Optional bilingual voice assistant with deterministic, allowlisted actions.
- Local-first AI advisor with structured telemetry context, deterministic offline fallback, and a strict action allowlist.
- Clear documentation of Android limitations and permissions.

## Planned expansion

- Android 16/API 36 migration.
- Core architecture 2.0 and per-game profiles.
- Adaptive thermal/performance engine.
- Gaming readiness, refresh-rate and connectivity diagnostics.
- Release hardening and final validation.
- Shortcuts, Quick Settings and session history.
- Baseline Profile, benchmarks and release hardening.

## Engineering policy

GameHub Ultra reports and applies only capabilities that Android or a supported vendor API actually exposes. It does not claim universal CPU/GPU overclocking, arbitrary Game Mode switching, X4 frame generation injection into other games, or other privileged controls that the platform does not provide.

## Detailed plan

See [docs/CAR-6-TECHNICAL-AUDIT.md](docs/CAR-6-TECHNICAL-AUDIT.md) and [docs/CAR-15-GAMEHUB-AI.md](docs/CAR-15-GAMEHUB-AI.md).
## CAR-17..19 — Accounts, compact UI, library search

### CAR-17 — Steam/Epic account hub
GameHub Ultra can open the official Steam and Epic sign-in pages and locally keep multiple public account entries. Passwords, Steam Guard codes, and provider tokens are never stored.

### CAR-18 — Compact visual polish
The red/black Material 3 theme uses reduced typography, spacing, and card padding. The duplicate profile block was removed from Inicio while keeping the three performance presets available.

### CAR-19 — Library search
The Biblioteca tab now filters games by visible name or package name without re-querying Android's PackageManager. Search misses display a dedicated empty-state message.

These CARs are considered complete only after the Android workflow passes JVM tests, debug APK compilation, and lint on the updated branch.
