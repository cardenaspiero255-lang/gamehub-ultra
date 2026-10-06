# GameHub Ultra — SSS Expansion Roadmap

This document is an **additive overlay** for `docs/CAR-31-70-ULTRA-NEXT-GEN.md`.

It does **not** replace, rename, remove, or weaken the original scope of any CAR. Every existing CAR keeps its original acceptance criteria. The SSS additions below are extra capabilities and extra quality gates.

Historical note:
- CAR-70, CAR-71, CAR-72, CAR-73 and CAR-74 already exist in the project history and must not be reused for unrelated new work.
- New SSS megacapabilities therefore start at **CAR-75**.
- CAR-71 remains the existing conversational-memory foundation, but **must not be treated as complete merely because its number already exists**. Its canonical acceptance criteria remain authoritative until every required history/thread/search/filter/export/import/privacy/offline item and applicable gate is actually implemented and verified.
- Any CAR-81 Memory 4.0 work must preserve CAR-71 scope and treat still-missing CAR-71 requirements as prerequisites or explicit carried-forward work; CAR-81 may extend them, not silently skip them.
- CAR-72 remains the existing network/game-booster foundation.
- CAR-73 remains the existing general-assistant/research foundation.
- CAR-74 remains the existing verified-query execution hardening foundation.

## Global SSS rules

These rules apply to every SSS addition and to every new CAR in this document.

1. **Preserve original CAR scope.** SSS work is additive; it cannot delete an original feature merely to simplify implementation.
2. **TDD for behavior changes.** Reproduce defects and specify new behavior before implementation.
3. **No fake capabilities.** Never claim FPS, thermal data, router control, frame generation, privileged Android control, local-model availability, source authority, or confidence that cannot actually be measured or verified.
4. **Fail safely.** Unsupported actions must be refused or downgraded to the safest available fallback.
5. **Bound latency.** Every online/provider/model route must share explicit deadlines, cancellation and fallback rules.
6. **Evidence before confidence.** Confidence must come from source quality, relevance, freshness, independence and agreement—not from arbitrary percentages.
7. **No single-provider lock-in.** Stable knowledge, current data and specialist research require provider abstractions and graceful fallbacks.
8. **Freshness-aware caching.** Stable definitions may use long-lived caches; volatile data such as prices, news, weather, releases and security data must expire quickly.
9. **Privacy by design.** Credentials, tokens, secrets and sensitive account material must never be stored as AI memory or diagnostic text.
10. **Prompt/tool security.** External content is untrusted input and cannot directly authorize actions.
11. **Reversible actions.** Significant automated changes require rollback metadata where technically possible.
12. **Measured benchmarking.** “SSS”, “better”, “faster” or “more accurate” claims must be backed by reproducible benchmark results.
13. **Full gates before completion.** A CAR is not complete while confirmed errors, failing tests, unresolved reviews or release-blocking warnings remain.
14. **Spanish-first UX.** Ultra should answer naturally in Spanish by default while preserving multilingual understanding.
15. **Structure before scale.** Provider registries, reasoning stages, memory, tools and UI must remain modular; do not grow one giant router/service file.

# SSS additions to existing CAR-44..69

## CAR-44 — Explainable Game Recommendations + SSS Evidence

Keep all original CAR-44 requirements and add:

- Claim-level provenance for every important recommendation.
- Separate **measured**, **inferred**, **remembered** and **externally researched** evidence.
- Calibrated confidence bands instead of decorative certainty percentages.
- Explicit contradiction flags when telemetry/history disagree.
- Explain why a recommendation changed after new evidence.
- Explain why a recommendation was *not* made.
- Confidence must fall when evidence is stale, incomplete or provider agreement is weak.
- Concise gaming-mode explanation and expanded “why?” explanation must use the same underlying evidence object.

## CAR-45 — AI Session Coach + SSS Proactive Intelligence

Keep all original CAR-45 requirements and add:

