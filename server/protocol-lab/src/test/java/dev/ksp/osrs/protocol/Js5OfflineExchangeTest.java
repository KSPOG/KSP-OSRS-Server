package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.util.Arrays;

public final class Js5OfflineExchangeTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("JS5 offline exchange test failed");
    }

    private static void rejects(byte[] packets) throws Exception {
        try {
            Js5OfflineExchange.parse(packets);
            throw new AssertionError("Malformed JS5 message accepted");
        } catch (IOException expected) { /* correct */ }
    }

    public static void main(String[] args) throws Exception {
        byte[] packets = {
            0, 24, 0x12, 0x34,
            1, (byte)255, 0, 24,
            2, 0, 0, 0,
            3, 0, 0, 0,
            4, 0, 0, 0
        };
        var decoded = Js5OfflineExchange.parse(packets);
        check(decoded.size() == 5);
        check(decoded.get(0).type() == Js5OfflineExchange.Type.PREFETCH);
        check(decoded.get(0).archive() == 24 && decoded.get(0).group() == 0x1234);
        check(decoded.get(1).type() == Js5OfflineExchange.Type.URGENT);
        check(decoded.get(1).archive() == 255 && decoded.get(1).group() == 24);
        check(decoded.get(2).type() == Js5OfflineExchange.Type.PRIORITY_HIGH);
        check(decoded.get(3).type() == Js5OfflineExchange.Type.PRIORITY_LOW);
        check(decoded.get(4).type() == Js5OfflineExchange.Type.XOR_CHANGE);
        check(decoded.get(4).xorKey() == 0);
        rejects(null);
        rejects(new byte[0]);
        rejects(new byte[3]);
        rejects(new byte[5]);
        rejects(new byte[] {5, 0, 0, 0});
        rejects(new byte[] {4, 0, 0, 1});
        rejects(new byte[(Js5OfflineExchange.MAX_MESSAGES + 1) * 4]);
        var report = new Js5OfflineExchange.Result(5, 1, 1, 3, 20, Arrays.asList("MASTER_INDEX_NOT_VERIFIED"));
        check(report.requests() == 5 && report.examples().size() == 1);
        System.out.println("Js5OfflineExchangeTest: PASS");
    }
}
