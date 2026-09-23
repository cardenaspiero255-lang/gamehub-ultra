# CAR-16 — Gaming Experience & Release

CAR-16 adds the final gaming-experience and release-hardening layer.

## Included

- Quick Settings Tile that opens GameHub Ultra from the system panel.
- Runtime detection of physical gamepads/joysticks, keyboards, mice and external audio outputs.
- Accessibility-friendly labels and descriptions for the Quick Settings entry.
- Baseline Profile integration remains enabled through the existing baseline-profile module.
- Release build remains minified/shrunk and uses optimized Android ProGuard defaults.
- Compatibility is documented around public Android APIs; unsupported privileged controls are never claimed.
- The app keeps landscape launch behavior and the red/black visual system.

## Compatibility matrix

| Capability | API / source | Fallback |
| --- | --- | --- |
| Quick Settings | Android 7.0+ | Launch from the app launcher |
| Gamepad / joystick | Android InputDevice | Report zero when none is exposed |
| Keyboard / mouse | Android InputDevice | Report zero when none is exposed |
| External audio | Android AudioDeviceInfo | Report disconnected when no external output is exposed |
| Baseline Profile | AndroidX ProfileInstaller | Standard runtime when profile is unavailable |
| Release shrinking | Android Gradle Plugin | Debug build remains available for validation |

CAR-16 does not require root, accessibility-service privileges, or hidden OEM APIs.

## Validation gate

CAR-16 is considered complete only when:
1. Code is integrated on main.
2. JVM tests pass.
3. Debug APK compiles.
4. Lint passes.
5. Release configuration remains buildable.
6. No unsupported Android capability is presented as guaranteed.

The APK/AAB release artifact is deliverable only after the CI gate reports success.
