#!/usr/bin/env bash
set -euo pipefail
exec python3 .github/scripts/check-ci-safety-contract.py
