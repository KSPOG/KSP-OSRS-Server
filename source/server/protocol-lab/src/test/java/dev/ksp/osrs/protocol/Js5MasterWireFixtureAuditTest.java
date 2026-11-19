package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.security.MessageDigest;

public final class Js5MasterWireFixtureAuditTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Master wire fixture test failed");
    }
    private static byte[] frame(byte[] bytes) throws Exception {
        ByteBuf buf = Js5WireFraming.prepare(255, 255, bytes);
        try {
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally { buf.release(); }
    }
    private static byte[] container(int entries) {
        ByteBuffer out = ByteBuffer.allocate(5 + entries * 8).order(ByteOrder.BIG_ENDIAN);
        out.put((byte)0).putInt(entries * 8);
        for (int i = 0; i < entries; i++) out.putInt(i * 17).putInt(i);
        return out.array();
    }
    private static void rejects(byte[] wire) throws Exception {
        try {
            Js5MasterWireFixtureAudit.decode(wire);
            throw new AssertionError("Malformed master index accepted");
        } catch (IOException expected) { /* correct */ }
    }
    public static void main(String[] args) throws Exception {
        byte[] one = container(25);
        byte[] framed = frame(one);
        var decoded = Js5MasterWireFixtureAudit.decode(framed);
        check(Arrays.equals(decoded.container(), one));
        check(decoded.continuationMarkers() == 0);
        check(decoded.extraBytes() == 0 && decoded.compressionType() == 0);
        byte[] large = container(100);
        byte[] framedLarge = frame(large);
        var largeDecoded = Js5MasterWireFixtureAudit.decode(framedLarge);
        check(largeDecoded.continuationMarkers() == 1);
        check(Arrays.equals(largeDecoded.container(), large));
        byte[] badMarker = framedLarge.clone();
        badMarker[512] = 0;
        rejects(badMarker);
        byte[] badPrefix = framed.clone();
        badPrefix[2] = 0;
        rejects(badPrefix);
        rejects(new byte[0]);
        rejects(new byte[7]);
        byte[] truncated = Arrays.copyOf(framedLarge, framedLarge.length - 1);
        rejects(truncated);
        var extra = Arrays.copyOf(one, one.length + 64);
        var parsedExtra = Js5MasterWireFixtureAudit.decode(frame(extra));
        check(parsedExtra.extraBytes() == 64);
        String pin = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(one));
        check(Js5MasterReferenceComparator.compare(one, decoded.container(), pin).exactMatch());
        System.out.println("Js5MasterWireFixtureAuditTest: PASS");
    }
}