- Event-significance scoring so Ultra speaks only when a change matters.
- Detect persistent thermal, latency, refresh, memory and battery trends.
- Proactive warnings before a likely problem when evidence is strong enough.
- Cooldowns and duplicate suppression for coach messages.
- Session goals such as stability, battery endurance or responsiveness.
- Track whether the user followed a suggestion and whether the measured outcome improved.
- Never present a prediction as a sensor measurement.

## CAR-46 — AI Profile Builder + SSS Planning/Rollback

Keep all original CAR-46 requirements and add:

- Build profiles from an explicit plan: objective → constraints → proposal → validation → apply.
- Dry-run validation before applying high-impact changes.
- Versioned profile history and known-good checkpoints.
- Atomic rollback metadata where settings permit rollback.
- Per-setting capability checks instead of all-or-nothing profile support.
- Compare expected benefit, risk and evidence before apply.
- Refuse unsupported/privileged settings rather than silently pretending they changed.

## CAR-47 — Per-game Adaptive Optimizer 2.0 + SSS Closed Loop

Keep all original CAR-47 requirements and add:

- Closed-loop optimization using measured outcomes.
- Multi-signal policy: thermal + battery + memory + refresh + latency + historical feedback.
- Hysteresis, cooldown and minimum-observation windows before switching.
- Automatic rollback when a newly applied profile creates a confirmed regression.
- Separate policies per game version, device fingerprint and relevant backend/driver context.
- Persist the reason for every automatic decision.
- Safety policy always overrides preference learning.

## CAR-48 — Thermal Prediction + SSS Forecasting

Keep all original CAR-48 requirements and add:

- Trend windows rather than single-sample decisions.
- Time-to-risk estimates only when enough samples exist.
- Confidence/range attached to thermal forecasts.
- Detect charger-related and sustained-load heat patterns when measurable.
- Per-device calibration from local history.
- Prediction invalidation when workload/profile changes substantially.
- Never invent a throttle temperature that Android does not expose.

## CAR-49 — Battery-aware Gaming Engine + SSS Energy Intelligence

Keep all original CAR-49 requirements and add:

- Session drain-rate trends and remaining-session estimates.
- Separate charging, fast-charging and unplugged behavior where Android exposes enough evidence.
- Identify heat + charging combinations that make aggressive profiles unsafe.
- Energy budget target per gaming session.
- Compare performance profile benefit against measured drain cost.
- Learn user preference for endurance versus performance without overriding safety.

## CAR-50 — Frame Pacing & Refresh Intelligence + SSS Smoothness Model

Keep all original CAR-50 requirements and add:

- Distinguish display refresh, observed frame data and interpolation claims.
- Frame-time/jank analysis where legitimate APIs expose usable data.
- Stability score based on variance, not only average FPS.
- Recommend refresh/profile combinations using actual capability evidence.
- Detect mismatch between selected profile and current display mode.
- Never call frame generation/interpolation active without verifiable proof.

## CAR-51 — Network Gaming Diagnostics 2.0 + SSS Network Intelligence

Keep all original CAR-51 requirements and add:

- Jitter, latency distribution and spike detection.
- Packet-loss diagnostics only from real measurable probes.
- Optional bounded bufferbloat-style diagnostics where safe and practical.
- Distinguish local Wi-Fi quality, internet-path symptoms and remote-server uncertainty.
- Track network behavior per session without claiming control over ISP/router infrastructure.
- Router integration remains capability-gated and authorized; no fake “gaming QoS enabled” state.
- Learn which local network profile performed better from measured outcomes.

## CAR-52 — Memory & Storage Pressure Intelligence + SSS Resource Forecast

Keep all original CAR-52 requirements and add:

- Trend resource pressure across a session.
- Detect repeated low-memory or storage-headroom patterns.
- Predict near-term risk only from real observations.
- Adaptive cache retention based on storage headroom.
- Distinguish Android-managed reclamation from app-controlled cleanup.
- Never advertise unsupported “RAM boosting” or process killing as guaranteed performance improvement.

