package dev.ksp.osrs.protocol;

import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.CRC32;

/** Synthetic coverage of raw group trimming, CRC rejection and JS5 index access. */
public final class VerifiedJs5GroupProviderTest {
    private VerifiedJs5GroupProviderTest() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ksp-js5-provider-");
        try {
            byte[] payload = {0,0,0,0,3,11,22,33};
            CRC32 crc = new CRC32();
            crc.update(payload);
            int expected = (int) crc.getValue();
            byte[] withSuffix = java.util.Arrays.copyOf(payload, payload.length + 4);
            withSuffix[withSuffix.length - 1] = 7;
            byte[] reference = reference(expected);
            write(root, 255, 0, reference, 1);
            write(root, 0, 0, withSuffix, 2);
            var audit = Js5MasterIndexAudit.audit(root);
            if (audit.entries() != 1 || audit.present() != 1
                || audit.crcVerified() != 1 || audit.revisionVerified() != 1)
                throw new AssertionError("Local master-index candidate audit failed");
            var master = Js5MasterIndexCandidate.build(root);
            if (master.entries() != 1 || master.present() != 1 || master.absent() != 0)
                throw new AssertionError("Master-index candidate entries are incorrect");
            byte[] candidate = master.container();
            if (candidate.length != 13 || candidate[0] != 0 || candidate[4] != 8)
                throw new AssertionError("Invalid VERSIONED master-index container");
            int advertisedRevision = java.nio.ByteBuffer.wrap(candidate, 9, 4).getInt();
            if (advertisedRevision != 1)
                throw new AssertionError("Master-index reference revision mismatch");

            try (var provider = new VerifiedJs5GroupProvider(root)) {
                if (provider.availableReferenceTables() != 1)
                    throw new AssertionError("Reference table not loaded");
                ByteBuf group = provider.provide(0, 0);
                if (group == null) throw new AssertionError("Valid group missing");
                try {
                    if (group.readUnsignedByte() != 0 || group.readUnsignedShort() != 0)
                        throw new AssertionError("Missing JS5 archive/group prefix");
                    if (group.readableBytes() != payload.length)
                        throw new AssertionError("Incorrect suffix removal");
                    byte[] actual = new byte[group.readableBytes()];
                    group.readBytes(actual);
                    if (!java.util.Arrays.equals(actual, payload))
                        throw new AssertionError("JS5 group payload was changed");
                } finally { group.release(); }

                ByteBuf index = provider.provide(255, 0);
                if (index == null) throw new AssertionError("Reference group missing");
                try {
                    if (index.readUnsignedByte() != 255 || index.readUnsignedShort() != 0)
                        throw new AssertionError("Incorrect reference-index response prefix");
                } finally { index.release(); }
                if (provider.provide(255, 255) != null
                    || provider.provide(0, 1) != null
                    || provider.provide(1, 0) != null)
                    throw new AssertionError("Unsupported group incorrectly served");

                // Corrupt payload after initialization: must fail closed.
                try (RandomAccessFile file = new RandomAccessFile(
                    root.resolve("main_file_cache.dat2").toFile(), "rw")) {
                    file.seek(2L * 520 + 8 + 6);
                    file.writeByte(44);
                }
                if (provider.provide(0, 0) != null)
                    throw new AssertionError("Corrupted group served");
            }
            System.out.println("Verified JS5 group provider tests passed");
        } finally {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (Exception ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }

    private static byte[] reference(int crc) throws Exception {
        var metadataBytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(metadataBytes)) {
            out.writeByte(7);
            out.writeInt(1);
            out.writeByte(0);
            out.writeShort(1); // one group
            out.writeShort(0); // group zero
            out.writeInt(crc);
            out.writeInt(7);
            out.writeShort(1); // file count
            out.writeShort(0); // file zero
        }
        byte[] metadata = metadataBytes.toByteArray();
        var containerBytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(containerBytes)) {
            out.writeByte(0);
            out.writeInt(metadata.length);
            out.write(metadata);
        }
        return containerBytes.toByteArray();
    }

    private static void medium(RandomAccessFile file, int value) throws Exception {
        file.writeByte(value >>> 16);
        file.writeByte(value >>> 8);
        file.writeByte(value);
    }

    private static void write(Path root, int index, int group,
        byte[] data, int sector) throws Exception {
        try (var dat = new RandomAccessFile(root.resolve("main_file_cache.dat2").toFile(), "rw");
             var idx = new RandomAccessFile(root.resolve("main_file_cache.idx" + index).toFile(), "rw")) {
            idx.seek((long)group * 6);
            medium(idx, data.length);
            medium(idx, sector);
            dat.seek((long)sector * 520);
            dat.writeShort(group);
            dat.writeShort(0);
            medium(dat, 0);
            dat.writeByte(index);
            dat.write(data);
        }
    }
}
