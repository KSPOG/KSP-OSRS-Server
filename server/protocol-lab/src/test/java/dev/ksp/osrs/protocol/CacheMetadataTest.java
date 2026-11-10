package dev.ksp.osrs.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public final class CacheMetadataTest {
    private CacheMetadataTest() {}

    public static void main(String[] args) throws Exception {
        byte[] metadata = example();
        var raw = container(0, metadata);
        var reference = CacheMetadata.parse(0, CacheMetadata.unpack(raw));
        if (reference.protocol() != 7 || reference.revision() != 1234
            || reference.groups().size() != 1 || reference.groups().get(3).fileCount() != 1
            || reference.groups().get(3).crc() != 0x12345678)
            throw new AssertionError("Reference-table decode mismatch");
        for (int type : new int[]{1,2}) {
            var decoded = CacheMetadata.parse(0, CacheMetadata.unpack(container(type, metadata)));
            if (!decoded.groups().equals(reference.groups()))
                throw new AssertionError("Compressed reference metadata mismatch");
        }
        byte[] broken = container(0, metadata);
        broken[5] = 4; // unsupported protocol
        expectBad(broken);
        byte[] shortData = container(0, metadata);
        shortData[4] = (byte)(shortData[4] + 1);
        expectBad(shortData);
        System.out.println("Cache reference metadata decoding tests passed");
    }

    private static void expectBad(byte[] raw) throws Exception {
        try {
            CacheMetadata.parse(0, CacheMetadata.unpack(raw));
            throw new AssertionError("Accepted invalid container");
        } catch (IOException expected) { /* fail closed */ }
    }

    private static byte[] example() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(7); // protocol
            out.writeInt(1234); // index revision
            out.writeByte(0); // flags
            out.writeShort(1); // number of groups (big smart small)
            out.writeShort(3); // delta group id
            out.writeInt(0x12345678); // group CRC
            out.writeInt(42); // group revision
            out.writeShort(1); // file count
            out.writeShort(0); // first file id
        }
        return bytes.toByteArray();
    }

    private static byte[] container(int type, byte[] data) throws IOException {
        var output = new ByteArrayOutputStream();
        var out = new DataOutputStream(output);
        byte[] packed;
        if (type == 1) {
            var tmp = new ByteArrayOutputStream();
            try (var zipped = new BZip2CompressorOutputStream(tmp, 1)) { zipped.write(data); }
            byte[] all = tmp.toByteArray();
            packed = java.util.Arrays.copyOfRange(all, 4, all.length);
        } else if (type == 2) {
            var tmp = new ByteArrayOutputStream();
            try (var zipped = new GZIPOutputStream(tmp)) { zipped.write(data); }
            packed = tmp.toByteArray();
        } else packed = data;
        out.writeByte(type);
        out.writeInt(packed.length);
        if (type != 0) out.writeInt(data.length);
        out.write(packed);
        return output.toByteArray();
    }
}
