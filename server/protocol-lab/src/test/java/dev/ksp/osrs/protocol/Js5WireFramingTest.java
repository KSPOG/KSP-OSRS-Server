package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.Arrays;

/** Byte-accurate tests of RSProt's JS5 512-byte framing boundaries. */
public final class Js5WireFramingTest {
    private Js5WireFramingTest() {}

    public static void main(String[] args) throws Exception {
        for (int length : new int[]{5, 508, 509, 510, 511, 512, 1019, 1020, 1021, 4096}) {
            byte[] raw = new byte[length];
            for (int i = 0; i < raw.length; i++) raw[i] = (byte)(i * 31);
            try (ByteBuf response = Js5WireFraming.prepare(255, 250, raw)) {
                if (response.readUnsignedByte() != 255 || response.readUnsignedShort() != 250)
                    throw new AssertionError("Incorrect JS5 prefix");
                byte[] unpacked = new byte[length];
                int offset = 0;
                while (offset < length) {
                    if (offset != 0 && response.readUnsignedByte() != 255)
                        throw new AssertionError("Missing JS5 0xFF continuation");
                    int count = Math.min(length - offset, offset == 0 ? 509 : 511);
                    response.readBytes(unpacked, offset, count);
                    offset += count;
                }
                if (response.isReadable() || !Arrays.equals(raw, unpacked))
                    throw new AssertionError("JS5 boundary error at " + length);
            }
        }
        expectBad(-1, 0);
        expectBad(256, 0);
        expectBad(0, -1);
        expectBad(0, 65536);
        System.out.println("RSProt JS5 wire framing tests passed");
    }

    private static void expectBad(int archive, int group) throws Exception {
        try (ByteBuf ignored = Js5WireFraming.prepare(archive, group, new byte[5])) {
            throw new AssertionError("Invalid JS5 request accepted");
        } catch (IOException expected) { /* fail closed */ }
    }
}
