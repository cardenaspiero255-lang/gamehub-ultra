# Build GameHub Ultra

## Requirements

- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Gradle 8.9 or a compatible Gradle 8.x version

## Debug build

From the repository root:

    gradle :app:assembleDebug

The GitHub Actions workflow performs the same debug build on Ubuntu with JDK 17 and Android SDK 35.

## Android Studio

Open the repository root as an existing Gradle project. Let Android Studio sync the project, then run the app configuration.

## Current scope

The initial milestone provides the independent Android app structure, Compose dashboard, performance profile model, and safe hardware/capability detection. It does not bypass Android security or modify unsupported system/game internals.
