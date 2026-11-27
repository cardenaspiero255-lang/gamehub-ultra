#!/usr/bin/env python3
"""Identify retryable Maven Central HTTP 429 failures in a Gradle log."""
from __future__ import annotations

import re
import sys
from pathlib import Path


_MAVEN_HOST = re.compile(
    r"https?://(?:repo1?\.maven\.org|repo\.maven\.apache\.org)(?:/|\b)",
    re.IGNORECASE,
)
_HTTP_429 = re.compile(
    r"(?:HTTP\s+response\s+code\s*:\s*429|"
    r"(?:received\s+)?status\s+code\s+429|"
    r"429\s+Too\s+Many\s+Requests)",
    re.IGNORECASE,
)


def is_transient_maven_429(log: str) -> bool:
    """Return true only when a Maven Central endpoint returned HTTP 429."""
    return any(
        _MAVEN_HOST.search(line) and _HTTP_429.search(line)
        for line in log.splitlines()
    )


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: is_transient_maven_429.py <gradle-log>", file=sys.stderr)
        return 2
    try:
        log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        print(f"Unable to read Gradle log: {exc}", file=sys.stderr)
        return 2
    return 0 if is_transient_maven_429(log) else 1


if __name__ == "__main__":
    raise SystemExit(main())
