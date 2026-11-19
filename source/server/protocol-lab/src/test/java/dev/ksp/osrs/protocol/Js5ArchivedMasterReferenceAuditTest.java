package dev.ksp.osrs.protocol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/** Tests the external master audit without disguising synthetic data as an external fixture. */
public final class Js5ArchivedMasterReferenceAuditTest {
    private Js5ArchivedMasterReferenceAuditTest() {}

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    private static void rejects(Path cache, byte[] data) throws Exception {
        boolean rejected = false;
        try { Js5ArchivedMasterReferenceAudit.inspect(cache, data); }
        catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "Invalid external master was accepted");
    }

    public static void main(String[] args) throws Exception {
        Path cache = Js5HandshakeDiagnosticTest.fixture();
        try {
            byte[] local = Js5MasterIndexCandidate.build(cache).container();
            var exact = Js5ArchivedMasterReferenceAudit.inspect(cache, local);
            check(exact.exact() && exact.firstDifference() == -1
                && exact.extraBytes() == 0 && exact.comparedEntries() == 1
                && exact.crcDifferences() == 0 && exact.versionDifferences() == 0,
                "Exact classic fixture not recognized");
            byte[] crcMismatch = local.clone();
            crcMismatch[5] ^= 1;
            var crc = Js5ArchivedMasterReferenceAudit.inspect(cache, crcMismatch);
            check(!crc.exact() && crc.crcDifferences() == 1
                && crc.versionDifferences() == 0 && crc.firstDifference() == 5,
                "CRC divergence unreported");
            byte[] versionMismatch = local.clone();
            versionMismatch[12] ^= 1;
            var version = Js5ArchivedMasterReferenceAudit.inspect(cache, versionMismatch);
            check(!version.exact() && version.crcDifferences() == 0
                && version.versionDifferences() == 1,
                "Version divergence unreported");
            byte[] extended = Arrays.copyOf(local, local.length + 64);
            var extension = Js5ArchivedMasterReferenceAudit.inspect(cache, extended);
            check(!extension.exact() && extension.extraBytes() == 64
                && extension.comparedEntries() == 1
                && extension.format().contains("NOT signature-verified"),
                "Trailing bytes were incorrectly authenticated");
            rejects(cache, new byte[0]);
            rejects(cache, new byte[]{3, 0, 0, 0, 0});
            rejects(cache, new byte[]{0, 0, 0, 0, 10});
            System.out.println("Js5ArchivedMasterReferenceAuditTest: PASS");
        } finally {
            try (var walk = Files.walk(cache)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception error) { throw new IllegalStateException(error); }
                });
            }
        }
    }
}
