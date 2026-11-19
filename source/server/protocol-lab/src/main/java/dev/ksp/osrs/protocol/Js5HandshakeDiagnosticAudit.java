package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Real-cache loopback integration test of the revision-242 handshake packet.
 * Validates only protocol subset (not actual game client).
 */
public final class Js5HandshakeDiagnosticAudit {
    private Js5HandshakeDiagnosticAudit() {}

    private static byte[] init(int revision) {
        ByteBuffer buffer = ByteBuffer.allocate(21).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte)15).putInt(revision).putInt(1).putInt(2).putInt(3).putInt(4);
        return buffer.array();
    }

    private static Js5HandshakeDiagnosticServer.Report exercise(
            Path cache, int revision, boolean sendRequest, byte[] expected) throws Exception {
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var task = executor.submit(() -> Js5HandshakeDiagnosticServer.serveOne(listener, cache));
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(7000);
                byte[] hello = init(revision);
                client.getOutputStream().write(hello, 0, 5);
                client.getOutputStream().flush();
                client.getOutputStream().write(hello, 5, hello.length - 5);
                client.getOutputStream().flush();
                if (revision == 242) {
                    if (client.getInputStream().read() != 0)
                        throw new AssertionError("Missing successful handshake 0x00 response");
                    if (sendRequest) {
                        client.getOutputStream().write(new byte[] {2, 0, 0, 0}); // priority high
                        client.getOutputStream().write(new byte[] {4, 0, 0, 0}); // XOR zero
                        client.getOutputStream().write(new byte[] {1, (byte)255, 0, 24});
                        client.getOutputStream().flush();
                        byte[] reply = client.getInputStream().readNBytes(expected.length);
                        if (!Arrays.equals(reply, expected))
                            throw new AssertionError("JS5 response after handshake differs");
                        client.getOutputStream().write(new byte[]{1, (byte)255, 0, (byte)255});
                        client.getOutputStream().flush();
                        if (client.getInputStream().read() != -1)
                            throw new AssertionError("Master index must remain blocked");
                    }
                } else {
                    if (client.getInputStream().read() != -1)
                        throw new AssertionError("Incorrect revision accepted");
                }
            }
            var report = task.get(10, TimeUnit.SECONDS);
            executor.shutdown();
            return report;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <cache-directory>");
        Path cache = Path.of(args[0]);
        byte[] expected;
        try (var provider = new VerifiedJs5GroupProvider(cache)) {
            ByteBuf b = provider.provide(255, 24);
            if (b == null) throw new AssertionError("Missing reference group 255/24");
            try {
                expected = new byte[b.readableBytes()];
                b.readBytes(expected);
            } finally { b.release(); }
        }
        var valid = exercise(cache, 242, true, expected);
        if (!valid.handshakeAccepted() || valid.requests() != 4
            || valid.responses() != 1 || valid.responseBytes() != expected.length
            || !"MASTER_NOT_VERIFIED".equals(valid.termination()))
            throw new AssertionError("Incorrect successful handshake report " + valid);
        var invalid = exercise(cache, 241, false, expected);
        if (invalid.handshakeAccepted() || !"REVISION_MISMATCH".equals(invalid.termination()))
            throw new AssertionError("Incorrect revision rejection report " + invalid);
        System.out.println("Revision-242 JS5 handshake loopback: PASS opcode=15"
            + " acknowledged=0x00 byteExactResponse=" + expected.length
            + " wrongRevision=REJECTED master255/255=BLOCKED");
        System.out.println("Real client compatibility: UNVERIFIED");
    }
}