## CAR-53 — GPU/Driver Capability Matrix + SSS Capability Graph

Keep all original CAR-53 requirements and add:

- Versioned capability graph keyed by GPU family, renderer, API level and relevant backend.
- Provenance for every compatibility rule.
- Separate **observed**, **vendor-documented**, **locally benchmarked** and **unknown** capability states.
- Known-good/known-bad driver observations must include context and version.
- No global conclusion from one device/session.

## CAR-54 — Turnip/Snapdragon Compatibility Layer + SSS Driver Safety

Keep all original CAR-54 requirements and add:

- Validate driver availability before launch.
- Keep system-driver fallback ready.
- Store per-game driver outcomes and regressions.
- Reject stale driver references after app/device/backend changes.
- Roll back automatically when a validated driver selection causes a confirmed launch/graphics regression where the platform permits it.
- Never imply Turnip support outside supported environments.

## CAR-55 — Vendor Adapter Framework + SSS Isolation

Keep all original CAR-55 requirements and add:

- Strict adapter interface for Qualcomm, MediaTek, Exynos and future vendors.
- Capability discovery before vendor calls.
- Timeout, exception isolation and circuit-breaker behavior per adapter.
- Generic Android path remains fully functional if every vendor adapter fails.
- Vendor-specific data cannot bypass safety or evidence rules.

## CAR-56 — Device Compatibility Database + SSS Local Knowledge

Keep all original CAR-56 requirements and add:

- Versioned compatibility schema with migrations.
- Confidence based on repeated local observations rather than device-name assumptions.
- Known-good profiles tied to game version/context.
- Export/import only non-sensitive compatibility knowledge.
- Rules expire or downgrade confidence when major app/device/backend versions change.

## CAR-57 — Steam Multi-account 2.0 + SSS Account-aware Intelligence

Keep all original CAR-57 requirements and add:

- AI/recommendation layer receives only the minimum account/library metadata required.
- No credentials/tokens enter conversation memory.
- Per-account library/recommendation scope.
- Clear provenance showing which account/library generated a recommendation.
- Offline cache invalidation after disconnect.

## CAR-58 — Epic Integration 2.0 + SSS Account-aware Intelligence

Keep all original CAR-58 requirements and add:

- Same privacy isolation and provenance guarantees as CAR-57.
- Connection-health state exposed to Ultra without exposing credentials.
- Safe fallback when the provider is unavailable.
- Avoid recommendations based on stale ownership state after disconnect.

## CAR-59 — Unified Cross-store Library + SSS Semantic Discovery

Keep all original CAR-59 requirements and add:

- Semantic search over already-known local game metadata where supported.
- Recommendation explanations based on actual genres/tags/history, not fabricated metadata.
- Duplicate-resolution provenance across stores.
- Per-user feedback on recommendations.
- Offline discovery over cached library data.

## CAR-60 — Account Privacy & Recovery + SSS AI Privacy

Keep all original CAR-60 requirements and add:

- Central secret-redaction boundary shared by logs, diagnostics, AI context and exports.
- Detect common token/key/JWT/credential patterns before persistence.
- Memory and research traces must not retain raw auth headers.
- Account disconnect clears account-scoped AI context and cached private metadata.
- Recovery workflows must not reveal secret values in error messages.

## CAR-61 — Session Replay Timeline + SSS Causal Analysis

Keep all original CAR-61 requirements and add:

- Correlate profile/network/thermal/battery/refresh changes by timestamp.
- Distinguish correlation from causation in explanations.
- Surface “what changed immediately before the regression”.
- Compare against known-good session baselines.
- Allow Ultra to answer questions about a completed session from sanitized timeline evidence.

## CAR-62 — Ultra Performance Lab + SSS Reproducibility

Keep all original CAR-62 requirements and add:

- Repeated-run statistics instead of single-run conclusions.
- Warm-up/outlier handling.
- Device temperature/battery/network context recorded with benchmark results.
- Confidence intervals or clearly stated sample weakness.
- Before/after experiments must use comparable conditions.
- Export reproducible benchmark summaries for CAR-85 evaluation.

