# GameHub Ultra architecture

## Layers

- UI: Jetpack Compose screens and user interactions.
- Domain: performance profiles and state transitions independent from Android APIs.
- Platform: Android-only device and capability detection.
- Future integrations: optional APIs isolated behind interfaces and capability checks.

## Important limitation

GameHub Ultra must not claim to change GPU clocks, system thermal limits, frame generation, game internals, or other privileged behavior unless Android/OEM APIs actually expose that capability and the implementation has the required permissions.

The X4 and interpolation profiles are currently product-level modes. They are not a claim that arbitrary games can be forced to generate four frames per rendered frame.

## Package identity

Application ID: com.cardenaspiero255.gamehubultra

This is intentionally independent from the original GameHub package so both apps can be installed together.
