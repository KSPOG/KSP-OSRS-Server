package dev.ksp.osrs.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Strict bounded JS5 reference table decoder; mirrored against RuneLite IndexData
 * protocol 5/6/7 field ordering. Never mutates the disk cache.
 */
public final class CacheMetadata {
    private static final int MAX_UNPACKED = 32 * 1024 * 1024;
    private static final int MAX_ARCHIVES = 100000;
    private static final int MAX_FILES = 1000000;
    public record Group(int id, int crc, int revision, int fileCount) {}
    public record Reference(int archive, int protocol, int revision, Map<Integer, Group> groups) {
        public Reference { groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups)); }
    }
    private CacheMetadata() {}

    /** Returns decoded container bytes, with a hard decompressed-byte limit. */
    public static byte[] unpack(byte[] raw) throws IOException {
        var info = CacheStructureCheck.validateContainer(raw);
        int offset = info.type() == 0 ? 5 : 9;
        if (info.type() == 0) {
            byte[] data = new byte[info.packedLength()];
            System.arraycopy(raw, 5, data, 0, data.length);
            return data;
        }
        int expected = readInt(raw, 5);
        if (expected < 0 || expected > MAX_UNPACKED)
            throw new IOException("Invalid decompressed length");
        var payload = new ByteArrayInputStream(raw, offset, info.packedLength());
        java.io.InputStream decompressor;
        if (info.type() == 1) {
            // Jagex omits the 4-byte BZh1 header from its BZip2 cache containers.
            byte[] header = {'B','Z','h','1'};
            var stream = new java.io.SequenceInputStream(new ByteArrayInputStream(header), payload);
            decompressor = new BZip2CompressorInputStream(stream, false);
        } else {
            decompressor = new GZIPInputStream(payload);
        }
        try (decompressor; var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = decompressor.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (out.size() + count > expected)
                    throw new IOException("Uncompressed payload exceeds advertised size");
                out.write(buffer, 0, count);
            }
            if (out.size() != expected)
                throw new IOException("Uncompressed payload length mismatch");
            return out.toByteArray();
        }
    }

    public static Reference parse(int archive, byte[] data) throws IOException {
        if (data.length > MAX_UNPACKED) throw new IOException("Reference table too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(data))) {
            int protocol = in.readUnsignedByte();
            if (protocol < 5 || protocol > 7)
                throw new IOException("Unsupported reference-table protocol: " + protocol);
            int revision = protocol >= 6 ? in.readInt() : 0;
            int flags = in.readUnsignedByte();
            if ((flags & ~5) != 0) throw new IOException("Unknown index flags");
            boolean named = (flags & 1) != 0;
            boolean sized = (flags & 4) != 0;
            int count = number(in, protocol);
            if (count < 0 || count > MAX_ARCHIVES) throw new IOException("Too many groups");
            int[] ids = new int[count];
            int previous = 0;
            for (int i = 0; i < count; i++) {
                int delta = number(in, protocol);
                if (delta < 0 || (i > 0 && delta == 0) || previous > Integer.MAX_VALUE - delta)
                    throw new IOException("Invalid group IDs");
                ids[i] = previous += delta;
            }
            if (named) skipInts(in, count);
            int[] crcs = new int[count];
            for (int i = 0; i < count; i++) crcs[i] = in.readInt();
            if (sized) skipInts(in, Math.multiplyExact(count, 2));
            int[] versions = new int[count];
            for (int i = 0; i < count; i++) versions[i] = in.readInt();
            int[] fileCounts = new int[count];
            long fileTotal = 0;
            for (int i = 0; i < count; i++) {
                fileCounts[i] = number(in, protocol);
                fileTotal += fileCounts[i];
                if (fileTotal > MAX_FILES) throw new IOException("Too many reference files");
            }
            for (int i = 0; i < count; i++) {
                int last = 0;
                for (int f = 0; f < fileCounts[i]; f++) {
                    int delta = number(in, protocol);
                    if (delta < 0 || (f > 0 && delta == 0) || last > Integer.MAX_VALUE - delta)
                        throw new IOException("Invalid file IDs");
                    last += delta;
                }
            }
            if (named) skipInts(in, Math.toIntExact(fileTotal));
            if (in.available() != 0)
                throw new IOException("Unexpected trailing reference-table bytes");
            Map<Integer, Group> groups = new LinkedHashMap<>();
            for (int i = 0; i < count; i++)
                groups.put(ids[i], new Group(ids[i], crcs[i], versions[i], fileCounts[i]));
            return new Reference(archive, protocol, revision, groups);
        } catch (ArithmeticException bad) {
            throw new IOException("Reference table exceeds integer limit", bad);
        }
    }

    private static int readInt(byte[] data, int index) {
        return (data[index] & 255) << 24 | (data[index + 1] & 255) << 16
            | (data[index + 2] & 255) << 8 | data[index + 3] & 255;
    }

    private static int number(DataInputStream in, int protocol) throws IOException {
        if (protocol < 7) return in.readUnsignedShort();
        in.mark(4);
        int high = in.readUnsignedByte();
        in.reset();
        if (high < 128) return in.readUnsignedShort();
        return in.readInt() & Integer.MAX_VALUE;
    }
    private static void skipInts(DataInputStream in, int count) throws IOException {
        for (int i = 0; i < count; i++) in.readInt();
    }
}
