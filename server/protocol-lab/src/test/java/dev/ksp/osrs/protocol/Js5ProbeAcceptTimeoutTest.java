package dev.ksp.osrs.protocol;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** No real client is required to verify that an empty listen window is expected. */
public final class Js5ProbeAcceptTimeoutTest {
    private Js5ProbeAcceptTimeoutTest() {}

    public static void main(String[] args) throws Exception {
        Path cache = Js5HandshakeDiagnosticTest.fixture();
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0)) {
            long start = System.nanoTime();
            var result = Js5HandshakeDiagnosticServer.serveOne(listener, cache, null, 125);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (result.handshakeAccepted() || result.requests() != 0
                || result.responses() != 0 || result.responseBytes() != 0
                || !"NO_CLIENT_CONNECTED".equals(result.termination()))
                throw new AssertionError("Missing client not reported safely: " + result);
            if (elapsedMs > 4000)
                throw new AssertionError("Short listen deadline unexpectedly exceeded: " + elapsedMs);
            System.out.println("Js5ProbeAcceptTimeoutTest: PASS"
                + " termination=NO_CLIENT_CONNECTED"
                + " gradleExitExpected=0");
        } finally {
            try (var files = Files.walk(cache)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                });
            }
        }
    }
}
