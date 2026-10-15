#!/bin/sh
# Optional local installation; never overwrites an existing external hooksPath silently.
set -eu
cd "$(git rev-parse --show-toplevel)"
if ! command -v node >/dev/null 2>&1; then
  echo "Ultra Sentinel: Node.js 18+ required" >&2
  exit 1
fi
current="$(git config --local --get core.hooksPath || true)"
if [ -n "$current" ] && [ "$current" != ".githooks" ]; then
  echo "Existing core.hooksPath=$current — refusing to override it." >&2
  exit 1
fi
chmod +x .githooks/pre-commit
git config --local core.hooksPath .githooks
echo "Ultra Sentinel local pre-commit installed. Uses lightweight 0-network scan."
echo "Optional strict behavior: SENTINEL_PRECOMMIT_STRICT=1 git commit ..."
