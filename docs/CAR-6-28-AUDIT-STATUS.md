# CAR-6 to CAR-28 audit status

This document fixes the tracking gap found during the CAR-6 -> CAR-28 audit. It is intentionally conservative: a CAR is not marked complete here unless the repository has clear evidence through merged implementation, CI validation, and no unresolved scope issue.

## Audit rules

- Do not mark a CAR complete only because a workflow is green.
- Verify the relevant PR, issue, tests, lint, release build, baseline profile, macrobenchmark, Qodo/review findings, and logs where applicable.
- Keep canonical open issues open until their full scope is validated.
- Close duplicate tracking issues only as duplicates, not as completed work.
- Do not claim privileged Android controls, stored provider tokens, fabricated FPS, driver installation, frame generation, or unsupported optimization capabilities.

## Current audit matrix

| CAR | Canonical evidence found | Audit status | Required next action |
| --- | --- | --- | --- |
| CAR-6 | PR #8 / docs/CAR-6-TECHNICAL-AUDIT.md | Documented | Keep as baseline audit source. |
| CAR-7 | No dedicated CAR-7 PR/issue found in current audit window | Needs manual trace | Map to early project scope before marking final. |
| CAR-8 | PR #3 | Merged | Review PR comments/checks if re-auditing. |
| CAR-9 | PR #4 | Merged | Review PR comments/checks if re-auditing. |
| CAR-10 | PR #5 | Merged | Review PR comments/checks if re-auditing. |
| CAR-11 | PR #6 | Merged | Review PR comments/checks if re-auditing. |
| CAR-12 | PR #7 | Merged | Review voice safety boundaries if re-auditing. |
| CAR-13 | PR #9 plus later CAR-13 commit | Merged | Verify Core 2.0 scope if re-auditing. |
| CAR-14 | PR #10 and PR #11 | Merged | Verify validation-only PR and Qodo comments if re-auditing. |
| CAR-15 | PR #12 | Merged | Verify AI allowlist/fallback safety if re-auditing. |
| CAR-16 | PR #13 closed unmerged; CAR-16 commits and docs exist | Needs manual trace | Do not treat PR #13 as merged evidence; verify final commit path and CI. |
| CAR-17 | PR #37 merged; PR #36 closed unmerged | Merged via final PR | Keep issue #35 open until CAR-17..19 final audit closes. |
| CAR-18 | PR #38 merged | Merged via final PR | Keep issue #35 open until CAR-17..19 final audit closes. |
| CAR-19 | PR #39 merged | Merged via final PR | Keep issue #35 open until CAR-17..19 final audit closes. |
| CAR-20 | Issue #25 closed; commits found | Partially verified | Cross-check against original issue #15 duplicate before final closure claims. |
| CAR-21 | Issue #26 open; commits found | Open / not complete | Validate persistence, fallback, build/lint/baseline/macrobench before closing. |
| CAR-22 | Issue #27 closed; commits found | Partially verified | Cross-check CI/log evidence before final closure claims. |
| CAR-23 | Issue #28 closed; commits found | Partially verified | Cross-check session persistence and privacy evidence. |
| CAR-24 | Issue #29 closed; commits found | Partially verified | Cross-check peripheral fallback tests. |
| CAR-25 | Issue #30 open; commits found | Open / not complete | Validate full Smart Performance, account safety, learning memory, driver compatibility, rollback and tests. |
| CAR-26 | Issue #31 open; docs commit found | Open / not complete | Implement/verify safe voice 2.0 parser, intent resolver and security filter before closing. |
| CAR-27 | Issue #32 open; commit found | Open / not complete | Verify UI performance pass with macrobench/baseline evidence before closing. |
| CAR-28 | Issue #33 open; commits found | Open / not complete | Verify accessibility/personalization tests before closing. |

## Duplicate issue cleanup plan

The repository contains older duplicate tracking issues for CAR-20 through CAR-29 (#15-#24) and newer canonical issues (#25-#34). The safe cleanup is:

- Close old duplicates as `duplicate` only when their newer canonical issue exists.
- Do not close canonical open issues (#26, #30, #31, #32, #33, #34, #35, #40) as completed until validation is proven.
- Leave issue #40 open as the active audit umbrella.

## Confirmed fixes made in this audit pass

- Added this audit status document so future work does not confuse merged commits with fully validated CAR closure.
- Preserved all open canonical issues where scope is still unverified.
- Created a dedicated branch for audit fixes: `codex/audit-car-6-28-fixes`.

## What still must not be marked complete

At the time of this audit pass, do not mark the following as fully complete without additional evidence:

- CAR-21
- CAR-25
- CAR-26
- CAR-27
- CAR-28
- CAR-17..19 umbrella audit (#35)
- CAR-20..28 umbrella audit (#40)

## Validation expectation for the audit PR

This PR changes documentation only. If GitHub Actions runs, it should still remain green. Qodo/review should focus on whether this audit accurately prevents false completion claims and whether any listed open CAR has enough evidence to move from open to verified.