## CAR-63 — Regression Detector + SSS Anomaly Intelligence

Keep all original CAR-63 requirements and add:

- Statistical or threshold-based anomaly detection with explainable evidence.
- Rank likely causes without claiming certainty.
- Separate app-version regressions from environment/network/device uncertainty.
- False-positive dismissal becomes feedback to future detection.
- Link regression to relevant commit/app version/session baseline where metadata exists.

## CAR-64 — Diagnostic Support Bundle + SSS Self-diagnosis Foundation

Keep all original CAR-64 requirements and add:

- Correlate sanitized Sentry/crash identifiers with app version and recent runtime state where available.
- Generate a structured probable-cause report, not an unsupported definitive diagnosis.
- Include relevant regression-test identifiers and failing gate metadata.
- Ultra may propose debugging next steps, but cannot silently modify/ship code from diagnostics alone.

## CAR-65 — Controller Intelligence + SSS Input Context

Keep all original CAR-65 requirements and add:

- Per-game controller capability/history metadata.
- Detect meaningful connect/disconnect or capability changes.
- Ultra can explain detected controller state using real Android input data.
- No fabricated remapping support outside exposed APIs.

## CAR-66 — Gaming Peripheral Hub + SSS Readiness Model

Keep all original CAR-66 requirements and add:

- Peripheral readiness score derived from observed connection/capability state.
- Detect mid-session peripheral changes.
- Explain missing capability without blocking unrelated gaming functionality.
- Per-game remembered peripheral preferences remain local and non-sensitive.

## CAR-67 — Quick Actions 2.0 + SSS Agent-safe Actions

Keep all original CAR-67 requirements and add:

- Every Quick Action is represented as a typed, allow-listed action.
- Agent/voice requests resolve to the same typed action contract.
- Validate parameters and target package/deep link before execution.
- High-impact/destructive actions require explicit confirmation where appropriate.
- Action execution returns structured success/failure evidence to Ultra.

## CAR-68 — Security Hardening 2.0 + SSS AI/Tool Security

Keep all original CAR-68 requirements and add:

- Prompt-injection resistance for researched/web/provider text.
- External text can provide evidence but cannot authorize Android/tool actions.
- Strict tool allow-list and schema validation.
- Model/provider output treated as untrusted until validated.
- Secret and PII redaction tests.
- Dependency/provider trust review.
- Abuse tests for malicious URLs, malformed JSON, oversized responses and poisoned research text.

## CAR-69 — Recovery, Migration & Data Integrity 2.0 + SSS AI State Recovery

Keep all original CAR-69 requirements and add:

- Versioned migrations for AI memory, research cache, capability DB and benchmark data.
- Corruption recovery per store so one damaged subsystem does not wipe unrelated state.
- Safe rollback after interrupted updates.
- Rebuildable semantic indexes/caches from canonical data.
- Migration tests for CAR-71 memory and all later SSS schemas.

# Existing post-70 foundations — do not renumber or replace

## CAR-70 — Existing final-experience milestone

Already part of project history. Future SSS work may strengthen its guarantees but must not reuse the CAR number.

## CAR-71 — Existing Conversational AI & Long-Term Memory foundation

Use as the foundation for CAR-81. Preserve its existing multi-turn, memory, privacy and voice work.

## CAR-72 — Existing Network Game Booster foundation

Use as the foundation for CAR-51 SSS extensions and future network-agent work.

## CAR-73 — Existing General Assistant / Verified Research foundation

Use as the foundation for CAR-75, CAR-76 and CAR-77.

## CAR-74 — Existing Ultra Query Execution hardening

CAR-74 already hardened query lifecycle, freshness policy and the Ultra query execution boundary. New work must build on it rather than create a second competing execution pipeline.

# New SSS megacapability CARs

## CAR-75 — Ultra Reasoning Core SSS

