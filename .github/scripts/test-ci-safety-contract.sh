#!/usr/bin/env bash
set -euo pipefail
python3 .github/scripts/check-ci-safety-contract.py
# Verify post-CI state transitions on the PR HEAD, before trusted-main workflow_run deployment.
node --check .github/scripts/ultra_sentinel_post_ci_state.cjs
node --test .github/scripts/ultra_sentinel_post_ci_state.test.cjs
