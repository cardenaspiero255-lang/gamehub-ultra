# GameHub Ultra

Independent Android gaming companion project designed to coexist with the original GameHub app.

## Development coordination

- **Linear:** planning and task tracking — [GameHub Ultra project](https://linear.app/gamehubautooptimizer/project/gamehub-ultra-ddec8420cfb0b)
- **GitHub:** source control and review.
- **GitHub Copilot:** implementation assistance using `.github/copilot-instructions.md` and the linked Linear tasks.

## Planned capabilities

- Gaming-oriented interface.
- Performance profiles: FPS balanceado, priorizar interpolación de frames, and X4.
- Safe Android hardware information.
- Independent application ID so it can be installed beside the original GameHub.
- Clear documentation of Android limitations and permissions.

## CAR-8 — automatic hardware detection

The current implementation detects, using public Android/platform APIs:

- Manufacturer, model, Android release and API level.
- Supported ABIs and logical CPU cores.
- CPU/SoC model when `Build.SOC_MODEL` is available, with a safe `/proc/cpuinfo` fallback.
- Total device RAM through `ActivityManager.MemoryInfo`.
- GPU vendor and renderer through a temporary OpenGL ES 2.0 EGL context; failure to create/query the context is reported as unavailable rather than guessed.
- Sustained Performance Mode support.
- Thermal API availability and current thermal status.
- Performance Hint API service availability on Android 12/API 31+.

GameHub Ultra does not claim to control CPU/GPU frequencies, force frame interpolation inside another game, or change another app's performance mode without a supported Android/vendor API and required privileges.

## Status

Features are only considered complete after implementation and build validation. CAR-8 is being validated with unit tests, debug build, and lint in GitHub Actions.
