#!/usr/bin/env python3
"""No-login native revision-242 client against the checked full JS5 cache.

MUST be invoked inside a network namespace containing only loopback. Cache
and binaries must have been downloaded/compiled BEFORE isolation. No login,
world service, fallback, external network, or user credentials are available.
"""
from __future__ import annotations

from collections import deque
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import threading
import time

from js5_gamepack_headless_probe import (
    CONFIG,
    CONFIG_PORT,
    ConfigHandler,
    verify_network_namespace,
)
import http.server

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Microbot-main"


class Capture:
    def __init__(self, stream, role: str):
        self.role = role
        self.lines = deque(maxlen=4000)
        self.ready = threading.Event()
        self.finished = threading.Event()
        self._thread = threading.Thread(target=self._read, args=(stream,), daemon=True)
        self._thread.start()

    def _read(self, stream) -> None:
        try:
            for raw in iter(stream.readline, ""):
                line = raw.strip()
                self.lines.append(line)
                if line.startswith("JS5_CONTROLLED_PROBE_READY"):
                    self.ready.set()
        finally:
            self.finished.set()

    def contains(self, phrase: str) -> bool:
        return any(phrase in value for value in self.lines)

    def find(self, phrase: str) -> str | None:
        for value in reversed(self.lines):
            if phrase in value:
                return value
        return None

    def show_safe_log(self) -> None:
        # Only stable diagnostic markers. Never print arbitrary game/client
        # output, local paths, account names, URLs or authentication details.
        for value in self.lines:
            if self.role == "server":
                if (value.startswith(("JS5_CONTROLLED_PROBE_READY",
                                      "JS5_CONTROLLED_PROBE_REPORT",
                                      "JS5_HANDSHAKE_OBSERVED",
                                      "JS5_REQUEST_LIMIT_REACHED",
                                      "JS5_SESSION_TIME_LIMIT"))
                    or re.match(r"^(?:JS5_REQUEST|JS5_RESPONSE) ordinal=", value)):
                    print("KSP_JS5_FULL_SERVER " + value[:320], flush=True)
            else:
                if ("KSP_OSRS_REVISION_READY" in value
                    or "KSP_OSRS_REVISION_SNAPSHOT" in value
                    or "KSP_JS5_LOCAL_CONFIG_ACCEPTED" in value):
                    print("KSP_JS5_FULL_CLIENT " + value.split(" - ", 1)[-1][:320],
                          flush=True)
                elif 'Cannot read field "al" because "rd.gr" is null' in value:
                    print("KSP_JS5_FULL_OLD_NPE_OBSERVED", flush=True)


def stop_group(process: subprocess.Popen) -> None:
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=8)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=4)


def main() -> int:
    verify_network_namespace()
    config = CONFIG.read_bytes()
    if b"codebase=http://127.0.0.1:43594/" not in config:
        raise RuntimeError("Invalid local-only applet fixture")
    home = os.environ.get("HOME", "")
    lib_dir = Path(os.environ["KSP_FULL_JS5_SERVER_LIB"])
    master = Path(os.environ["KSP_FULL_JS5_MASTER"])
    cache_root = Path(os.environ["KSP_FULL_JS5_CACHE"])
    if not (lib_dir.is_dir() and master.is_file()
            and (cache_root / "main_file_cache.idx255").is_file()
            and (cache_root / "main_file_cache.dat2").is_file()):
        raise RuntimeError("Full offline cache/provider prerequisites missing")
    with http.server.HTTPServer(("127.0.0.1", CONFIG_PORT), ConfigHandler) as config_server:
        config_server.config_data = config
        config_server.config_served = threading.Event()
        threading.Thread(target=config_server.serve_forever, daemon=True).start()
        with tempfile.TemporaryDirectory(prefix="ksp-full-js5-") as isolated:
            server_cmd = [
                "java", "-cp", str(lib_dir / "*"),
                "dev.ksp.osrs.protocol.Js5VerifiedMasterProbe",
                str(cache_root), str(master), "43594",
                "--confirm-local-probe", "--extended-client-session",
            ]
            server = subprocess.Popen(
                server_cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1, start_new_session=True
            )
            server_capture = Capture(server.stdout, "server")
            client = None
            try:
                if not server_capture.ready.wait(60):
                    if server.poll() is not None:
                        print("KSP_JS5_FULL_SERVER_STARTUP_FAILED exit="
                              + str(server.returncode), flush=True)
                    else:
                        print("KSP_JS5_FULL_SERVER_STARTUP_TIMEOUT", flush=True)
                    server_capture.show_safe_log()
                    return 1

                print("KSP_JS5_FULL_PROVIDER_READY loopbackOnly=true", flush=True)
                env = os.environ.copy()
                env["KSP_OSRS_PRIVATE_SERVER"] = "false"
                env["HOME"] = home
                client_cmd = [
                    "./gradlew", ":client:runKspJs5GamepackProbe",
                    "-PkspJs5ProbePort=43594",
                    "-PkspJs5ProbeHome=" + isolated,
                    "-PkspJs5LocalConfigUrl=http://127.0.0.1:43595/jav_config.ws",
                    "--offline", "--no-daemon", "--console=plain",
                ]
                client = subprocess.Popen(
                    client_cmd, cwd=CLIENT, env=env,
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, bufsize=1, start_new_session=True
                )
                client_capture = Capture(client.stdout, "client")
                deadline = time.monotonic() + 115
                while time.monotonic() < deadline:
                    if client.poll() is not None:
                        break
                    if server.poll() is not None:
                        # Server exits after its single session; don't keep the
                        # client running when no JS5 groups can be served.
                        break
                    time.sleep(1)
                stop_group(client)
                # Let the Java server detect the disconnected gamepack and
                # emit its final audited request/response report.
                try:
                    server.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    print("KSP_JS5_FULL_SERVER_REPORT_TIMEOUT", flush=True)
                server_capture.show_safe_log()
                client_capture.show_safe_log()
                report = server_capture.find("JS5_CONTROLLED_PROBE_REPORT")
                revision_ready = client_capture.contains("KSP_OSRS_REVISION_READY | revision=242")
                config_ready = config_server.config_served.is_set()
                crash = client_capture.contains('Cannot read field "al" because "rd.gr" is null')
                if not report:
                    print("KSP_JS5_FULL_ACCEPTANCE reportMissing=true"
                          + " revision242=" + str(revision_ready).lower(), flush=True)
                    return 1
                values = dict(re.findall(r"(handshakeAccepted|requests|responses|responseBytes|termination)=([^ ]+)", report))
                accepted = values.get("handshakeAccepted") == "true"
                requests = int(values.get("requests", "0"))
                responses = int(values.get("responses", "0"))
                print("KSP_JS5_FULL_ACCEPTANCE configFetched=" + str(config_ready).lower()
                      + " revision242=" + str(revision_ready).lower()
                      + " handshake242=" + str(accepted).lower()
                      + " requests=" + str(requests)
                      + " responses=" + str(responses)
                      + " priorNpe=" + str(crash).lower()
                      + " login=NOT_TESTED", flush=True)
                return 0 if (config_ready and revision_ready and accepted
                             and requests >= 10 and responses >= 10 and not crash) else 1
            finally:
                if client is not None:
                    stop_group(client)
                stop_group(server)


if __name__ == "__main__":
    raise SystemExit(main())
