#!/usr/bin/env python3
"""Inspect a user-selected OSRS cache folder without exporting asset data."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

# Classic Jagex cache storage filenames; other formats are reported as unknown.
CACHE_PATTERN = re.compile(r"^main_file_cache\.(?:dat2?|idx\d+)$", re.I)
MAX_FILES = 100000


def inspect(root: Path) -> dict:
    root = root.expanduser().resolve(strict=True)
    if not root.is_dir():
        raise ValueError("Provided cache path is not a directory")

    examined = 0
    matches: list[dict] = []
    for candidate in root.rglob("*"):
        examined += 1
        if examined > MAX_FILES:
            raise ValueError("Cache root contains too many entries; select a narrower directory")
        # Never follow symlinks or read filenames outside the requested directory.
        if candidate.is_symlink() or not candidate.is_file():
            continue
        if not CACHE_PATTERN.fullmatch(candidate.name):
            continue
        size = candidate.stat().st_size
        # Hash filenames and metadata, NOT file contents or account-specific paths.
        matches.append({"file": candidate.name, "sizeBytes": size})
    matches.sort(key=lambda x: x["file"])
    sha = hashlib.sha256()
    for record in matches:
        sha.update(f"{record['file']}\0{record['sizeBytes']}\n".encode("utf-8"))
    return {
        "schemaVersion": 1,
        "targetOsrsRevision": 242,
        "cacheRevisionVerified": False,
        "cacheContentsCopied": False,
        "recognizedFiles": matches,
        "recognizedCount": len(matches),
        "layoutFingerprint": sha.hexdigest(),
        "note": "File structure does not prove revision 242; cache group validation is required.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("cache_directory", type=Path)
    parser.add_argument("--output", type=Path, default=Path("cache-inventory.json"))
    args = parser.parse_args()
    try:
        summary = inspect(args.cache_directory)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"Saved cache file-layout report to {output.name} ({summary['recognizedCount']} recognized files).")
    print("This does not verify the OSRS revision or export any cache group contents.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
