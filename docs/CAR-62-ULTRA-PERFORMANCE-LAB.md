# CAR-62 — Ultra Performance Lab

## Real implementation and scientific limitations
- A short repeatable local **CPU integer workload, version CPU_INTEGER_V1**, runs 2 untimed warmups and 7 timed repetitions on a background dispatcher.
- Each run records its entire comparison context: device model, Android SDK, CPU core count, application version, scenario version, number of workload iterations, selected profile **label**, monotonic sample durations, and capture time.
- The scenario and workload are bounded (1,000–1,000,000 iterations; production UI uses 250,000). Tests inject clock/workload to validate repeatability without flaky physical performance assertions.
- **Statistical quality:** runs with fewer than 5 positive samples are insufficient; zero/negative durations invalid; sample range greater than 30% of median is unstable. No comparison can claim a gain from weak samples.
- **Before/after:** compare latest profile-labelled run to latest prior run on another profile **only when all context fields match**. Report a directional synthetic performance difference only if both sample ranges are disjoint and stable. Overlapping samples are inconclusive. Changed device/Android version/app version/scenario/workload/CPU cores invalidate comparison.
- Selecting a GameHub profile is not proof of an OS optimization, and a synthetic CPU measurement says nothing conclusive about GPU, thermal limits, gaming frame rate, network latency, game engine or actual Game FPS. Every result prominently states **not game FPS**.
- Reports are stored locally as a versioned JSON SharedPreferences record with a strict 12-run retention cap. Corrupted data and invalid profile/sample values fail closed; users can clear the history. No user accounts, credentials, game package names or private library items are collected.

## CAR-29 verification
- JVM test coverage: deterministic warmups, sample validity, noisy/short samples, incompatible contexts, overlapping/confident comparisons, privacy of labels.
- Robolectric: persistent scenario context, bounded history, malformed snapshot recovery, invalid data rejection and clearing.
- Compose UI: real-run trigger with injected deterministic clock, median display, no FPS claims and clear action.
- Android Build, all shards, JaCoCo executable added patch lines >=90%, emulator validation, independent automated reviewers and security gates mandatory.

## Dependencies and release
- CAR-58 #259, CAR-59 #260, CAR-60 #261 and CAR-61 #262 remain stacked pending their own real release gates. This CAR-62 branch starts at CAR-61 SHA `763e9e48524b8ef1501960ff915254fa7c214c96`.
- Because CI workflows presently trigger only on PRs targeting `main`, a draft comparison to main intentionally includes predecessor commits. That is for validation only, **never authorization to merge the stack**.
- Epic OAuth and entitlement configuration remain owner-controlled and incomplete. A separate reminder for CAR-69 exists, but it is not a waiver to claim earlier integrations functional. Do not merge stacked PRs until their dependencies are resolved, diffs isolated/rebased, and CI re-run on final exact SHA.
- The user-requested physical testing campaign is deferred through CAR-69.
