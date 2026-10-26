#!/usr/bin/env python3
"""Read-only, source-neutral APK ZIP inventory before a separately authorized REA run.

Only file-count metadata and a SHA-256 of the input archive are reported. This
script does not decompile, unpack, copy, run or modify any application code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import sys
import zipfile
from collections import Counter
from pathlib import Path

MAX_APK_BYTES = 1024 * 1024 * 1024
MAX_ENTRIES = 100_000
MAX_DECLARED_UNCOMPRESSED = 16 * 1024 * 1024 * 1024
DEX_NAME = re.compile(r"classes(?:[2-9][0-9]*)?\.dex\Z")


class InventoryError(Exception):
    pass


def inspect_apk(path: Path) -> dict:
    if path.is_symlink() or not path.is_file():
        raise InventoryError("APK must be an existing, regular, non-symlink file")
    size = path.stat().st_size
    if not 0 < size <= MAX_APK_BYTES:
        raise InventoryError("APK size outside conservative inventory limit")

    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)

    try:
        with zipfile.ZipFile(path, "r") as archive:
            members = archive.infolist()
            if not 0 < len(members) <= MAX_ENTRIES:
                raise InventoryError("APK entry count outside safe limit")
            seen = set()
            total = 0
            native = Counter()
            dex_count = 0
            assets = 0
            resources = 0
            manifest = False
            signed_metadata_present = False
            for info in members:
                name = info.filename
                parts = name.rstrip("/").split("/")
                if (name.startswith("/") or "\\" in name or "\x00" in name
                        or any(part in ("", ".", "..") for part in parts)
                        or any(ord(c) < 32 for c in name)
                        or ":" in parts[0]):
                    raise InventoryError("APK contains an unsafe member name")
                unix_type = (info.external_attr >> 16) & 0o170000
                if unix_type == stat.S_IFLNK:
                    raise InventoryError("APK contains an unsupported symlink")
                if name in seen:
                    raise InventoryError("APK contains duplicate entries")
                seen.add(name)
                total += info.file_size
                if total > MAX_DECLARED_UNCOMPRESSED:
                    raise InventoryError("APK exceeds declared uncompressed size budget")
                if name == "AndroidManifest.xml":
                    manifest = True
                if DEX_NAME.fullmatch(name):
                    dex_count += 1
                if len(parts) == 3 and parts[0] == "lib" and parts[2].endswith(".so"):
                    native[parts[1]] += 1
                if parts[0] == "assets" and not info.is_dir():
                    assets += 1
                if parts[0] == "res" and not info.is_dir():
                    resources += 1
                if parts[0] == "META-INF":
                    signed_metadata_present = True
            if not manifest or dex_count == 0:
                raise InventoryError("APK manifest or primary DEX declarations missing")
    except (zipfile.BadZipFile, zipfile.LargeZipFile, OSError) as exc:
        raise InventoryError("APK archive is unreadable or invalid") from exc

    return {
        "status": "STATIC_METADATA_ONLY",
        "apk_sha256": digest.hexdigest(),
        "apk_size_bytes": size,
        "entry_count": len(members),
        "declared_uncompressed_bytes": total,
        "has_android_manifest": manifest,
        "dex_count": dex_count,
        "native_abi_counts": dict(sorted(native.items())),
        "asset_count": assets,
        "resource_count": resources,
        "has_meta_inf": signed_metadata_present,
        "limitations": [
            "Only ZIP central-directory metadata was inspected; no decompilation or code recovery.",
            "Entry CRCs/signatures and APK authenticity were not verified.",
            "Not a permission, ownership, security, runtime, or license assessment.",
            "Do not commit extracted proprietary code, images, tokens, or credentials.",
        ],
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True, help="authorized local APK file")
    parser.add_argument("--out", type=Path, help="exclusive JSON output path (never overwritten)")
    args = parser.parse_args(argv)
    try:
        report = inspect_apk(args.apk)
        payload = json.dumps(report, sort_keys=True, indent=2, ensure_ascii=False) + "\n"
        if args.out is None:
            print(payload, end="")
        else:
            with args.out.open("x", encoding="utf8") as output:
                output.write(payload)
        return 0
    except (InventoryError, FileExistsError, PermissionError, IsADirectoryError, OSError):
        print("NOT_ANALYZED: invalid/unavailable APK or output path; no data was committed", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
