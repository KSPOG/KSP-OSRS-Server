package dev.ksp.osrs.protocol;

import java.util.Arrays;
import java.util.zip.CRC32;

public final class CacheIntegrityAuditTest {
    private CacheIntegrityAuditTest() {}

    public static void main(String[] args) {
        byte[] body = new byte[]{1,0,0,0,1,0,0,0,1,100};
        byte[] versioned = Arrays.copyOf(body, body.length + 2);
        versioned[body.length] = 0;
        versioned[body.length + 1] = 7;
        CRC32 crc = new CRC32();
        crc.update(body);
        int expected = (int)crc.getValue();

        var trailer = CacheIntegrityAudit.compareGroupCrc(versioned, expected);
        if (!trailer.matched() || trailer.excludedSuffixBytes() != 2)
            throw new AssertionError("Version trailer CRC not detected");

        var without = CacheIntegrityAudit.compareGroupCrc(body, expected);
        if (!without.matched() || without.excludedSuffixBytes() != 0)
            throw new AssertionError("Full container CRC not detected");

        byte[] versionedFour = Arrays.copyOf(body, body.length + 4);
        versionedFour[body.length] = 17;
        versionedFour[body.length + 1] = 99;
        versionedFour[body.length + 2] = 0;
        versionedFour[body.length + 3] = 7;
        var four = CacheIntegrityAudit.compareGroupCrc(versionedFour, expected);
        if (!four.matched() || four.excludedSuffixBytes() != 4)
            throw new AssertionError("Four byte cache suffix CRC not detected");
        var fourLayout = CacheIntegrityAudit.describeGroup(versionedFour, 7);
        if (!fourLayout.contains("suffixBytes=4")
            || !fourLayout.contains("trailingVersion=7"))
            throw new AssertionError("Four byte cache suffix layout diagnostic failed");

        byte[] corrupted = versioned.clone();
        corrupted[4] ^= 1;
        if (CacheIntegrityAudit.compareGroupCrc(corrupted, expected).matched())
            throw new AssertionError("Corrupt group accepted");

        var encryptedLike = new byte[]{(byte)255,(byte)251,1,2,3,4,5,6};
        CRC32 encryptedCrc = new CRC32();
        encryptedCrc.update(encryptedLike);
        if (!CacheIntegrityAudit.compareGroupCrc(encryptedLike, (int)encryptedCrc.getValue()).matched())
            throw new AssertionError("Raw encrypted-like group rejected");
        String full = CacheIntegrityAudit.describeGroup(body, 7);
        if (!full.contains("suffixBytes=0") || !full.contains("type=1"))
            throw new AssertionError("Raw container framing diagnostic incorrect");
        String versionedLayout = CacheIntegrityAudit.describeGroup(versioned, 7);
        if (!versionedLayout.contains("suffixBytes=2")
            || !versionedLayout.contains("trailingVersion=7")
            || !versionedLayout.contains("expectedVersionLow16=7"))
            throw new AssertionError("Group version suffix diagnostic incorrect");
        String shifted = CacheIntegrityAudit.describeGroup(new byte[]{0,0,0,0,1,42,1,2,3,4}, 9);
        if (!shifted.contains("suffixBytes=4"))
            throw new AssertionError("Nonstandard suffix must be reported, not accepted");

        System.out.println("Raw group CRC framing tests passed");
    }
}
