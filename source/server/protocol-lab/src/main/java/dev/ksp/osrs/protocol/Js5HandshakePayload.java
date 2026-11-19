package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decode-only model for the revision-242 RSProt
 * InitJs5RemoteConnectionDecoder payload.
 *
 * RSProt's decoder reads revision (g4) followed by four seed words (g4).
 * This class deliberately does not implement the login opcode, framing,
 * transport negotiation, socket handling, or an acknowledgment.
 */
public record Js5HandshakePayload(int revision, int[] seed) {
    public static final int PAYLOAD_BYTES = 20;
    public static final int EXPECTED_REVISION = 242;

    public Js5HandshakePayload {
        if (seed == null || seed.length != 4)
            throw new IllegalArgumentException("JS5 seed must contain four words");
        seed = seed.clone();
    }

    @Override public int[] seed() { return seed.clone(); }

    public static Js5HandshakePayload decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length != PAYLOAD_BYTES)
            throw new IOException("JS5 initialization payload must contain exactly 20 bytes");
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int revision = in.getInt();
        int[] seed = new int[4];
        for (int i = 0; i < seed.length; i++) seed[i] = in.getInt();
        return new Js5HandshakePayload(revision, seed);
    }

    public boolean revisionMatches() {
        return revision == EXPECTED_REVISION;
    }

    @Override public boolean equals(Object value) {
        return value instanceof Js5HandshakePayload other
            && revision == other.revision && java.util.Arrays.equals(seed, other.seed);
    }

    @Override public int hashCode() {
        return 31 * Integer.hashCode(revision) + java.util.Arrays.hashCode(seed);
    }

    @Override public String toString() {
        return "Js5HandshakePayload(revision=" + revision + ", seed=[REDACTED])";
    }
}
