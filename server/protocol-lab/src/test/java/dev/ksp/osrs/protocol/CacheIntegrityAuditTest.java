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
        if (!trailer.matched() || !trailer.hasVersionTrailer())
            throw new AssertionError("Version trailer CRC not detected");

        var without = CacheIntegrityAudit.compareGroupCrc(body, expected);
        if (!without.matched() || without.hasVersionTrailer())
            throw new AssertionError("Full container CRC not detected");

        byte[] corrupted = versioned.clone();
        corrupted[4] ^= 1;
        if (CacheIntegrityAudit.compareGroupCrc(corrupted, expected).matched())
            throw new AssertionError("Corrupt group accepted");

        var encryptedLike = new byte[]{(byte)255,(byte)251,1,2,3,4,5,6};
        CRC32 encryptedCrc = new CRC32();
        encryptedCrc.update(encryptedLike);
        if (!CacheIntegrityAudit.compareGroupCrc(encryptedLike, (int)encryptedCrc.getValue()).matched())
            throw new AssertionError("Raw encrypted-like group rejected");
        System.out.println("Raw group CRC framing tests passed");
    }
}
