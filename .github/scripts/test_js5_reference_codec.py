"""Offline fixture tests for the strict JS5 reference-table decoder."""
import gzip
import struct
import unittest
import zlib

from js5_reference_codec import (
    container_data,
    parse_group_checksums,
    verified_group_payload,
)


def number(value, protocol):
    if protocol < 7 or value < 32768:
        return struct.pack(">H", value)
    return struct.pack(">I", value | 0x80000000)


def sample_reference(protocol=6, group_ids=(0, 199)):
    groups = len(group_ids)
    data = bytearray((protocol,))
    if protocol >= 6:
        data.extend(struct.pack(">I", 7))
    data.append(0)  # no names; no sizes
    data.extend(number(groups, protocol))
    previous = 0
    for group in group_ids:
        data.extend(number(group - previous, protocol))
        previous = group
    for i in range(groups):
        data.extend(struct.pack(">I", 0xABCDEF01 + i))
    for i in range(groups):
        data.extend(struct.pack(">I", 20 + i))
    for i in range(groups):
        data.extend(number(1, protocol))
    for _ in range(groups):
        data.extend(number(0, protocol))
    return bytes((0,)) + struct.pack(">I", len(data)) + data


class Js5ReferenceCodecTest(unittest.TestCase):
    def test_reference_protocol_6(self):
        groups = parse_group_checksums(sample_reference())
        self.assertEqual(groups, {
            0: (0xABCDEF01, 20),
            199: (0xABCDEF02, 21),
        })

    def test_protocol_5_and_7(self):
        self.assertEqual(len(parse_group_checksums(sample_reference(5))), 2)
        groups = parse_group_checksums(sample_reference(7, (0, 35000)))
        self.assertIn(35000, groups)

    def test_gzip_reference(self):
        uncompressed = container_data(sample_reference())
        compressed = gzip.compress(uncompressed)
        packed = (bytes((2,)) + struct.pack(">I", len(compressed))
                  + struct.pack(">I", len(uncompressed)) + compressed)
        self.assertEqual(parse_group_checksums(packed),
                         parse_group_checksums(sample_reference()))

    def test_reject_tampered_reference(self):
        original = sample_reference()
        with self.assertRaises(ValueError):
            parse_group_checksums(original[:-1])
        with self.assertRaises(ValueError):
            parse_group_checksums(original + b"ignored")

    def test_group_crc_and_revision(self):
        original = bytes((0, 0, 0, 0, 2, 45, 67))
        crc = zlib.crc32(original) & 0xFFFFFFFF
        self.assertEqual(verified_group_payload(original, crc, 123), original)
        trailer = struct.pack(">H", 123)
        self.assertEqual(verified_group_payload(original + trailer, crc, 123), original)
        with self.assertRaises(ValueError):
            verified_group_payload(original, crc + 1, 123)
        with self.assertRaises(ValueError):
            verified_group_payload(original + trailer, crc, 124)


if __name__ == "__main__":
    unittest.main()
