#!/usr/bin/env python3
"""Localhost-only, one-session JS5 255/255 capture-forward proxy.

Python 3.10+, standard library. Intended for a client endpoint the operator
is authorized to redirect to a server endpoint the operator is authorized
to observe. Never alters data or logs client-to-server payloads.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import tempfile
import threading
from datetime import datetime, timezone

MAX_CONTAINER_BYTES = 1024 * 1024


class MasterCapture:
    """Streaming parser for FIRST server response only; no unbounded buffering."""

    def __init__(self, expect_login_success: bool = False) -> None:
        self.expect_login_success = expect_login_success
        self.data = bytearray()
        self.payload_remaining: int | None = None
        self.block_offset = 0
        self.markers = 0
        self.status = "waiting"

    def feed(self, packet: bytes) -> bytes | None:
        if self.status != "waiting":
            return None
        for octet in packet:
            if self.status != "waiting":
                break
            if self.expect_login_success:
                self.expect_login_success = False
                if octet != 0:
                    self.status = "js5-login-not-successful"
                    return None
                continue
            if self.payload_remaining is None:
                self.data.append(octet)
                if len(self.data) == 3 and self.data != b"\xff\x00\xff":
                    self.status = "first-response-not-master"
                    self.data.clear()
                    return None
                if len(self.data) == 8:
                    kind = self.data[3]
                    length = int.from_bytes(self.data[4:8], "big")
                    if kind > 2 or length > MAX_CONTAINER_BYTES:
                        self.status = "invalid-master-header"
                        self.data.clear()
                        return None
                    self.payload_remaining = length + (4 if kind else 0)
                    if self.payload_remaining > MAX_CONTAINER_BYTES:
                        self.status = "master-too-large"
                        self.data.clear()
                        return None
                    self.block_offset = 8
                    if self.payload_remaining == 0:
                        self.status = "captured"
                        return bytes(self.data)
                continue

            if self.block_offset == 512:
                if octet != 255:
                    self.status = "invalid-block-marker"
                    self.data.clear()
                    return None
                self.data.append(octet)
                self.markers += 1
                self.block_offset = 1
                continue
            self.data.append(octet)
            self.block_offset += 1
            self.payload_remaining -= 1
            if self.payload_remaining == 0:
                self.status = "captured"
                return bytes(self.data)
        return None


def save_capture(path: Path, wire: bytes, upstream_host: str,
                 upstream_port: int, markers: int) -> None:
    """Atomic output; no client request bytes or credentials persisted."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise FileExistsError(f"Refusing to overwrite existing capture: {path}")
    fd, tmp = tempfile.mkstemp(prefix=".js5-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(wire)
            out.flush()
            os.fsync(out.fileno())
        if path.exists():
            raise FileExistsError(f"Refusing to overwrite existing capture: {path}")
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    provenance = {
        "captured_utc": datetime.now(timezone.utc).isoformat(),
        "capture_kind": "first_upstream_js5_master_response_255_255",
        "upstream_endpoint": f"{upstream_host}:{upstream_port}",
        "wire_bytes": len(wire),
        "wire_sha256": hashlib.sha256(wire).hexdigest(),
        "continuation_markers": markers,
        "client_revision": "NOT_INFERRED",
        "signature_validity": "NOT_VERIFIED",
    }
    metadata = path.with_name(path.name + ".provenance.json")
    with metadata.open("x", encoding="utf-8") as out:
        json.dump(provenance, out, indent=2)
        out.write("\n")


def proxy_one_session(
    listener_port: int, upstream_host: str, upstream_port: int,
    output: Path, *, ready: threading.Event | None = None,
    bound_port: list[int] | None = None,
) -> tuple[str, bool]:
    """Forward all bytes bidirectionally without transformation or logging."""
    state = MasterCapture(expect_login_success=True)
    written = False
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(("127.0.0.1", listener_port))
        listener.listen(1)
        listener.settimeout(120)
        if bound_port is not None:
            bound_port.append(listener.getsockname()[1])
        if ready is not None:
            ready.set()
        with listener.accept()[0] as client:
            with socket.create_connection((upstream_host, upstream_port), timeout=15) as upstream:
                client.settimeout(None)
                upstream.settimeout(None)
                stop = threading.Event()

                def upload() -> None:
                    try:
                        while not stop.is_set():
                            block = client.recv(65536)
                            if not block:
                                try:
                                    upstream.shutdown(socket.SHUT_WR)
                                except OSError:
                                    pass
                                return
                            upstream.sendall(block)
                    except OSError:
                        pass

                sender = threading.Thread(target=upload, daemon=True)
                sender.start()
                try:
                    while True:
                        block = upstream.recv(65536)
                        if not block:
                            break
                        # Forward first. Capture must never delay/drop response bytes.
                        client.sendall(block)
                        if state.status == "waiting":
                            captured = state.feed(block)
                            if captured is not None:
                                save_capture(output, captured, upstream_host,
                                             upstream_port, state.markers)
                                written = True
                                print(f"CAPTURED {output} ({len(captured)} wire bytes)", flush=True)
                finally:
                    stop.set()
                    try:
                        client.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                    try:
                        upstream.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                    sender.join(timeout=3)
    return state.status, written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-host", required=True,
                        help="Explicitly authorized JS5 upstream host/IP")
    parser.add_argument("--upstream-port", type=int, required=True)
    parser.add_argument("--listen-port", type=int, default=43595)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not (1 <= args.upstream_port <= 65535 and 1 <= args.listen_port <= 65535):
        parser.error("Ports must be between 1 and 65535")
    if args.output.exists():
        parser.error("Capture already exists; choose a new output path")
    if args.output.with_name(args.output.name + ".provenance.json").exists():
        parser.error("Provenance sidecar already exists")
    print(f"Listening ONLY on 127.0.0.1:{args.listen_port}; "
          "forwarding to configured authorized upstream", flush=True)
    print("Client must use this localhost endpoint; otherwise no capture occurs.", flush=True)
    print("Does not change client settings, modify certificates or intercept other traffic.", flush=True)
    try:
        status, written = proxy_one_session(
            args.listen_port, args.upstream_host, args.upstream_port, args.output)
        print(f"Session complete: {status}; capture_written={written}", flush=True)
    except (OSError, FileExistsError) as exc:
        raise SystemExit(f"Capture proxy failed: {exc}") from exc


if __name__ == "__main__":
    main()
