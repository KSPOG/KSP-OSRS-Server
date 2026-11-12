#!/usr/bin/env python3
"""Credential-free native JS5 endpoint test inside a network namespace.

The caller MUST run this script with only loopback networking available.
It serves a reviewed local jav_config, captures an opcode-15 handshake, and
runs the actual injected RuneLite client without entering login details.
A handshake proves only endpoint selection, not cache/gameplay readiness.
"""
from __future__ import annotations

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
        self.request.settimeout(10)
        data = bytearray()
        try:
            while len(data) < 21:
                packet = self.request.recv(21 - len(data))
                if not packet:
                    break
                data.extend(packet)
        except OSError:
            pass
        if len(data) >= 5 and data[0] == 15:
            revision = struct.unpack(">I", data[1:5])[0]
            print("KSP_JS5_NATIVE_HANDSHAKE revision=" + str(revision), flush=True)
            if revision == 242 and len(data) == 21:
                self.server.handshake_accepted.set()
                self.request.sendall(b"\x00")
                print("KSP_JS5_NATIVE_HANDSHAKE_ACCEPTED revision=242", flush=True)
            else:
                print("KSP_JS5_NATIVE_HANDSHAKE_MISMATCH", flush=True)
        else:
            print("KSP_JS5_NATIVE_FIRST_PACKET_NOT_HANDSHAKE", flush=True)


def main() -> int:
    verify_network_namespace()
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
                native_ok = js5_server.handshake_accepted.is_set()
                crash = 'Cannot read field "al" because "rd.gr" is null' in captured
                print(
                    "KSP_JS5_NATIVE_ACCEPTANCE configFetched=" + str(config_ok).lower()
                    + " handshake242=" + str(native_ok).lower()
                    + " priorNpe=" + str(crash).lower(),
                    flush=True,
                )
                if not config_ok or crash or not native_ok:
                    return 1
                return 0


if __name__ == "__main__":
    raise SystemExit(main())
