# CAR-16 Compatibility Matrix

The application minimum supported Android version is **API 26** (`minSdk 26`). Individual platform APIs may have lower introduction levels, but GameHub Ultra only promises compatibility from API 26 upward.

| Capability | API / condition | Behavior when available | Fallback |
|---|---:|---|---|
| Thermal status | 29+ | Shows thermal state | Unavailable |
| Thermal headroom | 30+ | Shows thermal envelope usage | Unavailable |
| Display refresh modes | 23+ | Shows current/supported Hz | Current display fallback |
| Gamepad / keyboard / mouse | API 26 app floor + public InputDevice APIs | Reports exposed physical devices | Count remains zero |
| External audio | 23+ | Counts only explicit external output routes | Not available |
| Sustained Performance | 24+ + device support | Uses existing controller only when supported | Informative profile |
| Performance Hint | 31+ | Detects public API presence | No hint behavior |
| Quick Settings | 24+ | User-added GameHub launcher tile | App launcher remains available |
| Local Gemini Nano Prompt API | Device-dependent | Optional local advisor | Deterministic offline advisor |
| Baseline Profiles | Release build | Optimizes profiled paths | Normal compilation |

## Release validation

CI validates unit tests, debug APK, debug lint, release APK, and release AAB. The release outputs are build-validated binaries; they are **not distributable signed artifacts** unless a release signing configuration is provided by the distribution environment.

Unsigned release outputs are never treated as store-ready. A release-signing pipeline must supply protected credentials before publishing to any store.

## Performance validation

The baseline-profile module contains a startup and critical-navigation profile journey. CI also runs on an API 35 emulator for startup/navigation/library macrobenchmark checks so performance instrumentation is exercised automatically.

Actual latency/frame timings remain device- and emulator-dependent; CI records the benchmark output but does not claim that an emulated result represents every physical device.
