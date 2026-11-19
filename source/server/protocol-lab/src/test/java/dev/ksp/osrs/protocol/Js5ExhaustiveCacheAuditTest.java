package dev.ksp.osrs.protocol;

import java.util.Arrays;
import java.util.zip.CRC32;

public final class Js5ExhaustiveCacheAuditTest {
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError("Exhaustive audit synthetic test failed");
    }

    private static byte[] container() {
        return new byte[] {0, 0, 0, 0, 3, 4, 5, 6};
    }

    private static int crc(byte[] bytes) {
        CRC32 c = new CRC32();
        c.update(bytes);
        return (int)c.getValue();
    }

    private static byte[] append(byte[] original, int... suffix) {
        byte[] next = Arrays.copyOf(original, original.length + suffix.length);
        for (int i = 0; i < suffix.length; i++) next[original.length + i] = (byte)suffix[i];
        return next;
    }

    public static void main(String[] args) {
        byte[] body = container();
        var group = new CacheMetadata.Group(12, crc(body), 0x1234, 1);
        var zero = Js5ExhaustiveCacheAudit.checkGroup(body, group);
        check(zero.valid() && zero.suffixBytes() == 0);
        var two = Js5ExhaustiveCacheAudit.checkGroup(append(body, 0x12, 0x34), group);
        check(two.valid() && two.suffixBytes() == 2);
        var four = Js5ExhaustiveCacheAudit.checkGroup(append(body, 0x55, 0x66, 0x12, 0x34), group);
        check(four.valid() && four.suffixBytes() == 4);
        var wrongVersion = Js5ExhaustiveCacheAudit.checkGroup(append(body, 0x12, 0x35), group);
        check(!wrongVersion.valid() && wrongVersion.reason().equals("VERSION_TRAILER_MISMATCH"));
        byte[] corrupt = body.clone();
        corrupt[7] ^= 1;
        check(!Js5ExhaustiveCacheAudit.checkGroup(corrupt, group).valid());
        check(Js5ExhaustiveCacheAudit.checkGroup(null, group).reason().equals("MISSING"));
        var malformed = new byte[] {3, 0, 0, 0, 3, 4, 5, 6};
        var malformedGroup = new CacheMetadata.Group(12, crc(malformed), 0x1234, 1);
        check(Js5ExhaustiveCacheAudit.checkGroup(malformed, malformedGroup).reason().equals("INVALID_CONTAINER"));
        System.out.println("Js5ExhaustiveCacheAuditTest: PASS");
    }
}
