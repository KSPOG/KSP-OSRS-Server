"""Offline and loopback tests for the opt-in JS5 capture proxy."""
import hashlib
import json
from pathlib import Path
import socket
import tempfile
import threading
import time
import unittest

from js5_master_capture_proxy import MasterCapture, proxy_one_session


def framed_master(payload: bytes, compression: int = 0) -> bytes:
    # Uncompressed form: prefix FF/00/FF + type + packed length + payload.
    if compression != 0:
        raise NotImplementedError
    container = bytes([255, 0, 255, compression]) + len(payload).to_bytes(4, "big") + payload
    output = bytearray()
    pos = 0
    while pos < len(container):
        if pos < 512:
            count = min(512 - pos, len(container) - pos)
            output.extend(container[pos:pos + count])
        else:
            output.append(255)
            count = min(511, len(container) - pos)
            output.extend(container[pos:pos + count])
        pos += count
    return bytes(output)


class MasterCaptureTest(unittest.TestCase):
    def test_fragmented_response_with_marker(self):
        wire = framed_master(bytes(i % 251 for i in range(1300)))
        parser = MasterCapture()
        result = None
        for i in range(0, len(wire), 7):
            result = parser.feed(wire[i:i+7]) or result
        self.assertEqual(wire, result)
        self.assertEqual("captured", parser.status)
        self.assertEqual(2, parser.markers)

    def test_login_success_ack_then_fragmented_master(self):
        wire = framed_master(bytes(range(10)))
        parser = MasterCapture(expect_login_success=True)
        self.assertIsNone(parser.feed(b"\x00"))
        self.assertEqual(wire, parser.feed(wire))
        self.assertEqual("captured", parser.status)

    def test_login_failure_rejected(self):
        parser = MasterCapture(expect_login_success=True)
        self.assertIsNone(parser.feed(b"\x06"))
        self.assertEqual("js5-login-not-successful", parser.status)

    def test_non_master_is_not_captured(self):
        parser = MasterCapture()
        self.assertIsNone(parser.feed(b"\x00\x00\x01"))
        self.assertEqual("first-response-not-master", parser.status)

    def test_reject_invalid_marker(self):
        wire = bytearray(framed_master(bytes(1300)))
        wire[512] = 0
        parser = MasterCapture()
        self.assertIsNone(parser.feed(wire))
        self.assertEqual("invalid-block-marker", parser.status)

    def test_reject_large_length(self):
        parser = MasterCapture()
        self.assertIsNone(parser.feed(bytes([255, 0, 255, 0]) + (0xffffffff).to_bytes(4, "big")))
        self.assertEqual("invalid-master-header", parser.status)


class LocalForwardingTest(unittest.TestCase):
    def test_loopback_forwards_exact_bytes_and_saves_only_master(self):
        wire = framed_master(bytes(i % 251 for i in range(1400)))
        user_data = b"anonymous-synthetic-js5-request"
        with tempfile.TemporaryDirectory() as temp:
            file = Path(temp) / "capture.bin"
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as upstream:
                upstream.bind(("127.0.0.1", 0))
                upstream.listen(1)
                target_port = upstream.getsockname()[1]
                upstream_received = []

                def fake_upstream():
                    with upstream.accept()[0] as conn:
                        upstream_received.append(conn.recv(65536))
                        conn.sendall(b"\x00")  # RSProt successful JS5 login reply
                        for start in range(0, len(wire), 11):
                            conn.sendall(wire[start:start+11])
                        conn.shutdown(socket.SHUT_WR)

                origin = threading.Thread(target=fake_upstream, daemon=True)
                origin.start()
                ready = threading.Event()
                local_ports = []
                result = []

                def run_proxy():
                    result.append(proxy_one_session(
                        0, "127.0.0.1", target_port, file,
                        ready=ready, bound_port=local_ports))

                relay = threading.Thread(target=run_proxy, daemon=True)
                relay.start()
                self.assertTrue(ready.wait(5), "proxy did not bind")
                with socket.create_connection(("127.0.0.1", local_ports[0]), timeout=5) as client:
                    client.sendall(user_data)
                    forwarded = bytearray()
                    while True:
                        chunk = client.recv(4096)
                        if not chunk:
                            break
                        forwarded.extend(chunk)
                relay.join(5)
                origin.join(5)
                self.assertFalse(relay.is_alive())
                self.assertFalse(origin.is_alive())
                self.assertEqual(user_data, upstream_received[0])
                self.assertEqual(b"\x00" + wire, bytes(forwarded))
                self.assertEqual(wire, file.read_bytes())
                self.assertEqual([("captured", True)], result)
                meta = json.loads((Path(str(file) + ".provenance.json")).read_text())
                self.assertEqual(hashlib.sha256(wire).hexdigest(), meta["wire_sha256"])
                self.assertEqual("NOT_VERIFIED", meta["signature_validity"])


if __name__ == "__main__":
    unittest.main()
