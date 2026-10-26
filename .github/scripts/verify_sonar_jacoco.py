#!/usr/bin/env python3
"""Fail-closed validation of a genuine JaCoCo XML report before Sonar import."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(path: Path) -> tuple[int, int]:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing or empty JaCoCo XML report: {path}")
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise ValueError(f"Invalid JaCoCo XML report: {path}") from exc
    if root.tag != "report" or not root.findall("package"):
        raise ValueError("Expected a JaCoCo report containing source packages")
    counters = [x for x in root.findall("counter") if x.get("type") == "LINE"]
    if len(counters) != 1:
        raise ValueError("Missing or ambiguous JaCoCo LINE coverage counters")
    try:
        missed = int(counters[0].attrib["missed"])
        covered = int(counters[0].attrib["covered"])
        source = root.findall("./package/sourcefile/line")
        if not source:
            raise ValueError("JaCoCo report has no source line evidence")
        source_covered = 0
        source_missed = 0
        for line in source:
            instructions_missed = int(line.attrib["mi"])
            instructions_covered = int(line.attrib["ci"])
            if instructions_missed < 0 or instructions_covered < 0:
                raise ValueError("Negative JaCoCo line counters")
            if instructions_covered > 0:
                source_covered += 1
            else:
                source_missed += 1
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError("Malformed JaCoCo counters or source lines") from exc
    if covered <= 0 or missed < 0 or (source_covered, source_missed) != (covered, missed):
        raise ValueError("JaCoCo report lacks measured coverage or counters disagree")
    return covered, covered + missed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        covered, total = verify(args.report)
    except ValueError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1
    print(f"Validated JaCoCo XML: {covered}/{total} lines covered (report evidence present).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
