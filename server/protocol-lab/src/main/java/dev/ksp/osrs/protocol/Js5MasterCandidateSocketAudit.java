package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.EOFException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Synthetic-only master candidate socket test; does not establish an
 * independent fixture, signature validity, or real-client acceptance.
 */
public final class Js5MasterCandidateSocketAudit {
    private Js5MasterCandidateSocketAudit() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: <cache>");
        Path cache = Path.of(args[0]);
        byte[] candidate = Js5MasterIndexCandidate.build(cache).container();
        ByteBuf framed = Js5WireFraming.prepare(255, 255, candidate);
        byte[] expected;
        try {
            expected = new byte[framed.readableBytes()];
            framed.readBytes(expected);
        } finally {
            framed.release();
        }

        try (ServerSocket listener = Js5LocalDiagnosticServer.bind(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(
                () -> Js5LocalDiagnosticServer.serveOne(listener, cache, true));
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(7000);
                client.getOutputStream().write(new byte[] {1, (byte)255, 0, (byte)255});
                client.getOutputStream().flush();
                byte[] actual = client.getInputStream().readNBytes(expected.length);
                if (!Arrays.equals(actual, expected))
                    throw new AssertionError("Synthetic master bytes differ over loopback TCP");
                var unframed = Js5MasterWireFixtureAudit.decode(actual);
                if (!Arrays.equals(candidate, unframed.container())
                    || unframed.extraBytes() != 0)
                    throw new AssertionError("Unframed synthetic master candidate mismatch");
            }
            var report = future.get(10, TimeUnit.SECONDS);
            if (report.requests() != 1 || report.responses() != 1
                || report.responseBytes() != expected.length
                || !"CLIENT_CLOSED".equals(report.termination()))
                throw new AssertionError("Unexpected synthetic socket result: " + report);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(candidate));
            System.out.println("Synthetic JS5 master candidate socket: PASS"
                + " archive=255 group=255"
                + " containerBytes=" + candidate.length
                + " wireBytes=" + expected.length
                + " sha256=" + sha);
            System.out.println("Independent 255/255 fixture: MISSING");
            System.out.println("Signature and real client acceptance: UNVERIFIED");
            executor.shutdown();
        }
    }
}
