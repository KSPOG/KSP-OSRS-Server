package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end local-cache admission and loopback socket verification.
 * No actual game client, public listener, authentication or world login.
 */
public final class Js5VerifiedMasterProbeAudit {
    private Js5VerifiedMasterProbeAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: <cache-dir> <OpenRS2-255-255.dat>");
        Path cache = Path.of(args[0]);
        byte[] independent = Js5VerifiedMasterProbe.admit(cache, Path.of(args[1]));
        byte[] expectedMaster;
        ByteBuf master = Js5WireFraming.prepare(255, 255, independent);
        try {
            expectedMaster = new byte[master.readableBytes()];
            master.readBytes(expectedMaster);
        } finally { master.release(); }

        byte[] expectedReference;
        try (var provider = new VerifiedJs5GroupProvider(cache)) {
            ByteBuf reference = provider.provide(255, 24);
            if (reference == null)
                throw new AssertionError("Expected real-cache reference 255/24 missing");
            try {
                expectedReference = new byte[reference.readableBytes()];
                reference.readBytes(expectedReference);
            } finally { reference.release(); }
        }
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var pool = Executors.newSingleThreadExecutor()) {
            var server = pool.submit(
                () -> Js5HandshakeDiagnosticServer.serveOne(listener, cache, independent));
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(7000);
                var output = client.getOutputStream();
                var input = client.getInputStream();
                output.write(ByteBuffer.allocate(21).put((byte)15).putInt(242)
                    .putInt(0).putInt(1).putInt(2).putInt(3).array());
                output.flush();
                if (input.read() != 0) throw new AssertionError("JS5 ack missing");

                output.write(new byte[]{1, (byte)255, 0, (byte)255});
                output.flush();
                byte[] actualMaster = input.readNBytes(expectedMaster.length);
                if (!Arrays.equals(actualMaster, expectedMaster))
                    throw new AssertionError("External master response failed byte-exact transport");

                output.write(new byte[]{4, 0x5a, 0, 0});
                output.write(new byte[]{1, (byte)255, 0, 24});
                output.flush();
                byte[] response = input.readNBytes(expectedReference.length);
                Js5ResponseXor.apply(response, 0x5a);
                if (!Arrays.equals(response, expectedReference))
                    throw new AssertionError("Real reference 255/24 XOR response mismatch");
                client.shutdownOutput();
            }
            var report = server.get(10, TimeUnit.SECONDS);
            if (!report.handshakeAccepted() || report.requests() != 3
                || report.responses() != 2 || report.responseBytes() !=
                    (long)expectedMaster.length + expectedReference.length
                || !"CLIENT_CLOSED".equals(report.termination()))
                throw new AssertionError("Unexpected server report: " + report);
            pool.shutdown();
            System.out.println("Verified OpenRS2 master localhost probe: PASS"
                + " masterWireBytes=" + expectedMaster.length
                + " refWireBytes=" + expectedReference.length
                + " sha256=" + Js5VerifiedMasterProbe.OPENRS2_CACHE_2757_MASTER_SHA256);
            System.out.println("Controlled game client acceptance: UNVERIFIED");
        }
    }
}