Goal: introduce a testable reasoning layer above CAR-73/CAR-74 without turning Ultra into one monolithic prompt.

Required architecture:

- **Intent/Task Analyzer** — classify task type, freshness, risk and required tools.
- **Planner** — decompose multi-step questions/actions into bounded steps.
- **Resolver** — execute deterministic/local/research/model steps.
- **Verifier** — inspect evidence and contradictions before final answer.
- **Recovery loop** — retry with a different route only when the failure is recoverable.
- **Finalizer** — produce concise Spanish answer with evidence/confidence metadata.

Acceptance requirements:

- Simple math and deterministic commands still bypass expensive reasoning.
- Stable easy questions do not incur unnecessary multi-provider/model latency.
- Multi-step problems can explicitly plan and verify.
- A verifier can reject the resolver output and request one bounded correction attempt.
- Reasoning stages have cancellation and shared deadline budgets.
- No hidden “confidence” can override hard safety/capability failures.
- Unit tests cover decomposition, contradiction, timeout, cancellation and recovery.
- Benchmark against the pre-CAR-75 path for latency and answer quality.

## CAR-76 — Ultra Research Mesh Orchestrator SSS

Goal: turn specialist providers into a structured research mesh instead of a growing chain of `if` statements.

Architecture:

- Provider registry with domain, authority, freshness, latency and key-requirement metadata.
- Domain router chooses only relevant specialists.
- Parallel provider calls only when they add independent value.
- Shared route deadline and per-provider timeout.
- Deduplication by source/domain/fact.
- Circuit breaker for temporarily failing providers.
- Provider health metrics without leaking secrets.
- Fallback to CAR-73/CAR-74 verified general research.

Current/known specialist families to preserve and normalize include general encyclopedic sources and the existing scientific/security/economic/book/earth/exoplanet/chemistry/biology/trials specialists already added to Ultra.

Candidate **keyless/public** specialists may be added only after testing their terms, reliability, rate limits and response semantics. Candidate domains include:

- astronomy/space;
- chemistry/compounds;
- proteins/genetics;
- clinical trials;
- biomedical literature;
- academic papers/DOIs;
- cybersecurity vulnerabilities;
- earthquakes/geology;
- biodiversity/taxonomy;
- books/bibliographic metadata;
- macroeconomic/public statistics;
- weather/geographic public data;
- general encyclopedic/Wikidata-style entity resolution.

Acceptance requirements:

- No provider can steal an unrelated query due to fuzzy token similarity.
- Single-provider failure falls through cleanly.
- Specialist calls stay inside the shared latency budget.
- Stable definition queries cannot be replaced by arbitrary “latest event” specialist results.
- Provider-specific parsing has regression fixtures.
- Research answer records which independent domains agreed.

## CAR-77 — Evidence, Confidence & Contradiction Engine SSS

Goal: centralize trust decisions for every research/recommendation result.

Inputs:

- source authority;
- source freshness;
- query relevance;
- independent-domain count;
- direct versus inferred evidence;
- numeric/entity agreement;
- provider health;
- known conflicts.

Outputs:

- claim-level confidence band;
- evidence provenance;
- contradiction state;
- freshness state;
- decision: answer / answer-with-warning / verify-more / abstain.

Requirements:

- Do not count multiple URLs from the same upstream domain as independent confirmation.
- Numeric claims require numeric conflict checks.
- Current data must include freshness metadata.
- Contradictions trigger a bounded tie-break path rather than silent averaging.
- Ultra can say “las fuentes discrepan” when that is the truthful result.
- Confidence logic is deterministic and unit-tested.

## CAR-78 — Ultra Multimodal Intelligence SSS

Goal: let Ultra reason over supported visual/audio/document inputs while preserving the same evidence and safety rules.

Phases:

- screenshots and images;
- charts/graphs;
- app/game UI states;
- documents/log excerpts;
- audio/transcript context;
- video/screen-sequence support only if a reliable runtime pipeline exists.

Requirements:

