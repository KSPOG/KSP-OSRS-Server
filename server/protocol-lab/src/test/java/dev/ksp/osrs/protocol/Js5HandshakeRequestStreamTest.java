package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Negative tests for the bounded revision-242 post-handshake request stream. */
public final class Js5HandshakeRequestStreamTest {
    private Js5HandshakeRequestStreamTest() {}

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static byte[] hello() {
        return ByteBuffer.allocate(21).put((byte)15).putInt(242)
            .putInt(0).putInt(1).putInt(2).putInt(3).array();
    }

    private static Js5HandshakeDiagnosticServer.Report run(Path cache, byte[] commands,
                                                             String expected, byte[] response)
            throws Exception {
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> Js5HandshakeDiagnosticServer.serveOne(listener, cache));
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(7000);
                var output = client.getOutputStream();
                output.write(hello());
                output.flush();
                check(client.getInputStream().read() == 0, "missing handshake acknowledgment");
                // Write deliberately fragmented requests to exercise readFully.
                for (int i = 0; i < commands.length; i++) output.write(commands, i, 1);
                output.flush();
                client.shutdownOutput();
                if (response != null) {
                    byte[] actual = client.getInputStream().readNBytes(response.length);
                    check(Arrays.equals(actual, response), "unexpected reference-table bytes");
                }
                check(client.getInputStream().read() == -1, "unexpected response bytes after close");
            }
            var report = future.get(10, TimeUnit.SECONDS);
            check(report.handshakeAccepted(), "handshake unexpectedly rejected: " + expected);
            check(expected.equals(report.termination()),
                "wrong termination: " + expected + " versus " + report.termination());
            executor.shutdown();
            return report;
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Js5HandshakeDiagnosticTest.fixture();
        try {
            byte[] reference;
            try (var provider = new VerifiedJs5GroupProvider(root)) {
                ByteBuf b = provider.provide(255, 0);
                check(b != null, "reference absent");
                try {
                    reference = new byte[b.readableBytes()];
                    b.readBytes(reference);
                } finally { b.release(); }
            }
            var control = run(root, new byte[] {
                2, 0, 0, 0, // priority high
                3, 0, 0, 0, // priority low
                4, 0, 0, 0, // zero XOR key
                0, (byte)255, 0, 0 // prefetch reference table
            }, "CLIENT_CLOSED", reference);
            check(control.requests() == 4 && control.responses() == 1
                && control.responseBytes() == reference.length, "control flow incorrect");

            var unknown = run(root, new byte[] {99, 0, 0, 0}, "UNKNOWN_REQUEST", null);
            check(unknown.requests() == 1 && unknown.responses() == 0, "unknown opcode accepted");

            byte[] encrypted = reference.clone();
            for (int i = 0; i < encrypted.length; i++)
                encrypted[i] = (byte)(encrypted[i] ^ 0x5a);
            var xor = run(root, new byte[] {
                4, 0x5a, 0x12, 0x34, // key=0x5A; trailing bytes ignored by RSProt
                1, (byte)255, 0, 0  // urgent reference fetch
            }, "CLIENT_CLOSED", encrypted);
            check(xor.requests() == 2 && xor.responses() == 1
                && xor.responseBytes() == reference.length, "nonzero XOR response incorrect");
            var xorReset = run(root, new byte[] {
                4, 0x5a, 0, 0,
                4, 0, (byte)255, (byte)255, // reset to zero; ignored trailing bytes
                1, (byte)255, 0, 0
            }, "CLIENT_CLOSED", reference);
            check(xorReset.requests() == 3 && xorReset.responses() == 1,
                "XOR reset did not restore unencrypted responses");

            var missing = run(root, new byte[] {1, 0, 0, 0}, "GROUP_UNAVAILABLE", null);
            check(missing.requests() == 1 && missing.responses() == 0, "unavailable group accepted");

            var partial = run(root, new byte[] {1, (byte)255}, "PARTIAL_REQUEST", null);
            check(partial.requests() == 0, "partial packet counted as complete");

            byte[] limit = new byte[32 * 4];
            for (int i = 0; i < 32; i++) {
                limit[i * 4] = 2; // harmless priority high
            }
            var capped = run(root, limit, "REQUEST_LIMIT", null);
            check(capped.requests() == 32 && capped.responses() == 0, "request budget bypassed");

            System.out.println("Js5HandshakeRequestStreamTest: PASS");
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                });
            }
        }
    }
}
