# CAR-15 — GameHub AI

## Scope

CAR-15 adds a local-first advisory boundary on top of the existing GameHub Ultra performance and voice layers.

## Architecture

- GameHubAiContext carries structured device, thermal, battery, refresh-rate, network, storage, peripheral, profile, and session signals.
- LocalAiModelAdapter is a provider boundary for compatible on-device generation. The production Android implementation uses the ML Kit Prompt API / Gemini Nano when the feature reports AVAILABLE; unsupported or unavailable devices use the deterministic fallback. No remote endpoint or API key is required by the core advisor.
- GameHubAiAdvisor first accepts only allowlisted model actions and otherwise falls back to a deterministic offline policy.
- AiActionAllowlist explicitly permits only profile selection, installed-game targeting, device status, help, and advisory actions. Shell commands, arbitrary URLs, and generic intents are rejected.
- Voice advice is represented by VoiceCommand.AskAi; it never bypasses VoiceCommandEngine.
- The UI exposes the advisor and clearly states whether a local model is available or the deterministic offline fallback is active.

## Safety boundary

The model layer cannot directly launch a process, execute shell, open a URL, or construct an arbitrary Android intent. Game launching remains the existing installed-package flow, and profile changes remain the existing PerformanceController / preference flow.

## Degradation

When no compatible local model adapter is installed or available, the advisor continues to work offline using structured telemetry and deterministic rules. This state is surfaced to the user rather than presented as a generative model response.

## Validation

CAR-15 tests cover:

- Spanish and English advice intent routing.
- Ambiguous/unsupported actions.
- Invalid model outputs falling back safely.
- Allowlisted profile actions.
- Rejection of shell, URL, and generic-intent actions.
- Voice integration that returns advice without invoking unrelated game/profile actions.
