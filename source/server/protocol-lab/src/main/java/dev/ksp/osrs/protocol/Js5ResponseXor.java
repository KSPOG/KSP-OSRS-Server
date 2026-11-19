package dev.ksp.osrs.protocol;

/**
 * Applies RSProt-242 Js5GroupResponseEncoder's per-response XOR transform.
 *
 * Caller owns and must not reuse the output array as the unencoded frame.
 * The complete preframed JS5 wire response is transformed: archive/group,
 * compressed container, and 0xff continuation bytes.
 */
public final class Js5ResponseXor {
    private Js5ResponseXor() {}

    public static void apply(byte[] wire, int key) {
        if (wire == null) throw new IllegalArgumentException("Missing JS5 response");
        if (key < 0 || key > 255)
            throw new IllegalArgumentException("JS5 XOR key is one unsigned byte");
        if (key == 0) return;
        for (int i = 0; i < wire.length; i++) wire[i] = (byte)(wire[i] ^ key);
    }
}
