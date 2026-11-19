package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Offline, byte-exact comparison to an independently captured master-index container.
 * No network IO, no signature inference and no client-compatibility certification.
 */
public final class Js5MasterReferenceComparator {
    private static final int MAX_FIXTURE_BYTES = 1024 * 1024;
    private Js5MasterReferenceComparator() {}

    public record Report(boolean exactMatch, int candidateBytes, int referenceBytes,
                         int firstDifference, int comparedEntries, int crcDifferences,
                         int versionDifferences, String referenceSha256, String formatNote) {}

    public static Report compare(byte[] candidate, byte[] reference, String pinnedSha256)
            throws IOException {
        if (candidate == null || reference == null || candidate.length == 0
                || reference.length == 0 || candidate.length > MAX_FIXTURE_BYTES
                || reference.length > MAX_FIXTURE_BYTES)
            throw new IOException("Empty or oversized master-index container");
        if (pinnedSha256 == null || !pinnedSha256.matches("(?i)[a-f0-9]{64}"))
            throw new IOException("A 64-digit SHA-256 fixture pin is required");
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reference));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        if (!MessageDigest.isEqual(digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                pinnedSha256.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Reference fixture SHA-256 mismatch");
        int first = -1;
        for (int i = 0; i < Math.min(candidate.length, reference.length); i++) {
            if (candidate[i] != reference[i]) { first = i; break; }
        }
        if (first < 0 && candidate.length != reference.length)
            first = Math.min(candidate.length, reference.length);
        boolean exact = first < 0;

        int compared = 0, crcs = 0, versions = 0;
        String note;
        if (isClassicVersionedContainer(candidate) && isClassicVersionedContainer(reference)) {
            ByteBuffer a = ByteBuffer.wrap(candidate).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer b = ByteBuffer.wrap(reference).order(ByteOrder.BIG_ENDIAN);
            int ac = a.getInt(1) / 8, bc = b.getInt(1) / 8;
            compared = Math.min(ac, bc);
            for (int i = 0; i < compared; i++) {
                if (a.getInt(5 + i * 8) != b.getInt(5 + i * 8)) crcs++;
                if (a.getInt(9 + i * 8) != b.getInt(9 + i * 8)) versions++;
            }
            note = ac == bc ? "Both containers have classic VERSIONED shape (not proof of revision-242 acceptance)"
                    : "Classic VERSIONED entry counts differ: candidate=" + ac + " reference=" + bc;
        } else {
            note = "At least one container is not classic uncompressed VERSIONED; entry fields not interpreted";
        }
        return new Report(exact, candidate.length, reference.length, first,
                compared, crcs, versions, digest, note);
    }

    private static boolean isClassicVersionedContainer(byte[] bytes) {
        if (bytes.length < 5 || bytes[0] != 0) return false;
        long len = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 1, 4).getInt());
        return len % 8 == 0 && len == bytes.length - 5;
    }

    public static Report comparePinnedFile(byte[] candidate, Path fixture, String sha256)
            throws IOException {
        if (!Files.isRegularFile(fixture) || Files.isSymbolicLink(fixture)
                || Files.size(fixture) < 1 || Files.size(fixture) > MAX_FIXTURE_BYTES)
            throw new IOException("Reference must be a regular, bounded, non-symlink file");
        return compare(candidate, Files.readAllBytes(fixture), sha256);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: Js5MasterReferenceComparator <cache-dir> <independent-master-fixture> <fixture-sha256>");
            System.exit(2);
        }
        byte[] candidate = Js5MasterIndexCandidate.build(Path.of(args[0])).container();
        Report r = comparePinnedFile(candidate, Path.of(args[1]), args[2]);
        System.out.println("Fixture SHA-256: " + r.referenceSha256());
        System.out.println("Master comparison: exact=" + r.exactMatch() + " candidateBytes=" + r.candidateBytes()
                + " referenceBytes=" + r.referenceBytes() + " firstDifference=" + r.firstDifference());
        System.out.println("Entry comparison: inspected=" + r.comparedEntries() + " crcDifferences="
                + r.crcDifferences() + " versionDifferences=" + r.versionDifferences());
        System.out.println("Format note: " + r.formatNote());
        System.out.println("Revision-242 client compatibility: UNVERIFIED (fixture provenance/client behavior not established)");
        System.out.println("Live network listeners: DISABLED");
        if (!r.exactMatch()) System.exit(3);
    }
}
