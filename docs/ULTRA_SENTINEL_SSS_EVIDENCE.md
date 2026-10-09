# Ultra Sentinel — Security gates and independent evidence

## Implemented in PR #169

- **Real YAML parser:** js-yaml 4.1.1 with SHA512 integrity-pinned
  package-lock.json and npm ci --ignore-scripts --no-audit --no-fund.
  The parser reads untrusted YAML as data, using strict mapping and type
  validation and bounded source size, number of nodes, depth and cycles.
  Unknown tags, duplicate keys, malformed indentation and ambiguous schemas
  produce INCOMPLETE instead of a silent approval. GitHub's on key is parsed
  as YAML 1.2, not the YAML 1.1 boolean handling of unconfigured PyYAML.
- **Defense in depth:** the trusted auto-review workflow evaluates the old
  heuristic checker AND the new structural AST; either can identify risk.
  Invalid/partial AST evidence and BLOCKERs fail the review. PR source is
  retrieved as data only and never executed by the trusted reviewer.
- **File integrity policy:** ultra_sentinel_policy.cjs compares the complete
  GitHub changed-files list to its declared count and blocks removal or
  renaming of security-critical workflows, parser, policy and lockfile.
- **YAML regression tests:** at least 50 isolated adversarial tests of
  anchors, aliases, escaped keys, merges, BOM, tabs, nested flow maps,
  untrusted PR checkouts, remote shell pipelines, permissions and benign
  negative controls.
- **100-run reproducibility:** the reliability workflow executes five
  unprivileged shards of 20 independent synthetic scenarios, each checking
  three repository profiles (trusted, attacker, tampered), 300 evaluations.
  Any false positive, missed attack, missed tamper or failed shard fails the
  aggregate gate. Report artifacts are SHA-scoped.

**Scope disclaimer:** these are 100 in-repository scenario executions, NOT
100 consecutive remote GitHub Actions workflow runs, and the three profiles
are NOT three separately provisioned external GitHub repositories.

## Mandatory GitHub settings — requires an administrator

A PR cannot enforce branch protection by editing its own YAML. After merging
the verified trusted engine to main:

1. Settings > Rules > Rulesets: target main and require a PR, prevent force
   pushes and deletions, require all review conversations resolved and disable
   bypasses wherever permitted. Require independent approval for changes to
   protected security workflow files.
2. Require exact aggregate checks as they exist in GitHub's Checks UI:
   Sentinel Core / full regression suite; Sentinel Reliability / 100 scenarios;
   Android Build's aggregate build job; aggregate Coverage job. Verify names
   and require a specific trusted GitHub App source, not an arbitrary status.
3. Require a trusted independent reviewer from protected main or an external
   application. A PR author must not be able to substitute a check provider,
   remove its implementation or fabricate an identical status name.
4. Try deleting the auto-review workflow in a disposable PR. Confirm that
   GitHub physically prevents merging even if the malicious PR emits NO checks.

Until those settings are configured, automated detection of .yml deletion
is ADVISORY and must not be described as tamper immunity.

## Three *real* external GitHub repository trials

Three external test repos were not created in PR #169; their provisioning,
branch rules and checks require separate GitHub administration. The required
safe trial plan is:

- sentinel-e2e-safe: pinned read-only workflow and harmless PR => no false
  HIGH/BLOCKER and all required checks pass.
- sentinel-e2e-attack: pull_request_target plus PR head/merge checkout or
  dangerous remote shell => trusted independent check fails; merge blocked.
- sentinel-e2e-tamper: delete/rename Sentinel workflow or lockfile =>
  required check is absent/fails; merge blocked regardless of PR author.

Use disposable repos, no real secrets, no production credentials and
no execution of downloaded attacker scripts. Archive the PR head SHA,
trusted workflow ID, check conclusion, rule IDs, timestamps and immutable
report artifacts. Compare actual outputs to expected verdicts.

## Completion criteria before SS / SSS claims

- The complete current-SHA Core, AST, mutation and reliability suites pass.
- Android Build, Coverage and external Codecov verify the SAME head SHA.
- No unaddressed high-impact Codex or independent review findings remain.
- The three real repositories demonstrate protected merges as above.
- Long-run evidence measures false-positive rate, false-negative rate,
  parser failures and latency across diverse cases; 100 green runs alone
  cannot prove absence of vulnerabilities.
- Independent audit, adversarial/fuzz testing and provenance reviews support
  any future SSS rating.

Do not merge PR #169 with confirmed errors or unfinished required checks.
