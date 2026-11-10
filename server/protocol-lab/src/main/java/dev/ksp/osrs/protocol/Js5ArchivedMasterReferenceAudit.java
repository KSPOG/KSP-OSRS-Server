package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Independent raw (255,255) JS5 container comparison against OpenRS2 archive.
 *
 * This class does NOT manufacture a reference or infer a client signature.
 * It accepts a downloaded external cache-group container (no 3-byte JS5
 * network prefix); the source and revision must be established separately.
 */
public final class Js5ArchivedMasterReferenceAudit {
    private static final int MAX_BYTES = 1_048_576;

    private Js5ArchivedMasterReferenceAudit() {}

    public record Report(String sha256, int referenceBytes, int candidateBytes,
                         int compressionType, int declaredPayloadBytes, int extraBytes,
                         boolean exact, int firstDifference, int comparedEntries,
                         int crcDifferences, int versionDifferences, String format) {}

    public static Report inspect(Path cache, byte[] archive) throws Exception {
        if (archive == null || archive.length < 5 || archive.length > MAX_BYTES)
            throw new IOException("Archive master file is empty, invalid or oversized");
        int compression = archive[0] & 255;
        if (compression > 2) throw new IOException("Unknown JS5 compression type");
        long packed = Integer.toUnsignedLong(ByteBuffer.wrap(archive, 1, 4).getInt());
        long expected = (compression == 0 ? 5L : 9L) + packed;
        if (expected > MAX_BYTES || expected > archive.length)
            throw new IOException("JS5 master container truncated/oversized");
        if (compression != 0 && archive.length < 9)
            throw new IOException("Compressed master lacks unpacked-length field");

        byte[] candidate = Js5MasterIndexCandidate.build(cache).container();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        int first = -1;
        for (int i = 0; i < Math.min(candidate.length, archive.length); i++) {
            if (candidate[i] != archive[i]) { first = i; break; }
        }
        if (first < 0 && candidate.length != archive.length)
            first = Math.min(candidate.length, archive.length);
        int compared = 0, crcDifferences = 0, versionDifferences = 0;
        String format = "Non-classic or extended format; field interpretation unsupported";
        if (compression == 0 && packed % 8 == 0
            && expected == candidate.length
            && candidate.length == 5 + packed) {
            // Compare only the advertised classic payload; an appended
            // signature/extension is reported, never treated as validated.
            compared = (int)packed / 8;
            ByteBuffer actual = ByteBuffer.wrap(archive).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer local = ByteBuffer.wrap(candidate).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < compared; i++) {
                if (actual.getInt(5 + 8*i) != local.getInt(5 + 8*i)) crcDifferences++;
                if (actual.getInt(9 + 8*i) != local.getInt(9 + 8*i)) versionDifferences++;
            }
            format = archive.length > expected
                ? "Classic VERSIONED prefix plus trailing bytes (NOT signature-verified)"
                : "Exact classic VERSIONED container shape";
        }
        return new Report(hash, archive.length, candidate.length, compression,
            (int)packed, (int)(archive.length - expected), first < 0, first,
            compared, crcDifferences, versionDifferences, format);
    }

    public static Report inspectFile(Path cache, Path archive) throws Exception {
        if (!Files.isRegularFile(archive) || Files.isSymbolicLink(archive)
            || Files.size(archive) < 5 || Files.size(archive) > MAX_BYTES)
            throw new IOException("External master fixture must be a regular file <= 1MiB");
        return inspect(cache, Files.readAllBytes(archive));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: Js5ArchivedMasterReferenceAudit <cache> <external-255-255.dat>");
            System.exit(2);
        }
        Report r = inspectFile(Path.of(args[0]), Path.of(args[1]));
        System.out.println("External JS5 255/255 container SHA-256: " + r.sha256());
        System.out.println("External container: bytes=" + r.referenceBytes()
            + " compression=" + r.compressionType()
            + " advertisedPackedBytes=" + r.declaredPayloadBytes()
            + " trailingBytes=" + r.extraBytes());
        System.out.println("Local candidate: bytes=" + r.candidateBytes()
            + " exact=" + r.exact() + " firstDifference=" + r.firstDifference());
        System.out.println("Classic metadata fields: compared=" + r.comparedEntries()
            + " crcDifferences=" + r.crcDifferences()
            + " versionDifferences=" + r.versionDifferences());
        System.out.println("Format: " + r.format());
        System.out.println("External source identity and signature validity: NOT PROVEN BY THIS FILE");
        System.out.println("Actual revision-242 client acceptance: UNVERIFIED");
        if (r.extraBytes() > 0 || !r.exact()) System.exit(3);
    }
}
