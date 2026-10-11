# Ultra Sentinel — stabilization and release contract

This PR is in **security stabilization**, not feature expansion. The 500k+ Core
execution count measures executed checks, **not unique security scenarios**.

## Architecture
- Parse JavaScript as inert data using SHA512-locked Acorn; resolve lexical
  scope using SHA512-locked ESLint Scope; propagate dangerous capabilities through
  bounded assignment, destructuring, object properties, VM factories and aliases.
- Never execute PR code inside privileged workflows, install lifecycle scripts,
  or checkout untrusted PR HEAD when inspecting it.
- Treat unparseable syntax, missing immutable context and analysis limit
  exhaustion as `JS_CONTEXT_INCOMPLETE` and fail closed, not as an approval.
- Make a distinction between a confirmed dynamic-execution sink and an
  unverified situation. Avoid certifying either as safe.
- The candidate self-review is a smoke test, not an independent trusted-main
  attestation. Keep both separate.
- Safeguard Android + Kotlin/Java source reviews with immutable HEAD context.

## Mandatory pre-merge evidence
1. All confirmed P1/P2 review threads resolved with TDD RED/GREEN evidence.
2. Core 15-shard aggregate: zero failures/skips, exact registered baseline
   counts, immutable SHA, and 500,000+ executed tests.
3. Candidate self-review + trusted-main read-only review, mutation,
   adversarial gauntlet and reliability jobs succeed on the current HEAD.
4. Android build and coverage succeed for current HEAD, not previous commits.
5. Independent Codex review on current HEAD: no unresolved reproducible
   severe findings. Never auto-resolve threads before proof.
6. No merge while CI is queued, failing, or while regression evidence is stale.

## Mutation and metamorphic tests
Security fixtures cross syntax trivia, bracket access, aliases, object storage,
destructuring and VM/Reflect entry points. Pair each malicious example with
benign look-alikes and scope-shadowing controls to detect false positives.
For each confirmed bypass: RED repro, root fix, GREEN, preserved regression,
independent retest. Prefer reducing unique error classes over inflating counts.

## Exit criteria
Three consecutive independent review cycles with no new confirmed P1/P2
findings are a stabilization goal—not a guarantee of future flawlessness.
Do not weaken fail-closed policy to achieve a green CI state.
