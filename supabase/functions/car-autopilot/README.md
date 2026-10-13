# CAR Autopilot — event-driven external connector (not yet deployed)

The job is to hand off the **next already-approved CAR issue** to GitHub Copilot after a verified CAR PR has merged. It does **not** write code, enable GH bypass, run on a timer, create new CARs, or merge PRs. Mergify remains independently responsible for merging clean code. Written for the already-existing `Gamehub ultra` Supabase project. Safe failure when the required secrets are absent.

## One-time owner setup

1. Merge PR #195 first after passing all gates; it carries the Copilot agent profile and Mergify policy. Protect `main` in GitHub; do not allow bypass, require Android, Coverage and Sentinel checks.
2. Deploy this Edge Function only after reviewing the tests, security and CI. The Supabase platform must use **verify_jwt=false only for this function** because GitHub authenticates through raw HMAC SHA-256. `handler.mjs` rejects requests without a valid signed header.
3. Configure these Supabase Edge Function secrets in the **Supabase dashboard**, never in GitHub issues or chat: `CAR_GITHUB_WEBHOOK_SECRET` (new strong random string), `CAR_GITHUB_ISSUES_TOKEN` (fine-grained token for this single repo: Metadata read, Issues read/write, Pull Requests read; do not grant Contents write, administration, workflows or merge permissions).
4. Add a GitHub repository webhook in **Settings → Webhooks** with URL `https://<your-project-ref>.supabase.co/functions/v1/car-autopilot`, content type `application/json`, the **same** signing secret and event **Pull requests only**. Keep SSL verification enabled.
5. Open only one CAR issue with `car:ready` at first and use the owner-created issue template; dependencies must already be validated before adding the label. Ensure Copilot cloud coding agent is enabled and able to access custom agent `gamehub-car-engineer`.
6. Copilot must include `Fixes #<CAR ISSUE NUMBER>` in the PR body, create PR title beginning `CAR-N`, and leave it draft until validation. Its merge closes the linked CAR; the webhook refuses to dispatch another CAR while any `car:active` issue remains open.
7. Test with one small non-privileged CAR and verify: HMAC rejection, issue assignment, draft PR, tests, Mergify merge, auto-close issue, next ready CAR assignment. If any stage fails, stop and repair; do not skip rules.

## Security and limits

- Validates `X-Hub-Signature-256` over raw bytes, repository immutable ID (survives rename), owner ID, exact source repo, merged PR, CAR identity, recent timestamp and GitHub `X-GitHub-Delivery` format.
- Rejects payloads above 128KiB and returns 503 without secrets. Never logs secrets or bodies.
- This function selects the earliest ready owner-authored CAR and uses GitHub's documented `agent_assignment` mechanism. It cannot verify Copilot entitlements; a rejected API call fails safely.
- At most one active CAR; no new issues are fabricated. Queue labels `car:ready`, `car:active`, `car:blocked` are external contracts, not authorization substitutes.
- Repeated webhook deliveries after assignment see `car:active`. There is no *durable* database ledger for `X-GitHub-Delivery`, so strict global exactly-once across concurrency/restarts is **not guaranteed**. Before production-scale use, add a unique delivery ledger with transactional locking and replay windows; do not claim perfect idempotency.
- A repo rename keeps the immutable ID, but the GitHub webhook and token application scope must be checked afterwards. Mergify's configuration lives with the renamed repo.

Local tests: `node --test supabase/functions/car-autopilot/handler.test.mjs`. Github CI workflow `CAR Autopilot Webhook Tests` is unprivileged and event-driven.

**Actual status:** Source-only draft PR until merged, Supabase-deployed, secrets added and webhook enabled. It does not run merely because this document exists.
