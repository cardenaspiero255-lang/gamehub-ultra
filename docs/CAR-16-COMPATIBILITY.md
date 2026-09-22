# CAR-16 Compatibility Matrix

GameHub Ultra uses public Android APIs and degrades when a capability is absent.

| Capability | API | Behavior when available | Fallback |
|---|---:|---|---|
| Thermal status | 29+ | Shows thermal status | Unavailable |
| Thermal headroom | 30+ | Shows headroom | Unavailable |
| Display refresh modes | 23+ | Shows current/supported Hz | Current display fallback |
| External audio devices | 23+ | Counts exposed external outputs | Not available |
| Gamepad/keyboard/mouse | 1+ | Counts exposed physical input devices | Count remains zero |
| Sustained Performance | 24+ + device support | Requests only through existing controller | Profile remains informative |
| Performance Hint | 31+ | Detects API presence | No hint behavior |
| Quick Settings | 24+ | User can add safe GameHub launcher tile | App launcher remains available |
| Gemini Nano Prompt API | Device-dependent | Local advisory model | Deterministic offline advisor |
| Baseline Profiles | Release build | ART can precompile profiled paths | Normal compilation |
