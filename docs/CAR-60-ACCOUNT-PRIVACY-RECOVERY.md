# CAR-60 — Account Privacy & Recovery

## Scope and threat model
CAR-60 adds local account privacy controls without pretending that cached games prove provider authentication. It depends on CAR-57, CAR-58 and CAR-59; the Epic OAuth backend and authorized Epic library entitlement are **not** delivered or certified by this CAR.

### Disconnect: clear local data with consent
1. The Settings connected-account row opens a confirmation dialog instead of immediately deleting an account.
2. After explicit confirmation, `AccountPrivacyRecovery` confirms that the record still exists, erases only that account's cached store games **first**, then removes the account metadata from DataStore.
3. Store-library deletion commits its scoped cache update to disk and reports an error if the commit fails. A failed cache purge does not proceed to account removal; another account's games remain untouched.
4. After both operations succeed, Settings signals a refresh. On failure, it reports an error instead of claiming success. Cancellation dismisses the dialog without mutations.
5. This is **local data removal only**. It does **not** invalidate vendor-issued sessions, revoke Steam/Epic tokens, or clear the whole Android WebView cookie jar (which could affect unrelated accounts). Neither an offline cache nor its timestamp grants session validity.

A power failure between the two stores can leave the account record with an empty cache or (if the second deletion fails) an account record requiring retry. This conservative partial state avoids silently retaining the target's cached games after an account record is deleted. It is not a distributed atomic transaction.

### Recovery
- Malformed legacy account lines (empty account/public IDs or invalid Base64/platform) are excluded rather than becoming visible/active.
- Reading legacy `accounts_v1` records is supported; on a successful write the account records move to `accounts_v2` and the legacy key is removed. No credential migration is attempted, because this store holds public account metadata, not OAuth secrets.
- Public avatar URLs now require valid HTTPS without embedded userinfo, query strings or fragments, so signed URLs and URL-embedded credentials do not persist.
- A malformed library JSON snapshot fails closed to an empty library. Accountless cache records are rejected. A subsequent valid sync can recover metadata.
- Cached session timestamps older than 30 days, in the future, or invalid (zero/negative) request **REAUTENTICAR** in Settings. Cache content is still available offline, but not interpreted as a verified session.
- Export is user-initiated via Android's share sheet, contains **only counts by provider** and a disclaimer. No identifiers, names, game titles, avatar URLs, access/refresh tokens or account credentials appear. It is a diagnostic report, **not** a restorable backup.

### Verification
- Unit tests: account-scoped ordering, unrelated-account isolation, interrupted cache write, unknown accounts, safe export, stale/unverifiable timestamp policy.
- DataStore tests: legacy migration, malformed rows and unsafe avatar URL rejection.
- Robolectric: corrupted/offline library recovery, scoped cache deletion and on-disk reopen, confirmation/cancellation and reauth UI.
- CAR-29 strict gates: Android Build, JVM/Robolectric tests, lint, patch-line coverage **>=90%** (no exclusions), authenticated automated reviews, exact SHA with no blockers. Do not count skipped checks as successes.

### Release constraints
- CAR-60 branch is stacked on CAR-59 `dbf3e2951aa3724eaaa339f61ef821844b8a9ed0`, itself stacked on the still-unmerged CAR-58. A PR temporarily retargeted to `main` solely to trigger the existing GitHub workflows will contain the cumulative diff until dependencies land.
- **Do not merge CAR-60, CAR-59 or CAR-58** until their respective dependencies and permission/security gates pass. Rebase/isolate and re-run full CI at the final base.
- Epic client secrets and the Supabase `epic-oauth-exchange` configuration remain owner-controlled and are tracked for a reminder upon reaching CAR-69. That reminder is not certification and cannot bypass CAR-29.
- Physical-device verification remains deferred as a single campaign after CAR-69.
