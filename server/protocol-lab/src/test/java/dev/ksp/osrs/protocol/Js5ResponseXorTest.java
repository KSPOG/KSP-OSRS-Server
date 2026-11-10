package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;

/**
 * Encoded full-frame coverage across 512-byte JS5 block boundaries.
 * No external cache or network required.
 */
public final class Js5ResponseXorTest {
    private Js5ResponseXorTest() {}

    public static void main(String[] args) throws Exception {
        byte[] container = new byte[1605];
        container[0] = 0;
        int payload = container.length - 5;
        container[1] = (byte)(payload >>> 24);
        container[2] = (byte)(payload >>> 16);
        container[3] = (byte)(payload >>> 8);
        container[4] = (byte)payload;
        for (int i = 5; i < container.length; i++) container[i] = (byte)(i * 23);

        ByteBuf framed = Js5WireFraming.prepare(255, 24, container);
        byte[] original;
        try {
            original = new byte[framed.readableBytes()];
            framed.readBytes(original);
        } finally { framed.release(); }

        if (original.length <= 1536 || (original[512] & 255) != 255
            || (original[1024] & 255) != 255
            || (original[1536] & 255) != 255)
            throw new AssertionError("Expected 512-byte continuation markers");

        int key = 0x5a;
        byte[] encrypted = original.clone();
        Js5ResponseXor.apply(encrypted, key);
        if ((encrypted[512] & 255) != (255 ^ key)
            || (encrypted[1024] & 255) != (255 ^ key)
            || (encrypted[1536] & 255) != (255 ^ key))
            throw new AssertionError("Continuation marker was not XOR encrypted");
        if ((encrypted[0] & 255) != (255 ^ key)
            || (encrypted[1] & 255) != key
            || (encrypted[2] & 255) != (24 ^ key))
            throw new AssertionError("Archive/group prefix was not XOR encrypted");

        Js5ResponseXor.apply(encrypted, key);
        if (!Arrays.equals(encrypted, original))
            throw new AssertionError("Double XOR did not restore exact JS5 frame");

        byte[] zero = original.clone();
        Js5ResponseXor.apply(zero, 0);
        if (!Arrays.equals(zero, original))
            throw new AssertionError("Zero-key transform changed response");

        boolean rejected = false;
        try { Js5ResponseXor.apply(zero, 256); }
        catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Invalid XOR key accepted");

        System.out.println("Js5ResponseXorTest: PASS frameBytes=" + original.length
            + " continuationMarkers=3 key=0x5A roundTrip=EXACT");
    }
}
