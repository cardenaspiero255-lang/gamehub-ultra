# GameHub Ultra – Copilot instructions

## Project goal
Build an independent Android app called GameHub Ultra. It must be installable alongside the original GameHub app.

## Rules
- Inspect the existing repository before changing files.
- Keep the applicationId/package distinct from the original GameHub app.
- Prefer Kotlin, AndroidX, and modern Android APIs.
- Do not claim that privileged gaming optimizations are implemented unless the required Android permissions/API are actually available.
- Keep UI, domain logic, and platform integrations separated.
- Add tests for non-UI logic where practical.
- Keep changes small and buildable.
- Document assumptions and limitations.

## Initial product requirements
- Performance profiles: FPS balanceado, priorizar interpolación de frames, and X4.
- Hardware information screen using safe Android APIs.
- Clear, gaming-oriented interface.
- No root requirement for the baseline app.
- Preserve compatibility with standard Android devices.

## Workflow
1. Read the relevant Linear issue.
2. Inspect the current implementation.
3. Implement the smallest complete change.
4. Run the available Gradle checks.
5. Report changed files, tests, limitations, and next steps.
