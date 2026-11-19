package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loopback integration test using a real, validated reference-table container.
 * Does not exercise OSRS handshake/client compatibility.
 */
public final class Js5LocalDiagnosticAudit {
    private Js5LocalDiagnosticAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <cache>");
        Path cache = Path.of(args[0]);
        byte[] expected;
        try (var provider = new VerifiedJs5GroupProvider(cache)) {
            ByteBuf b = provider.provide(255, 24);
            if (b == null) throw new AssertionError("Expected archive 24 reference table");
            try {
                expected = new byte[b.readableBytes()];
                b.readBytes(expected);
            } finally {
                b.release();
            }
        }
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            int port = listener.getLocalPort();
            var task = executor.submit(() -> Js5LocalDiagnosticServer.serveOne(listener, cache));
            try (Socket client = new Socket("127.0.0.1", port)) {
                client.setSoTimeout(7000);
                // Request real reference table 255/24; then request 255/255
                // to prove the server refuses the unsupported master index.
                client.getOutputStream().write(new byte[]{1, (byte) 255, 0, 24});
                byte[] actual = client.getInputStream().readNBytes(expected.length);
                if (!Arrays.equals(expected, actual))
                    throw new AssertionError("Socket JS5 response differs from verified provider");
                client.getOutputStream().write(new byte[]{1, (byte)255, 0, (byte)255});
                client.getOutputStream().flush();
                int end = client.getInputStream().read();
                if (end != -1)
                    throw new AssertionError("Master-index request unexpectedly received bytes");
            }
            var report = task.get(10, TimeUnit.SECONDS);
            if (report.requests() != 2 || report.responses() != 1
                || !report.termination().equals("MASTER_NOT_VERIFIED")
                || report.responseBytes() != expected.length)
                throw new AssertionError("Unexpected local diagnostic report: " + report);
            System.out.println("JS5 localhost diagnostic: PASS responseBytes="
                + expected.length + " master255/255=BLOCKED");
            System.out.println("Real revision-242 client acceptance: UNVERIFIED");
            executor.shutdown();
        }
    }
}
