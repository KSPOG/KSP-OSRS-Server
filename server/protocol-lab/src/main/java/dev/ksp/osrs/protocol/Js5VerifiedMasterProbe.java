package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Opt-in, single-session localhost-only revision-242 JS5 probe.
 *
 * The master group is admitted ONLY if a regular local copy of the independently
 * archived OpenRS2 cache 2757 group 255/255 matches both the fixed SHA-256 pin
 * and the candidate derived directly from the checked local cache.
 *
 * This is NOT an authenticated production game server, client signature
 * validation, world-login handler, or external network listener.
 */
public final class Js5VerifiedMasterProbe {
    public static final String OPENRS2_CACHE_2757_MASTER_SHA256 =
        "a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38";
    private static final int MAX_MASTER_BYTES = 1024 * 1024;
    private Js5VerifiedMasterProbe() {}

    public static byte[] admit(Path cache, Path archivedMaster) throws Exception {
        if (!Files.isRegularFile(archivedMaster) || Files.isSymbolicLink(archivedMaster))
            throw new IOException("Master reference must be a regular file, not a symlink");
        long len = Files.size(archivedMaster);
        if (len != 205 || len > MAX_MASTER_BYTES)
            throw new IOException("Revision-242 archived master must be exactly 205 bytes");
        byte[] data = Files.readAllBytes(archivedMaster);
        String sha256 = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(data));
        if (!MessageDigest.isEqual(sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                OPENRS2_CACHE_2757_MASTER_SHA256.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Archived 255/255 SHA-256 does not match pinned OpenRS2 cache 2757");
        byte[] candidate = Js5MasterIndexCandidate.build(cache).container();
        if (!Arrays.equals(candidate, data))
            throw new IOException("Archived 255/255 differs from the provided local cache");
        if (data[0] != 0 || java.nio.ByteBuffer.wrap(data, 1, 4).getInt() != 200)
            throw new IOException("Unexpected revision-242 master-index structure");
        return data;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !"--confirm-local-probe".equals(args[3])) {
            System.err.println("Usage: Js5VerifiedMasterProbe <cache-dir> <archived-255-255.dat> <port> --confirm-local-probe");
            System.exit(2);
        }
        Path cache = Path.of(args[0]);
        Path master = Path.of(args[1]);
        int port = Integer.parseInt(args[2]);
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("Port must be 1..65535");
        // Admission completes BEFORE any TCP port is opened.
        byte[] verifiedMaster = admit(cache, master);
        try (ServerSocket server = Js5LocalDiagnosticServer.bind(port)) {
            System.out.println("JS5_CONTROLLED_PROBE_READY address=127.0.0.1 port=" + server.getLocalPort());
            System.out.println("master_sha256=" + OPENRS2_CACHE_2757_MASTER_SHA256
                + " master_bytes=" + verifiedMaster.length);
            System.out.println("one_session=true timeout=7s max_requests=32");
            System.out.println("real_revision_242_client_acceptance=NOT_YET_VERIFIED");
            Js5HandshakeDiagnosticServer.Report report =
                Js5HandshakeDiagnosticServer.serveOne(server, cache, verifiedMaster);
            System.out.println("JS5_CONTROLLED_PROBE_REPORT handshakeAccepted="
                + report.handshakeAccepted() + " requests=" + report.requests()
                + " responses=" + report.responses()
                + " responseBytes=" + report.responseBytes()
                + " termination=" + report.termination());
            System.out.println("Client signature acceptance, account login and game world: UNVERIFIED");
        }
    }
}
