#!/usr/bin/env python3
"""Credential-free native JS5 endpoint test inside a network namespace.

The caller MUST run this script with only loopback networking available.
It serves a reviewed local jav_config, captures an opcode-15 handshake, and
runs the actual injected RuneLite client without entering login details.
A handshake proves only endpoint selection, not cache/gameplay readiness.
"""
from __future__ import annotations

import hashlib
import http.server
import os
from pathlib import Path
import re
import socket
import socketserver
import struct
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Microbot-main"
CONFIG = CLIENT / "runelite-client/src/test/resources/net/runelite/client/rs/jav_config.local.js5probe.ws"
JS5_PORT = 43594
CONFIG_PORT = 43595
MASTER_SHA256 = "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38"


def load_pinned_master() -> bytes:
    source = os.environ.get("KSP_JS5_MASTER_FILE")
    if not source:
        raise RuntimeError("No offline pinned master file was supplied")
    master = Path(source).read_bytes()
    if len(master) != 205 or hashlib.sha256(master).hexdigest() != MASTER_SHA256:
        raise RuntimeError("Archived OpenRS2 2757 master does not match the pinned revision-242 reference")
    if master[:5] != bytes((0, 0, 0, 0, 200)):
        raise RuntimeError("Unexpected archived master compression/length header")
    return master


def read_packet(connection: socket.socket, size: int) -> bytes:
    received = bytearray()
    while len(received) < size:
        fragment = connection.recv(size - len(received))
        if not fragment:
            break
        received.extend(fragment)
    return bytes(received)



def verify_network_namespace() -> None:
    # In the dedicated network namespace this must fail at the OS level.
    with socket.socket() as sock:
        sock.settimeout(0.5)
        try:
            sock.connect(("1.1.1.1", 443))
        except OSError:
            print("KSP_JS5_OS_EGRESS_DENIED nonlocalTcp=true", flush=True)
        else:
            raise RuntimeError("Unsafe probe: nonloopback socket unexpectedly connected")


class ConfigHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path != "/jav_config.ws":
            self.send_error(404)
            return
        body = self.server.config_data
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.server.config_served.set()

    def log_message(self, fmt: str, *args: object) -> None:
        # No request parameters or other client metadata are printed.
        return


class Js5Handler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        self.request.settimeout(7)
        try:
            data = read_packet(self.request, 21)
            if len(data) != 21 or data[0] != 15:
                print("KSP_JS5_NATIVE_FIRST_PACKET_NOT_HANDSHAKE", flush=True)
                return
            revision = struct.unpack(">I", data[1:5])[0]
            print("KSP_JS5_NATIVE_HANDSHAKE revision=" + str(revision), flush=True)
            if revision != 242:
                print("KSP_JS5_NATIVE_HANDSHAKE_MISMATCH", flush=True)
                return
            self.server.handshake_accepted.set()
            self.request.sendall(b"\x00")
            print("KSP_JS5_NATIVE_HANDSHAKE_ACCEPTED revision=242", flush=True)
            xor_key = 0
            for ordinal in range(1, 65):
                message = read_packet(self.request, 4)
                if len(message) != 4:
                    return
                opcode, archive, group_high, group_low = message
                group = (group_high << 8) | group_low
                if ordinal <= 12:
                    print("KSP_JS5_NATIVE_REQUEST ordinal=" + str(ordinal)
                          + " opcode=" + str(opcode)
                          + " archive=" + str(archive)
                          + " group=" + str(group), flush=True)
                self.server.request_observed.set()
                if opcode in (2, 3):
                    continue
                if opcode == 4:
                    xor_key = archive
                    continue
                if opcode not in (0, 1):
                    print("KSP_JS5_NATIVE_UNKNOWN_OPCODE", flush=True)
                    return
                if archive != 255 or group != 255:
                    print("KSP_JS5_NATIVE_GROUP_UNAVAILABLE archive="
                          + str(archive) + " group=" + str(group), flush=True)
                    return
                frame = bytes((255, 0, 255)) + self.server.master_data
                if xor_key:
                    frame = bytes(value ^ xor_key for value in frame)
                self.request.sendall(frame)
                self.server.master_delivered.set()
                print("KSP_JS5_NATIVE_PINNED_MASTER_DELIVERED bytes="
                      + str(len(frame)), flush=True)
        except (OSError, TimeoutError):
            return


