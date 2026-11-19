package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;

/**
 * RSProt 242 preframed JS5 response bytes. Mirrors
 * Js5Service.prepareJs5Buffer: 3-byte archive/group prefix, 512-byte
 * sections, 0xFF continuation markers. No socket or network action.
 */
public final class Js5WireFraming {
    private static final int BLOCK_SIZE = 512;
    private Js5WireFraming() {}

    public static ByteBuf prepare(int archive, int group, byte[] container) throws IOException {
        if (archive < 0 || archive > 255 || group < 0 || group > 65535)
            throw new IOException("JS5 request ID out of range");
        if (container == null || container.length < 5)
            throw new IOException("Missing compressed JS5 container");
        int continuationCount = container.length > BLOCK_SIZE - 3
            ? (container.length - (BLOCK_SIZE - 3) + (BLOCK_SIZE - 2)) / (BLOCK_SIZE - 1)
            : 0;
        long totalSize = 3L + container.length + continuationCount;
        if (totalSize > Integer.MAX_VALUE) throw new IOException("JS5 buffer too large");
        ByteBuf output = Unpooled.buffer((int)totalSize);
        try {
            output.writeByte(archive);
            output.writeShort(group);
            int written = Math.min(container.length, BLOCK_SIZE - 3);
            output.writeBytes(container, 0, written);
            while (written < container.length) {
                output.writeByte(0xFF);
                int count = Math.min(container.length - written, BLOCK_SIZE - 1);
                output.writeBytes(container, written, count);
                written += count;
            }
            if (output.readableBytes() != (int)totalSize)
                throw new IllegalStateException("Unexpected JS5 response byte count");
            return output;
        } catch (RuntimeException | Error error) {
            output.release();
            throw error;
        }
    }
}
