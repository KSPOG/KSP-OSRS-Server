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

/** Synthetic-socket tests; NEVER claim a synthetic master passed external admission. */
public final class Js5VerifiedMasterProbeTest {
    private Js5VerifiedMasterProbeTest() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] framed(int archive, int group, byte[] payload) throws Exception {
        ByteBuf b = Js5WireFraming.prepare(archive, group, payload);
        try {
            byte[] out = new byte[b.readableBytes()];
            b.readBytes(out);
            return out;
        } finally { b.release(); }
    }

    private static void syntheticRoundTrip(Path cache) throws Exception {
        byte[] container = Js5MasterIndexCandidate.build(cache).container();
        byte[] masterWire = framed(255, 255, container);
        byte[] referenceWire;
        try (var provider = new VerifiedJs5GroupProvider(cache)) {
            ByteBuf b = provider.provide(255, 0);
            check(b != null, "reference group unavailable");
            try {
                referenceWire = new byte[b.readableBytes()];
                b.readBytes(referenceWire);
            } finally { b.release(); }
        }
        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            // Package-private override is exercised with a SYNTHETIC
            // master without ever invoking externally pinned admission.
            var task = executor.submit(() ->
                Js5HandshakeDiagnosticServer.serveOne(listener, cache, container));
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(7000);
                var out = client.getOutputStream();
                var in = client.getInputStream();
                byte[] hello = ByteBuffer.allocate(21).put((byte)15).putInt(242)
                    .putInt(0).putInt(1).putInt(2).putInt(3).array();
                out.write(hello);
                out.flush();
                check(in.read() == 0, "handshake response missing");
                out.write(new byte[]{1, (byte)255, 0, (byte)255});
                out.flush();
                check(Arrays.equals(in.readNBytes(masterWire.length), masterWire),
                    "master response differs from candidate");
                out.write(new byte[]{4, 0x5a, 0, 0});
                out.write(new byte[]{1, (byte)255, 0, 0});
                out.flush();
                byte[] encrypted = in.readNBytes(referenceWire.length);
                Js5ResponseXor.apply(encrypted, 0x5a);
                check(Arrays.equals(encrypted, referenceWire),
                    "reference response XOR or framing mismatch");
                client.shutdownOutput();
                check(in.read() == -1, "unexpected response after requests");
            }
            var result = task.get(10, TimeUnit.SECONDS);
            check(result.handshakeAccepted() && result.requests() == 3
                && result.responses() == 2 && result.termination().equals("CLIENT_CLOSED")
                && result.responseBytes() == masterWire.length + referenceWire.length,
                "unexpected synthetic connection report " + result);
            executor.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Js5HandshakeDiagnosticTest.fixture();
        Path syntheticFixture = Files.createTempFile("js5-unsigned-master-", ".dat");
        try {
            byte[] candidate = Js5MasterIndexCandidate.build(root).container();
            Files.write(syntheticFixture, candidate);
            boolean denied = false;
            try { Js5VerifiedMasterProbe.admit(root, syntheticFixture); }
            catch (java.io.IOException expected) { denied = true; }
            check(denied, "Synthetic master incorrectly passed OpenRS2 pinned admission");
            syntheticRoundTrip(root);
            System.out.println("Js5VerifiedMasterProbeTest: PASS"
                + " admissionWrongPin=REJECTED"
                + " syntheticMasterSocket=PASS"
                + " xorReferenceAfterMaster=PASS");
            System.out.println("Real client acceptance: UNVERIFIED");
        } finally {
            Files.deleteIfExists(syntheticFixture);
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                });
            }
        }
    }
}
