# Build GameHub Ultra

## Requirements

- JDK 17
- Android SDK Platforms 35 and 36
- Android Build Tools 36.0.0
- Gradle 8.11.1

## Debug build

From the repository root:

    gradle :app:assembleDebug

The GitHub Actions workflow uses JDK 17, Android SDK Platforms 35/36, Build Tools 36.0.0, Gradle 8.11.1, release APK/AAB builds, and an API 35 connected benchmark/baseline-profile validation.

## Android Studio

Open the repository root as an existing Gradle project. Let Android Studio sync the project, then run the app configuration.

## Current scope

The initial milestone provides the independent Android app structure, Compose dashboard, performance profile model, and safe hardware/capability detection. It does not bypass Android security or modify unsupported system/game internals.
