# Ultra Sentinel #179 — Intelligent Baseline & Quality Gate

**Status: SHADOW PROTOTYPE / NOT A MERGE AUTHORITY.** This change does not replace
existing trusted main self-review, Core, Android Build, Coverage, or independent
review gates. It cannot create or merge a PR, write to production, or grant approval.

## Five blocks implemented as a pure evaluation library

1. **Baseline and changed-code attribution:** accepts separate complete baseline
   and HEAD findings linked to supplied 40-hex refs; deterministic SHA-256
   fingerprints survive source-line shifts when a qualified semantic symbol is
   available; validated rename mapping, escalations and dependency reactivation.
   Inherited severe findings remain visible, not hidden.
2. **Severity policy:** BLOCKER/CRITICAL/HIGH → review-required; MAJOR/WARNING/MEDIUM
   → advisory; INFO/MINOR/LOW → report. Unknown severities and absent evidence
   produce INCOMPLETE, not PASS. Existing gates are never bypassed.
3. **Family-based report:** grouping is based on a supplied causal mechanism,
   not only on a matching rule; distinct locations are never silently dropped.
   Exports bounded 50 annotations and SARIF 2.1.0 partialFingerprints; sensitive
   source snippets and alleged secrets are not copied into messages.
4. **SHA-scoped quality-gate evaluator:** compares an explicit list of required
   CI checks, requiring independently supplied same-SHA trustworthy evidence;
   missing/skipped/stale/untrusted/pending checks return INCOMPLETE; failing
   required checks return BLOCKED. No write permissions or auto-merge.
5. **Adversarial validation / shadow mode:** 31 deterministic Node.js tests,
   including malicious paths, oversized contexts, duplicate alerts,
   conflicting renames, check spoofing, dropped checks, historical escalation,
   stable fingerprints, regression-family grouping and a legacy divergence signal.
   A read-only workflow runs these contract tests when relevant files change.

## IMPORTANT: Work remaining before production enablement

- The new evaluator does NOT yet collect baseline and head findings from a
  trusted GitHub API source. SHA strings and "trusted" fields supplied by an
  untrusted caller are **not authentication**.
- Real merge-base verification, authenticated full-repository scans, a verified
  dependency graph, branch-aware historical data, GitHub Check Runs publishing,
  and empirical shadow comparison against the current CI gate are still pending.
- The SARIF payload is constructed but not published as a GitHub Code Scanning
  alert. No automatic annotations are posted.
- No existing mandatory gate is changed. Never label this evaluator a production
  quality gate until a trusted, provenance-checked integration is separately
  reviewed, tested on fresh unseen PRs, and explicitly authorized.

**Rollout rule:** shadow → independently validated evidence → owner-reviewed
policy decision → only then consider making a check required. This document
does not authorize that policy change.
