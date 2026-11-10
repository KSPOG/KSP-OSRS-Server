package dev.ksp.osrs.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Js5MasterReferenceComparatorTest {
    private static byte[] container(int... words) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        out.writeByte(0);
        out.writeInt(words.length * 4);
        for (int word : words) out.writeInt(word);
        return b.toByteArray();
    }
    private static String sha(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Master reference comparator check failed");
    }
    private static void reject(Throwing action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected fail-closed IOException");
    }
    private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        byte[] original = container(11, 2, 22, 3);
        var equal = Js5MasterReferenceComparator.compare(original, original.clone(), sha(original));
        check(equal.exactMatch() && equal.firstDifference() == -1 && equal.comparedEntries() == 2);
        byte[] other = container(11, 9, 25, 3);
        var mismatch = Js5MasterReferenceComparator.compare(original, other, sha(other));
        check(!mismatch.exactMatch() && mismatch.crcDifferences() == 1 && mismatch.versionDifferences() == 1);
        byte[] larger = container(11, 2, 22, 3, 33, 4);
        check(Js5MasterReferenceComparator.compare(original, larger, sha(larger)).firstDifference() == 4);
        byte[] opaque = new byte[] {1, 2, 3, 4, 5};
        check(Js5MasterReferenceComparator.compare(original, opaque, sha(opaque)).comparedEntries() == 0);
        reject(() -> Js5MasterReferenceComparator.compare(original, original, "bad"));
        reject(() -> Js5MasterReferenceComparator.compare(original, original, sha(other)));
        reject(() -> Js5MasterReferenceComparator.compare(original, new byte[0], sha(original)));
        Path file = Files.createTempFile("js5-fixture", ".bin");
        try {
            Files.write(file, other);
            check(!Js5MasterReferenceComparator.comparePinnedFile(original, file, sha(other)).exactMatch());
            reject(() -> Js5MasterReferenceComparator.comparePinnedFile(original, file, sha(original)));
        } finally { Files.deleteIfExists(file); }
        System.out.println("Js5MasterReferenceComparatorTest: PASS");
    }
}
