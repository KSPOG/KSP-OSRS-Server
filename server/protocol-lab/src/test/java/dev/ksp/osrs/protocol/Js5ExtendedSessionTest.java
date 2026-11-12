package dev.ksp.osrs.protocol;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Verifies the larger native-client request cap without changing legacy 32-request tests. */
public final class Js5ExtendedSessionTest {
    private Js5ExtendedSessionTest() {}

    private static void check(boolean actual, String failure) {
        if (!actual) throw new AssertionError(failure);
    }

    public static void main(String[] args) throws Exception {
        Path cache = Js5HandshakeDiagnosticTest.fixture();
        try {
            try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
                 var executor = Executors.newSingleThreadExecutor()) {
                var future = executor.submit(() ->
                    Js5HandshakeDiagnosticServer.serveOne(listener, cache, null,
                        7_000, 128, 10_000, s -> {}));
                try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                    client.setSoTimeout(7_000);
                    var out = client.getOutputStream();
                    out.write(ByteBuffer.allocate(21).put((byte)15).putInt(242)
                        .putInt(0).putInt(1).putInt(2).putInt(3).array());
                    out.flush();
                    check(client.getInputStream().read() == 0, "handshake failed");
                    for (int i = 0; i < 100; i++) {
                        out.write(new byte[] {2, 0, 0, 0});
                    }
                    out.flush();
                    client.shutdownOutput();
                }
                var report = future.get(10, TimeUnit.SECONDS);
                check(report.handshakeAccepted() && report.requests() == 100
                    && report.responses() == 0
                    && "CLIENT_CLOSED".equals(report.termination()),
                    "Extended session closed at legacy limit: " + report);
                executor.shutdown();
            }
            try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
                 var executor = Executors.newSingleThreadExecutor()) {
                var future = executor.submit(() ->
                    Js5HandshakeDiagnosticServer.serveOne(listener, cache, null,
                        7_000, 64, 10_000, s -> {}));
                try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                    client.setSoTimeout(7_000);
                    var out = client.getOutputStream();
                    out.write(ByteBuffer.allocate(21).put((byte)15).putInt(242)
                        .putInt(0).putInt(1).putInt(2).putInt(3).array());
                    out.flush();
                    check(client.getInputStream().read() == 0, "handshake failed");
                    for (int i = 0; i < 64; i++) {
                        out.write(new byte[] {2, 0, 0, 0});
                    }
                    out.flush();
                    client.shutdownOutput();
                }
                var report = future.get(10, TimeUnit.SECONDS);
                check(report.requests() == 64 && report.responses() == 0
                    && "REQUEST_LIMIT".equals(report.termination()),
                    "Extended cap did not enforce configured limit: " + report);
                executor.shutdown();
            }
            System.out.println("Js5ExtendedSessionTest: PASS"
                + " over32Requests=100 explicitCap=64");
        } finally {
            try (var stream = Files.walk(cache)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                });
            }
        }
    }
}
