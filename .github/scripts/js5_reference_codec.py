"""Bounded standard-library decoder for pinned OSRS JS5 reference tables.

It mirrors the format handled by server/protocol-lab CacheMetadata.java:
container type 0/1/2, reference protocol 5/6/7, delta group IDs,
CRC32 and group revision. No sockets, credentials or mutable cache access.
"""
from __future__ import annotations

import bz2
import gzip
import struct
import zlib

MAX_BYTES = 32 * 1024 * 1024
MAX_GROUPS = 100_000
MAX_FILES = 1_000_000


def container_data(raw: bytes) -> bytes:
    """Decode one complete compressed JS5 container, no version trailer."""
    if len(raw) < 5 or raw[0] > 2:
        raise ValueError("Unsupported or incomplete JS5 cache container")
    packed = struct.unpack_from(">I", raw, 1)[0]
    kind = raw[0]
    head = 5 if kind == 0 else 9
    if packed > MAX_BYTES or len(raw) != packed + head:
        raise ValueError("Unexpected packed container length")
    if kind == 0:
        return raw[5:]
    expected = struct.unpack_from(">I", raw, 5)[0]
    if expected > MAX_BYTES:
        raise ValueError("Unpacked reference table exceeds safe size")
    compressed = raw[9:]
    if kind == 1:
        expanded = bz2.decompress(b"BZh1" + compressed)
    else:
        expanded = gzip.decompress(compressed)
    if len(expanded) != expected:
        raise ValueError("Uncompressed reference table length mismatch")
    return expanded


class Reader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.pos = 0

    def get(self, size: int) -> bytes:
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError("Truncated JS5 reference table")
        result = self.data[self.pos:self.pos + size]
        self.pos += size
        return result

    def u8(self) -> int:
        return self.get(1)[0]

    def u16(self) -> int:
        return struct.unpack(">H", self.get(2))[0]

    def u32(self) -> int:
        return struct.unpack(">I", self.get(4))[0]

    def number(self, protocol: int) -> int:
        if protocol < 7 or self.data[self.pos:self.pos + 1] < b"\\x80":
            return self.u16()
        return self.u32() & 0x7FFFFFFF

    def skip_u32s(self, count: int) -> None:
        self.get(4 * count)


def parse_group_checksums(raw: bytes) -> dict[int, tuple[int, int]]:
    """Return group ID -> (unsigned CRC32, revision) from a reference table."""
    body = container_data(raw)
    if len(body) > MAX_BYTES:
        raise ValueError("Oversized reference table")
    reader = Reader(body)
    protocol = reader.u8()
    if protocol not in (5, 6, 7):
        raise ValueError("Unsupported JS5 reference protocol")
    if protocol >= 6:
        reader.u32()  # Reference index revision
    flags = reader.u8()
    if flags & ~5:
        raise ValueError("Unsupported JS5 reference flags")
    named = bool(flags & 1)
    sized = bool(flags & 4)
    count = reader.number(protocol)
    if count > MAX_GROUPS:
        raise ValueError("Too many reference-table groups")
    group_ids = []
    previous = 0
    for i in range(count):
        delta = reader.number(protocol)
        if i and delta == 0:
            raise ValueError("Duplicate reference group")
        previous += delta
        if previous > 0x7FFFFFFF:
            raise ValueError("Reference group ID overflow")
        group_ids.append(previous)
    if named:
        reader.skip_u32s(count)
    crcs = [reader.u32() for _ in range(count)]
    if sized:
        reader.skip_u32s(count * 2)
    revisions = [reader.u32() for _ in range(count)]
    file_counts = [reader.number(protocol) for _ in range(count)]
    if sum(file_counts) > MAX_FILES:
        raise ValueError("Too many reference-table files")
    for file_count in file_counts:
        last = 0
        for index in range(file_count):
            delta = reader.number(protocol)
            if index and not delta:
                raise ValueError("Duplicate file ID")
            last += delta
            if last > 0x7FFFFFFF:
                raise ValueError("Reference file ID overflow")
    if named:
        reader.skip_u32s(sum(file_counts))
    if reader.pos != len(body):
        raise ValueError("Unexpected trailing reference-table bytes")
    return dict(zip(group_ids, zip(crcs, revisions)))


def verified_group_payload(raw: bytes, crc: int, revision: int) -> bytes:
    """Verify a downloaded ordinary JS5 cache group against its pinned reference."""
    if len(raw) < 5 or raw[0] > 2:
        raise ValueError("Malformed JS5 group container")
    packed = struct.unpack_from(">I", raw, 1)[0]
    head = 5 if raw[0] == 0 else 9
    if len(raw) < head or packed > MAX_BYTES:
        raise ValueError("Invalid JS5 group header/size")
    length = head + packed
    trailing = len(raw) - length
    if trailing not in (0, 2):
        raise ValueError("Unexpected group trailer size")
    if trailing and struct.unpack_from(">H", raw, length)[0] != revision & 0xFFFF:
        raise ValueError("JS5 group revision suffix mismatch")
    container = raw[:length]
    if zlib.crc32(container) & 0xFFFFFFFF != crc:
        raise ValueError("JS5 group CRC does not match pinned reference table")
    return container
