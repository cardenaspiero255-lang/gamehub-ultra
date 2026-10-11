# Ultra Sentinel 500k Core validation

The existing 130,000 deterministic adversarial inputs are preserved. The new matrix adds 370,500 **different fixture inputs**, across protected-file trust, review chronology, CI run provenance, Kotlin lexical context, downloaded-code execution and YAML workflow semantics. Together they produce 500,500 individual matrix tests, plus pre-existing baseline tests. Fixture diversity does not mean 500,500 independent vulnerability types or mathematically prove zero missed bugs.

Execution: 13 file-disjoint matrix shards (10,000 original plus 28,500 new per shard) and two baseline shards; exact SHA, zero failures/skips/TODOs, verified 15 JSON reports, completeness and SHA-256 uniqueness tests. No fake counters, external network requests, malicious code execution or live credentials in fixtures.

GitHub Actions guardrails: at most 4 concurrent Core shards, hard job and subprocess timeouts, cancellation of superseded PR runs, compact reports and no automatic scheduled re-runs. Keep standard hosted runners; watch total usage/queues across *all* workflows and do not retry continuously. No configuration can guarantee an account will never be rate-limited. Never merge with a confirmed security finding or an unsatisfied independent approval requirement.
