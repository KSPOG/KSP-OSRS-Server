package dev.ksp.osrs.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Local-only candidate for the classic VERSIONED (CRC32, version) master index.
 *
 * The actual client master-index format for revision 242 has not been confirmed.
 * Do NOT serve this container to live clients without an authoritative comparison.
 */
public final class Js5MasterIndexCandidate {
    private Js5MasterIndexCandidate() {}

    public record Summary(int entries, int present, int absent, byte[] container) {
        public Summary { container = container.clone(); }
        @Override public byte[] container() { return container.clone(); }
    }

    public static Summary build(Path cache) throws IOException {
        Path index = cache.resolve("main_file_cache.idx255");
        if (!Files.isRegularFile(index) || Files.isSymbolicLink(index))
            throw new IOException("Missing idx255");
        long length = Files.size(index);
        if (length <= 0 || length % 6 != 0 || length > 255 * 6L)
            throw new IOException("Invalid idx255 entry count");
        int count = (int)(length / 6);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(count * 8 + 5);
        int present = 0, absent = 0;
        try (var reader = new IndexedCacheReader(cache);
             var output = new DataOutputStream(bytes)) {
            // Uncompressed JS5 container (type 0 + 32-bit packed payload length).
            output.writeByte(0);
            output.writeInt(count * 8);
            for (int archive = 0; archive < count; archive++) {
                byte[] group = reader.read(255, archive);
                if (group == null) {
                    output.writeInt(0);
                    output.writeInt(0);
                    absent++;
                    continue;
                }
                var info = CacheStructureCheck.validateContainer(group);
                if (info.crcLength() != group.length)
                    throw new IOException("Unexpected ref table suffix for archive " + archive);
                var reference = CacheMetadata.parse(archive, CacheMetadata.unpack(group));
                CRC32 crc = new CRC32();
                crc.update(group);
                output.writeInt((int)crc.getValue());
                output.writeInt(reference.revision());
                present++;
            }
        }
        return new Summary(count, present, absent, bytes.toByteArray());
    }
}
