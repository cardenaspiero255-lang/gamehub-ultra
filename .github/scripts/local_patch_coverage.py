#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


SOURCE_ROOTS = ("app/src/main/java/", "app/src/main/kotlin/")


@dataclass(frozen=True)
class PatchCoverage:
    executable: int
    covered: int
    unmapped_files: tuple[str, ...] = ()

    @property
    def percent(self) -> float:
        if self.executable == 0:
            return 100.0
        return self.covered * 100.0 / self.executable


def parse_added_lines(diff_text: str) -> dict[str, set[int]]:
    added: dict[str, set[int]] = {}
    current_file: str | None = None
    new_line: int | None = None

    for raw in diff_text.splitlines():
        if raw.startswith("+++ "):
            path = raw[4:].strip()
            current_file = path[2:] if path.startswith("b/") else path
            if current_file == "/dev/null":
                current_file = None
            continue

        if raw.startswith("@@ "):
            match = re.search(r"\+(\d+)(?:,(\d+))?", raw)
            if match is None:
                raise ValueError(f"Unable to parse diff hunk: {raw}")
            new_line = int(match.group(1))
            continue

        if current_file is None or new_line is None:
            continue

        if raw.startswith("+") and not raw.startswith("+++"):
            added.setdefault(current_file, set()).add(new_line)
            new_line += 1
        elif raw.startswith("-") and not raw.startswith("---"):
            continue
        else:
            new_line += 1

    return added


def _report_lines(report: ET.Element) -> dict[str, dict[int, tuple[int, int]]]:
    result: dict[str, dict[int, tuple[int, int]]] = {}
    for package in report.findall("package"):
        package_name = package.get("name", "").strip("/")
        for source in package.findall("sourcefile"):
            source_name = source.get("name")
            if not source_name:
                continue
            suffix = f"{package_name}/{source_name}" if package_name else source_name
            lines: dict[int, tuple[int, int]] = {}
            for line in source.findall("line"):
                number = int(line.get("nr", "0"))
                missed = int(line.get("mi", "0"))
                covered = int(line.get("ci", "0"))
                if number > 0:
                    lines[number] = (missed, covered)
            for root in SOURCE_ROOTS:
                result[root + suffix] = lines
    return result


def _looks_executable_source_line(line: str) -> bool:
    stripped = line.strip()
    if not stripped:
        return False
    if stripped.startswith(("//", "/*", "*", "*/", "package ", "import ", "@")):
        return False
    if stripped in {"{", "}", "(", ")", ")", "}", "},", ");"}:
        return False
    if re.match(
        r"^(?:(?:public|private|protected|internal|override|abstract|open|final|"
        r"suspend|inline|tailrec|operator|infix|external|expect|actual)\s+)*"
        r"(?:fun|class|data\s+class|sealed\s+class|enum\s+class|interface|"
        r"fun\s+interface|object)\b",
        stripped,
    ):
        return False
    if re.match(
        r"^(?:(?:public|private|protected|internal)\s+)?companion\s+object\b",
        stripped,
    ):
        return False
    if re.match(
        r"^(?:(?:public|private|protected|internal)\s+)?const\s+val\b",
        stripped,
    ):
        return False
    if re.match(r"^[A-Za-z_][A-Za-z0-9_]*\s*:\s*[^=]+,?$", stripped):
        return False
    if re.match(r"^\)\s*(?::\s*[^=]+)?\s*\{?$", stripped):
        return False
    if re.match(r"^(?:else\s*->\s*\{|\}\s*else\s*\{)$", stripped):
        return False
    return True


def calculate_patch_line_coverage(
    report: ET.Element,
    added_lines: dict[str, set[int]],
    source_text_by_path: dict[str, str] | None = None,
) -> PatchCoverage:
    report_by_path = _report_lines(report)
    executable = 0
    covered = 0
    unmapped: list[str] = []

    for path, line_numbers in added_lines.items():
        if not path.startswith(SOURCE_ROOTS):
            continue

        source_lines = report_by_path.get(path)
        if source_lines is None:
            if line_numbers:
                unmapped.append(path)
            continue

        for number in line_numbers:
            counters = source_lines.get(number)
            if counters is None:
                if source_text_by_path is not None:
                    source = source_text_by_path.get(path, "").splitlines()
                    source_line = source[number - 1] if 0 < number <= len(source) else ""
                    if _looks_executable_source_line(source_line):
                        unmapped.append(f"{path}:{number}")
                continue
            missed, hit = counters
            if missed + hit <= 0:
                continue
            executable += 1
            if hit > 0:
                covered += 1

    return PatchCoverage(
        executable=executable,
        covered=covered,
        unmapped_files=tuple(sorted(unmapped)),
    )


def meets_threshold(stats: PatchCoverage, minimum_percent: float) -> bool:
    if stats.unmapped_files:
        return False
    return stats.percent + 1e-9 >= minimum_percent


def _git_diff(base: str, head: str, paths: Iterable[str]) -> str:
    command = ["git", "diff", "--unified=0", "--no-color", f"{base}...{head}", "--", *paths]
    result = subprocess.run(
        command,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr, end="")
        raise SystemExit(result.returncode)
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Enforce JaCoCo line coverage for executable lines added by the patch."
    )
    parser.add_argument("--xml", required=True, type=Path)
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--min-patch-line", type=float, default=90.0)
    args = parser.parse_args()

    if not 0.0 <= args.min_patch_line <= 100.0:
        parser.error("--min-patch-line must be between 0 and 100")
    if not args.xml.is_file():
        print(f"::error::Coverage XML not found: {args.xml}", file=sys.stderr)
        return 2

    diff_text = _git_diff(args.base, args.head, SOURCE_ROOTS)
    added = parse_added_lines(diff_text)
    report = ET.parse(args.xml).getroot()
    source_text_by_path = {
        path: Path(path).read_text(encoding="utf-8")
        for path in added
        if path.startswith(SOURCE_ROOTS) and Path(path).is_file()
    }
    stats = calculate_patch_line_coverage(report, added, source_text_by_path)

    print(
        "Local patch coverage: "
        f"{stats.covered}/{stats.executable} executable added lines "
        f"({stats.percent:.2f}%), minimum {args.min_patch_line:.2f}%."
    )

    if stats.unmapped_files:
        for path in stats.unmapped_files:
            print(
                f"::error::Changed production source is missing from JaCoCo report: {path}",
                file=sys.stderr,
            )
        return 1

    if not meets_threshold(stats, args.min_patch_line):
        print(
            f"::error::Patch line coverage {stats.percent:.2f}% is below "
            f"{args.min_patch_line:.2f}%.",
            file=sys.stderr,
        )
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