- Visual analysis cannot execute actions by itself.
- UI-action requests pass through CAR-79 typed action validation.
- OCR/text extraction is evidence with provenance, not guaranteed truth.
- Detect uncertainty when text/objects are unreadable.
- Do not fabricate hidden UI state from a screenshot.
- Regression suite includes misleading/partial screenshots.

## CAR-79 — Ultra Android Agent & Capability Planner SSS

Goal: safely execute supported Android/game actions through one capability-gated agent layer.

Architecture:

- typed action catalog;
- capability discovery;
- planner;
- parameter validator;
- risk classifier;
- confirmation policy;
- executor;
- result verifier;
- rollback handler.

Actions may include only capabilities legitimately available to the app/device, such as supported profile changes, game launch, local diagnostics, GameHub settings and authorized integrations.

Optional privileged bridges such as Shizuku must be isolated behind an explicit adapter and must:

- require user-installed/user-authorized capability;
- expose only allow-listed operations;
- fail safely when permission disappears;
- never be required for core GameHub functionality;
- never execute arbitrary shell text generated by an AI/model;
- record reversible state where possible.

## CAR-80 — Ultra Local AI & Offline Tiering SSS

Goal: reduce cloud dependence and latency using capability-aware local models/components.

Local tiers may cover:

- intent classification;
- language detection;
- command parsing;
- embeddings/retrieval;
- summarization/compaction;
- lightweight response generation when device capability permits.

Requirements:

- Capability benchmark before enabling a heavy local tier.
- Memory/RAM/thermal safeguards.
- Fallback to deterministic implementation on weak devices.
- Model/version provenance.
- Offline status visible and truthful.
- No “runs locally” claim when execution actually uses a remote provider.

## CAR-81 — Ultra Context & Memory Intelligence 4.0

Build directly on already-completed CAR-71.

Add:

- entity-aware conversational context;
- long-context compaction with provenance;
- relevance-ranked memory retrieval;
- separate user/game/device/session scopes;
- contradiction handling between current facts and old memories;
- stale-memory decay for volatile facts;
- pinned facts versus inferred preferences;
- memory explanation: current conversation vs recalled history vs telemetry.

Requirements:

- Destructive memory commands invalidate active context immediately.
- Deleted/forgotten facts cannot remain retrievable from an index/cache.
- Credentials/secrets remain excluded.
- Cross-game leakage tests.
- Long conversations benchmarked without unbounded prompt growth.

## CAR-82 — Ultra Knowledge Cache & Freshness Engine SSS

Goal: make reuse fast without serving stale facts.

Policies:

- immutable/stable definitions: long TTL;
- product specs: version-aware TTL;
- security/current releases: short TTL;
- prices/news/weather: very short TTL;
- session/device telemetry: session-bound;
- failed/low-confidence research: do not persist as trusted knowledge.

Requirements:

- cache key includes normalized topic + route kind + relevant locale/context;
- stale-while-revalidate only where safe;
- invalidate when provider/model/schema versions make cached representation incompatible;
- cache provenance and retrieval age visible to verifier.

## CAR-83 — Ultra Multilingual, Chilean Spanish & Gaming Language SSS

Goal: understand natural user language, abbreviations and noisy speech without making fuzzy routing unsafe.

Add:

- Spanish-first generation;
- Chilean Spanish recognition variants;
- English understanding;
- common gaming abbreviations;
- spoken acronyms;
- typo/ASR-error normalization;
- entity alias registry;
- per-user safe aliases.

Requirements:

- fuzzy matching cannot override an exact conflicting entity.
- reserved action/profile terms cannot become game aliases.
- ambiguous match requires clarification or safe refusal.
- benchmark accents, slang, typos and speech-recognition substitutions.

## CAR-84 — Ultra Self-diagnostics & Autonomous Reliability SSS

Goal: detect and explain GameHub failures faster while keeping code changes reviewable.

Pipeline:

- Sentry/CI/runtime signal;
- sanitize/redact;
- classify failure;
- correlate with app version/recent change where available;
- identify probable subsystem;
- link existing regression test or propose a new one;
- produce a patch proposal only through the normal development workflow.