def main() -> int:
    verify_network_namespace()
    master = load_pinned_master()
    body = CONFIG.read_bytes()
    if b"codebase=http://127.0.0.1:43594/" not in body:
        raise RuntimeError("The local fixture is missing loopback codebase")
    if re.search(rb"(?:runescape|jagex)\.(?:com|net)", body, flags=re.IGNORECASE):
        raise RuntimeError("The local fixture includes an official domain")
    if len(re.findall(rb"(?m)^param=\d+=", body)) < 18:
        raise RuntimeError("The fixture does not contain historical applet parameters")
    with http.server.HTTPServer(("127.0.0.1", CONFIG_PORT), ConfigHandler) as config_server:
        config_server.config_data = body
        config_server.config_served = threading.Event()
        with socketserver.TCPServer(("127.0.0.1", JS5_PORT), Js5Handler) as js5_server:
            js5_server.handshake_accepted = threading.Event()
            js5_server.request_observed = threading.Event()
            js5_server.master_delivered = threading.Event()
            js5_server.master_data = master
            threading.Thread(target=config_server.serve_forever, daemon=True).start()
            threading.Thread(target=js5_server.serve_forever, daemon=True).start()
            print("KSP_JS5_ISOLATED_LISTENERS_READY ports=43594,43595", flush=True)
            with tempfile.TemporaryDirectory(prefix="ksp-js5-ci-") as isolated_home:
                cmd = [
                    "./gradlew", ":client:runKspJs5GamepackProbe",
                    "-PkspJs5ProbePort=43594",
                    "-PkspJs5ProbeHome=" + isolated_home,
                    "-PkspJs5LocalConfigUrl=http://127.0.0.1:43595/jav_config.ws",
                    "--offline", "--no-daemon", "--console=plain"
                ]
                env = os.environ.copy()
                env["KSP_OSRS_PRIVATE_SERVER"] = "false"
                try:
                    completed = subprocess.run(
                        cmd, cwd=CLIENT, env=env, stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT, text=True, timeout=70, check=False
                    )
                    captured = completed.stdout
                    print("KSP_JS5_CLIENT_PROCESS exit=" + str(completed.returncode), flush=True)
                except subprocess.TimeoutExpired as exc:
                    captured = exc.stdout or b""
                    if isinstance(captured, bytes):
                        captured = captured.decode("utf-8", errors="replace")
                    print("KSP_JS5_CLIENT_PROCESS timedOut=true", flush=True)
                # Log only redacted diagnostic prefixes. Never print raw RuneLite
                # lines that might contain account/session identifiers.
                markers = (
                    "KSP_JS5_", "KSP_OSRS_REVISION",
                    "NullPointerException:", "java.lang.SecurityException:"
                )
                for line in captured.splitlines():
                    if any(marker in line for marker in markers):
                        if "KSP_JS5_" in line or "KSP_OSRS_REVISION" in line:
                            print(line[:350], flush=True)
                        elif "NullPointerException:" in line:
                            print("KSP_JS5_CLIENT_NPE_OBSERVED", flush=True)
                config_ok = config_server.config_served.is_set()
                # On CI startup failures print only narrowly scoped build
                # diagnostics. The profile contains no account credentials.
                if not config_ok:
                    for line in captured.splitlines()[-50:]:
                        if any(needle in line for needle in (
                            "FAILURE:", "What went wrong", "Problem:", "Could not",
                            "Execution failed", "Unrecognized option", "Exception",
                            "Task :", "Error:", "does not exist", "not found"
                        )):
                            redacted = re.sub(r"https?://\S+", "<url>", line)
                            redacted = re.sub(r"\S+@\S+", "<email>", redacted)
                            print("KSP_JS5_LAUNCH_DIAGNOSTIC " + redacted[:300], flush=True)
                native_ok = js5_server.handshake_accepted.is_set()
                requested = js5_server.request_observed.is_set()
                master_ok = js5_server.master_delivered.is_set()
                crash = 'Cannot read field "al" because "rd.gr" is null' in captured
                print(
                    "KSP_JS5_NATIVE_ACCEPTANCE configFetched=" + str(config_ok).lower()
                    + " handshake242=" + str(native_ok).lower()
                    + " firstJs5Request=" + str(requested).lower()
                    + " pinnedMasterDelivered=" + str(master_ok).lower()
                    + " priorNpe=" + str(crash).lower(),
                    flush=True,
                )
                if not config_ok or crash or not native_ok or not requested or not master_ok:
                    return 1
                return 0


if __name__ == "__main__":
    raise SystemExit(main())
