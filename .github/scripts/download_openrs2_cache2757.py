#!/usr/bin/env python3
"""Download an ephemeral bounded OpenRS2 revision-242 cache for isolated CI.

Caches are NOT committed, uploaded as artifacts, or used for login. The Java
provider compares their reconstructed 255/255 master with a separately pinned
SHA-256 reference before opening a local socket.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import re
import shutil
from urllib.request import urlopen
import zipfile

ROOT = Path("/tmp/ksp-js5-full-cache")
ZIP = ROOT / "cache-disk.zip"
EXTRACTED = ROOT / "cache"
MASTER = ROOT / "master-255-255.dat"
MAX_ARCHIVE = 400 * 1024 * 1024
MAX_TOTAL = 600 * 1024 * 1024
MASTER_SHA = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38"
URL_ROOT = "https://archive.openrs2.org/caches/runescape/2757"


def retrieve_bounded(url: str, target: Path, limit: int) -> int:
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".partial")
    if partial.exists():
        raise RuntimeError("Unsafe stale partial cache download")
    total = 0
    try:
        with urlopen(url, timeout=120) as response, partial.open("xb") as output:
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                total += len(block)
                if total > limit:
                    raise RuntimeError("Archived download exceeds strict byte limit")
                output.write(block)
        if total < 5:
            raise RuntimeError("Unexpected empty archive response")
        partial.replace(target)
        return total
    finally:
        if partial.exists():
            partial.unlink()


def extract_cache_only(source: Path) -> tuple[int, int]:
    EXTRACTED.mkdir(parents=True, exist_ok=True)
    files = 0
    total = 0
    seen: set[str] = set()
    with zipfile.ZipFile(source) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            if not re.fullmatch(r"cache/main_file_cache\.(?:dat2|idx\d+)", item.filename):
                raise RuntimeError("Unexpected cache zip path")
            if item.filename in seen:
                raise RuntimeError("Duplicate cache zip path")
            seen.add(item.filename)
            mode = (item.external_attr >> 16) & 0o170000
            if mode not in (0, 0o100000):
                raise RuntimeError("Unexpected ZIP entry type")
            if item.file_size <= 0 or item.file_size > MAX_TOTAL:
                raise RuntimeError("Invalid cache file length")
            total += item.file_size
            if total > MAX_TOTAL:
                raise RuntimeError("Cache ZIP expands beyond safe limit")
            target = ROOT / item.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(item) as input_stream, target.open("xb") as output_stream:
                copied = shutil.copyfileobj(input_stream, output_stream, 1024 * 1024)
            if target.stat().st_size != item.file_size:
                raise RuntimeError("Incomplete cache file extraction")
            files += 1
    if files < 20 or not (EXTRACTED / "main_file_cache.dat2").is_file() or not (
        EXTRACTED / "main_file_cache.idx255"
    ).is_file():
        raise RuntimeError("Required native cache files are missing")
    return files, total


def main() -> None:
    size = retrieve_bounded(URL_ROOT + "/disk.zip", ZIP, MAX_ARCHIVE)
    files, unpacked = extract_cache_only(ZIP)
    # Preserve ephemeral full cache but release compressed ZIP disk space.
    ZIP.unlink()
    length = retrieve_bounded(
        URL_ROOT + "/archives/255/groups/255.dat", MASTER, 1024 * 1024
    )
    if length != 205 or hashlib.sha256(MASTER.read_bytes()).hexdigest() != MASTER_SHA:
        raise RuntimeError("Independently archived master SHA-256 mismatch")
    print("KSP_JS5_FULL_CACHE_READY fileCount=" + str(files)
          + " zipBytes=" + str(size) + " cacheBytes=" + str(unpacked)
          + " masterSha256Verified=true", flush=True)


if __name__ == "__main__":
    main()
