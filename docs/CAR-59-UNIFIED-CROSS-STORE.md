# CAR-59 — Unified Cross-store Library (staged, NOT released)

**Dependency:** CAR-57 (Steam multi-account) and CAR-58 (Epic secure authorization). PR #259 for CAR-58 is still unmerged; CAR-59 must not be merged or called complete before its dependency is resolved.

## Invariants

- A copy is identified by its **provider, account ID and provider game ID** (or Android package name for locally installed apps). Titles are never used as account or entitlement identity.
- Equal titles across stores are only a **possible presentation match**. Case/whitespace normalization is minimal. DLC, editions, punctuation and variants remain separate. All source copies remain present, with their original identities.
- A catalog filter must not silently reassign or show another provider's licenses as the selected provider. Offline records are **cached metadata**, not proof of current authentication or purchase rights.
- Installed/favorite/recent are based on local Android package discovery and user preference metadata. A cached Steam or Epic game must not be marked installed/favorite/recent from a similar title.
- The existing Steam active-account visibility boundary remains in `GameHubUltraApp`; an account can be detached without removing other accounts' games.
- The unified catalog is a read-only projection of existing local discovery and store cache. It does not call an undocumented Epic API, perform a remote login, move credentials, or fabricate an Epic account status.

## UI

The existing installed-game carousel and game launch remain unchanged. The new unified section below the installed catalog exposes Todos / Instalados / Steam / Epic provider chips. It reuses existing search, favorite and recent filters. Its entries display source labels and indicate that cross-store title groups are **not validated equivalences**. Cached entries remain visible when local discovery has no results or network connectivity is absent.

## Verification and release gate

- Unit tests: identical-title grouping, distinct editions, multi-account records, source filters, local favorites/recents only, provider ID search, malformed cache, offline projection.
- Compose tests: installed/Steam/Epic grouping, offline single-source filters, empty favorite-filter state.
- CAR-29: release only with same-SHA Android Build, relevant unit tests and emulator jobs, Lint, patch-line JaCoCo >=90%, protected approvals and no unresolved high-severity findings. Skipped checks are not evidence of success.
- Workflows are configured to run automatically only on PRs targeting `main`; the **draft validation PR** may therefore include the unmerged CAR-58 diff in its temporary main-based comparison. This does not authorize bundling or merging CAR-58 or CAR-59. Revalidate on the correct final base after CAR-58 is integrated.
- The single physical testing campaign for CAR-51..69 remains deferred until after CAR-69.