Requirements:

- never auto-merge or auto-release solely because an AI generated a patch;
- TDD remains mandatory for behavior fixes;
- confidence/probable-cause language must reflect evidence;
- duplicate crash grouping;
- regression detector integration;
- no secrets in diagnostic prompts/logs.

## CAR-85 — Ultra SSS Benchmark & Final Quality Gate

Goal: make “SSS” measurable instead of subjective.

Create a versioned evaluation corpus that can scale from 1,000 to **10,000–50,000 scenarios** across:

- general stable knowledge;
- fresh/current knowledge;
- mathematics;
- multi-step reasoning;
- specialist science/medicine/security/economics;
- source contradiction;
- hallucination traps;
- context follow-ups;
- long-term memory;
- offline behavior;
- voice/ASR noise;
- game aliases;
- Android actions;
- unsupported capability refusal;
- profile optimization;
- thermal/battery/network predictions;
- multimodal inputs where implemented;
- prompt/tool injection;
- privacy/secret redaction;
- provider outages/timeouts/rate limits;
- cache freshness;
- latency;
- cancellation;
- crash/recovery.

Required metrics:

- task success rate;
- verified factual accuracy;
- abstention precision/recall;
- contradiction detection;
- unsupported-action refusal;
- source relevance;
- stale-data rate;
- latency percentiles;
- crash-free execution;
- memory retrieval precision;
- action success/rollback;
- per-domain results instead of one misleading global score.

Before any CAR-85 “SSS” label is applied, the repository must publish a **versioned pass/fail manifest** for the fixed benchmark suite. For every required metric and every benchmark domain, that manifest must define:

- the exact metric calculation/formula;
- the numeric pass threshold or explicit binary acceptance rule;
- the minimum number of independent runs/repetitions;
- how repeated-run results are aggregated (for example minimum, median, percentile or confidence interval);
- the corpus/benchmark version and relevant runtime/provider configuration;
- and the rule that converts the recorded result into PASS/FAIL.

Thresholds cannot be invented after seeing the result. Changes to a threshold, formula, run count, aggregation rule or fixed-suite composition require a new manifest version and must preserve historical results for comparison.

CAR-85 may only label a subsystem “SSS” when **every required metric for that subsystem passes the versioned manifest repeatedly on the fixed benchmark suite**. GameHub Ultra as a whole is not “SSS in everything” while a required subsystem remains below its gate.

# Dependency order

Recommended dependency graph after CAR-43 is clean:

1. Continue CAR-44..69 in their approved order, applying the SSS overlay above whenever each CAR is reached.
2. Preserve CAR-70..74 as already-existing numbered foundations/history **without assuming every preserved CAR is complete**. Re-check each canonical acceptance list before depending on it; in particular, unresolved CAR-71 requirements must be completed or explicitly carried as prerequisites before CAR-81 can claim its memory layer is complete.
3. CAR-75 Reasoning Core.
4. CAR-76 Research Mesh.
5. CAR-77 Evidence/Confidence.
6. CAR-80 Local/Offline and CAR-81 Memory may progress after the core contracts are stable.
7. CAR-78 Multimodal and CAR-79 Android Agent consume the same reasoning/evidence contracts.
8. CAR-82/83 harden freshness/language across all earlier layers.
9. CAR-84 self-diagnostics integrates CI/Sentry/runtime evidence.
10. CAR-85 is the final measurable SSS gate.

# Definition of “SSS”

SSS is a **quality classification**, not a promise of being universally better than every frontier model.

For GameHub Ultra, SSS means that the relevant subsystem:

- meets its reproducible benchmark thresholds;
- is structurally testable and maintainable;
- has no known release-blocking defect;
- fails safely under missing permissions/providers/models;
- remains truthful about device/platform limitations;
- has bounded latency and cancellation;
- protects user data and secrets;
- and passes all applicable CI/review/release gates.

