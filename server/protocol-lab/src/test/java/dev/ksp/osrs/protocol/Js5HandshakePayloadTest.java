package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class Js5HandshakePayloadTest {
    private Js5HandshakePayloadTest() {}

    public static void main(String[] args) throws Exception {
        byte[] bytes = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
            .putInt(242).putInt(1).putInt(-2).putInt(0).putInt(0x12345678).array();
        var handshake = Js5HandshakePayload.decode(bytes);
        if (!handshake.revisionMatches() || handshake.seed()[1] != -2)
            throw new AssertionError("Revision 242 handshake decoding failed");
        int[] seed = handshake.seed();
        seed[0] = 777;
        if (handshake.seed()[0] != 1)
            throw new AssertionError("Handshake seed was not defensively copied");
        if (handshake.toString().contains("305419896") || handshake.toString().contains("12345678"))
            throw new AssertionError("Handshake seed values leaked into diagnostic output");
        var mismatch = bytes.clone();
        mismatch[3] = (byte)241;
        if (Js5HandshakePayload.decode(mismatch).revisionMatches())
            throw new AssertionError("Incorrect JS5 revision accepted");
        for (byte[] bad : new byte[][]{new byte[0],new byte[19],new byte[21]}) {
            try {
                Js5HandshakePayload.decode(bad);
                throw new AssertionError("Malformed handshake accepted");
            } catch(IOException expected) { /* reject */ }
        }
        System.out.println("RSProt 242 JS5 initialization payload decoder tests passed");
    }
}
